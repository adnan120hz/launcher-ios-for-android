import AVFoundation
import CoreGraphics
import UIKit

/// Video helpers: time-lapse assembly from captured stills.
enum VideoTools {

    /// Assembles captured stills into a real time-lapse movie
    /// (30fps playback; frames are shot at `interval` seconds).
    static func assembleTimeLapse(images: [UIImage], completion: @escaping (URL?) -> Void) {
        guard let first = images.first, first.size.width > 0 else {
            completion(nil)
            return
        }
        let out = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("timelapse-\(UUID().uuidString).mp4")
        let width = Int(first.size.width)
        let height = Int(first.size.height)
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let writer = try AVAssetWriter(outputURL: out, fileType: .mp4)
                let settings: [String: Any] = [
                    AVVideoCodecKey: AVVideoCodecType.h264,
                    AVVideoWidthKey: width,
                    AVVideoHeightKey: height,
                ]
                let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
                input.expectsMediaDataInRealTime = false
                let adaptor = AVAssetWriterInputPixelBufferAdaptor(
                    assetWriterInput: input,
                    sourcePixelBufferAttributes: [
                        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
                        kCVPixelBufferWidthKey as String: width,
                        kCVPixelBufferHeightKey as String: height,
                    ]
                )
                guard writer.canAdd(input) else { throw NSError(domain: "KameraIOS", code: -2) }
                writer.add(input)
                writer.startWriting()
                writer.startSession(atSourceTime: .zero)
                for (index, image) in images.enumerated() {
                    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.01) }
                    guard let cg = image.cgImage, let buffer = pixelBuffer(from: cg, width: width, height: height) else { continue }
                    adaptor.append(buffer, withPresentationTime: CMTime(value: Int64(index), timescale: 30))
                }
                input.markAsFinished()
                writer.finishWriting {
                    DispatchQueue.main.async { completion(writer.status == .completed ? out : nil) }
                }
            } catch {
                DispatchQueue.main.async { completion(nil) }
            }
        }
    }

    private static func pixelBuffer(from image: CGImage, width: Int, height: Int) -> CVPixelBuffer? {
        var buffer: CVPixelBuffer?
        let attrs: [String: Any] = [
            kCVPixelBufferCGImageCompatibilityKey as String: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
        ]
        guard CVPixelBufferCreate(kCFAllocatorDefault, width, height,
                                  kCVPixelFormatType_32ARGB, attrs as CFDictionary, &buffer) == kCVReturnSuccess,
              let buffer else { return nil }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let base = CVPixelBufferGetBaseAddress(buffer),
              let ctx = CGContext(data: base, width: width, height: height, bitsPerComponent: 8,
                                  bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
                                  space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue) else { return nil }
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return buffer
    }
}
