import AVFoundation
import CoreImage
import UIKit

/// Applies STYLES/FILTER grades and the CONFIG panel to captured photos,
/// for real, through CoreImage — the iOS counterpart of the Android
/// PhotoConfigProcessor. Neutral values are skipped entirely so the
/// capture fast path stays untouched.
enum PhotoProcessor {
    private static let context = CIContext(options: [.useSoftwareRenderer: false])

    /// Full pipeline: grade (style/filter) -> CONFIG -> aspect crop.
    static func process(photoData: Data, config: PhotoConfig, grade: GradePreset?,
                        styleIntensity: Double, aspect: PhotoAspect,
                        portraitDepth: AVDepthData?) -> Data {
        guard var image = CIImage(data: photoData) else { return photoData }

        // Portrait depth blur first (real AVFoundation depth/matte only).
        if let depth = portraitDepth {
            image = applyDepthBlur(to: image, depth: depth) ?? image
        }

        if let grade {
            image = applyGrade(to: image, grade: grade, intensity: styleIntensity)
        }
        if !config.isNeutral {
            image = applyConfig(to: image, config: config)
        }
        image = crop(image, toAspect: aspect.value)

        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        if let jpeg = context.jpegRepresentation(of: image, colorSpace: colorSpace,
                                                 options: [kCGImageDestinationLossyCompressionQuality as CIImageRepresentationOption: 0.92]) {
            return jpeg
        }
        return photoData
    }

    private static func applyGrade(to image: CIImage, grade: GradePreset, intensity: Double) -> CIImage {
        let t = min(max(intensity, 0), 1.5)
        var out = image
        if grade.mono > 0 {
            out = out.applyingFilter("CIPhotoEffectMono")
        }
        out = out.applyingFilter("CIColorControls", parameters: [
            kCIInputSaturationKey: 1 + (grade.saturation - 1) * t,
            kCIInputContrastKey: 1 + (grade.contrast - 1) * t,
        ])
        if grade.warmth != 0 {
            out = out.applyingFilter("CITemperatureAndTint", parameters: [
                "inputNeutral": CIVector(x: 6500, y: 0),
                "inputTargetNeutral": CIVector(x: 6500 + grade.warmth * t, y: 0),
            ])
        }
        return out
    }

    static func applyConfig(to image: CIImage, config: PhotoConfig) -> CIImage {
        var out = image
        // Gamma (power < 1 brightens when config.gamma > 1: apply 1/gamma).
        if config.gamma != 1 {
            out = out.applyingFilter("CIGammaAdjust", parameters: [
                "inputPower": 1.0 / max(0.2, config.gamma),
            ])
        }
        if config.contrast != 1 || config.saturation != 1 {
            out = out.applyingFilter("CIColorControls", parameters: [
                kCIInputContrastKey: config.contrast,
                kCIInputSaturationKey: config.saturation,
            ])
        }
        // Denoise: blend toward a softly blurred copy.
        if config.denoise > 0 {
            let blurred = out.clampedToExtent()
                .applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 2.0])
                .cropped(to: out.extent)
            out = out.applyingFilter("CIDissolveTransition", parameters: [
                "inputTargetImage": blurred,
                kCIInputTimeKey: config.denoise * 0.55,
            ])
        }
        // Unsharp mask last, on top of everything.
        if config.sharpness > 0 {
            out = out.applyingFilter("CIUnsharpMask", parameters: [
                kCIInputRadiusKey: 2.4,
                kCIInputIntensityKey: config.sharpness,
            ])
        }
        return out.cropped(to: image.extent)
    }

    private static func applyDepthBlur(to image: CIImage, depth: AVDepthData) -> CIImage? {
        let depthImage = CIImage(cvPixelBuffer: depth.depthDataMap)
        guard let filter = CIFilter(name: "CIDepthBlurEffect") else { return nil }
        filter.setValue(image, forKey: kCIInputImageKey)
        filter.setValue(depthImage, forKey: "inputDepthImage")
        return filter.outputImage?.cropped(to: image.extent)
    }

    /// Centre-crop to the requested aspect (what the user picked is what
    /// the saved JPEG is — honest, like the Android aspect handling).
    private static func crop(_ image: CIImage, toAspect aspect: CGFloat) -> CIImage {
        let extent = image.extent
        guard extent.width > 0, extent.height > 0 else { return image }
        let current = extent.width / extent.height
        var target = extent
        if current > aspect {
            let w = extent.height * aspect
            target = CGRect(x: extent.minX + (extent.width - w) / 2, y: extent.minY, width: w, height: extent.height)
        } else if current < aspect {
            let h = extent.width / aspect
            target = CGRect(x: extent.minX, y: extent.minY + (extent.height - h) / 2, width: extent.width, height: h)
        }
        return image.cropped(to: target)
    }
}

/// Minimal panorama stitcher. Translation-only compositing with a
/// feathered overlap: honest limits — best for distant scenery, moving
/// subjects and near objects will ghost. (On Android this is the same
/// trade-off; iOS AVFoundation has no public panorama stitcher.)
enum PanoStitcher {
    private static let context = CIContext(options: [.useSoftwareRenderer: false])

    static func stitch(images: [Data]) -> Data? {
        let ciImages = images.compactMap { CIImage(data: $0) }
        guard let first = ciImages.first, ciImages.count > 1 else { return nil }
        let width = first.extent.width
        let height = first.extent.height
        let step = width * 0.62 // ~38% overlap between sweep frames

        var canvas = first
        for (index, frame) in ciImages.dropFirst().enumerated() {
            let xOffset = step * CGFloat(index + 1)
            let translated = frame.transformed(by: CGAffineTransform(translationX: xOffset, y: 0))
            let overlapRect = CGRect(x: xOffset, y: 0, width: width * 0.38, height: height)
            let mask = featherMask(rect: overlapRect, canvasHeight: height)
            if let blended = CIFilter(name: "CIBlendWithMask", parameters: [
                kCIInputImageKey: translated,
                kCIInputBackgroundImageKey: canvas,
                kCIInputMaskImageKey: mask,
            ])?.outputImage {
                canvas = blended
            }
        }
        let totalWidth = width + step * CGFloat(ciImages.count - 1)
        canvas = canvas.cropped(to: CGRect(x: 0, y: 0, width: totalWidth, height: height))
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        return context.jpegRepresentation(of: canvas, colorSpace: colorSpace,
                                          options: [kCGImageDestinationLossyCompressionQuality as CIImageRepresentationOption: 0.9])
    }

    /// Horizontal black->white gradient used as a feathered blend mask.
    private static func featherMask(rect: CGRect, canvasHeight: CGFloat) -> CIImage {
        guard let gradient = CIFilter(name: "CILinearGradient") else { return CIImage(color: .white) }
        gradient.setValue(CIVector(x: rect.minX, y: 0), forKey: "inputPoint0")
        gradient.setValue(CIVector(x: rect.maxX, y: 0), forKey: "inputPoint1")
        gradient.setValue(CIColor.black, forKey: "inputColor0")
        gradient.setValue(CIColor.white, forKey: "inputColor1")
        return (gradient.outputImage ?? CIImage(color: .white)).cropped(to: rect)
    }
}
