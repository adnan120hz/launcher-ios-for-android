import Foundation
import AVFoundation

/// The seven carousel modes, ordered exactly like the Android app
/// (and the iOS Camera layout): TIME-LAPSE · SLO-MO · CINEMATIC · VIDEO ·
/// PHOTO · PORTRAIT · PANO.
enum CameraMode: Int, CaseIterable, Identifiable {
    case timeLapse, sloMo, cinematic, video, photo, portrait, pano

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .timeLapse: return "TIME-LAPSE"
        case .sloMo: return "SLO-MO"
        case .cinematic: return "CINEMATIC"
        case .video: return "VIDEO"
        case .photo: return "PHOTO"
        case .portrait: return "PORTRAIT"
        case .pano: return "PANO"
        }
    }

    var isVideoFamily: Bool {
        switch self {
        case .video, .sloMo, .cinematic, .timeLapse: return true
        default: return false
        }
    }
}

enum SheetKind: Equatable {
    case none, grid, flash, timer, exposure, styles, filter, aspect, config, night, action, resolution
}

enum FlashSetting: String, CaseIterable {
    case off, auto, on
    var label: String {
        switch self {
        case .off: return "OFF"
        case .auto: return "AUTO"
        case .on: return "ON"
        }
    }
    var photoFlashMode: AVCaptureDevice.FlashMode {
        switch self {
        case .off: return .off
        case .auto: return .auto
        case .on: return .on
        }
    }
}

enum PhotoAspect: String, CaseIterable {
    case ratio43, ratio169, square
    var label: String {
        switch self {
        case .ratio43: return "4:3"
        case .ratio169: return "16:9"
        case .square: return "1:1"
        }
    }
    /// width / height of the saved photo after the honest centre-crop.
    var value: CGFloat {
        switch self {
        case .ratio43: return 4.0 / 3.0
        case .ratio169: return 16.0 / 9.0
        case .square: return 1.0
        }
    }
}

/// CONFIG — this app's own photo-quality configuration (inspired by the
/// GCam-config idea, but the parameters are ours and are really applied
/// through CoreImage in PhotoProcessor). Mirrors the Android PhotoConfig.
struct PhotoConfig: Equatable {
    var sharpness: Double = 0      // 0..1  unsharp-mask strength
    var saturation: Double = 1     // 0.5..1.6
    var contrast: Double = 1       // 0.6..1.5
    var gamma: Double = 1          // 0.6..1.6 (>1 lifts shadows)
    var denoise: Double = 0        // 0..1  blend toward a blurred copy

    var isNeutral: Bool {
        sharpness == 0 && saturation == 1 && contrast == 1 && gamma == 1 && denoise == 0
    }

    static let neutral = PhotoConfig()
}

struct PhotoConfigPreset: Identifiable {
    let id: String
    let label: String
    let config: PhotoConfig
}

let photoConfigPresets: [PhotoConfigPreset] = [
    PhotoConfigPreset(id: "default", label: "Default", config: PhotoConfig()),
    PhotoConfigPreset(id: "natural", label: "Natural",
                      config: PhotoConfig(sharpness: 0.15, saturation: 1.05, contrast: 1.02, gamma: 1.0, denoise: 0.2)),
    PhotoConfigPreset(id: "vivid", label: "Vivid",
                      config: PhotoConfig(sharpness: 0.25, saturation: 1.35, contrast: 1.08, gamma: 1.0, denoise: 0.1)),
    PhotoConfigPreset(id: "tajam", label: "Tajam",
                      config: PhotoConfig(sharpness: 0.7, saturation: 1.05, contrast: 1.05, gamma: 1.0, denoise: 0.05)),
    PhotoConfigPreset(id: "malam", label: "Malam",
                      config: PhotoConfig(sharpness: 0.1, saturation: 1.05, contrast: 0.95, gamma: 1.25, denoise: 0.6)),
]

/// Photographic styles / filters: real CoreImage colour grades.
struct GradePreset: Identifiable {
    let id: String
    let label: String
    let saturation: Double
    let contrast: Double
    /// Temperature shift in Kelvin away from 6500 (warm > 0).
    let warmth: Double
    /// Sepia/mono flavour 0..1 handled specially for mono.
    let mono: Double
}

let stylePresets: [GradePreset] = [
    GradePreset(id: "standard", label: "STANDARD", saturation: 1.0, contrast: 1.0, warmth: 0, mono: 0),
    GradePreset(id: "vibrant", label: "VIBRANT", saturation: 1.3, contrast: 1.06, warmth: 150, mono: 0),
    GradePreset(id: "warm", label: "WARM", saturation: 1.08, contrast: 1.0, warmth: 700, mono: 0),
    GradePreset(id: "cool", label: "COOL", saturation: 1.05, contrast: 1.0, warmth: -700, mono: 0),
    GradePreset(id: "mono", label: "MONO", saturation: 0.0, contrast: 1.08, warmth: 0, mono: 1),
]

let filterPresets: [GradePreset] = [
    GradePreset(id: "none", label: "NONE", saturation: 1.0, contrast: 1.0, warmth: 0, mono: 0),
    GradePreset(id: "vivid", label: "VIVID", saturation: 1.35, contrast: 1.1, warmth: 100, mono: 0),
    GradePreset(id: "dramatic", label: "DRAMATIC", saturation: 1.1, contrast: 1.25, warmth: -150, mono: 0),
    GradePreset(id: "silvertone", label: "SILVERTONE", saturation: 0.15, contrast: 1.12, warmth: -250, mono: 0),
    GradePreset(id: "noir", label: "NOIR", saturation: 0.0, contrast: 1.3, warmth: 0, mono: 1),
]

/// External links, identical to the Android app.
enum AppLinks {
    static let tiktok = "https://www.tiktok.com/@adnan.120hz?_r=1&_t=ZS-9AElXliY2Me"
    static let website = "https://adnan120hz.vercel.app"
    static let github = "https://github.com/adnan120hz"
    static let releasesAPI = "https://api.github.com/repos/adnan120hz/launcher-ios-for-android/releases?per_page=20"
}
