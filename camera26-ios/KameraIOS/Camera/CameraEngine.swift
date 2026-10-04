import AVFoundation
import CoreImage
import Photos
import UIKit

/// One selectable recording resolution backed by a real device format.
struct VideoFormatOption: Identifiable {
    let id: String
    let label: String        // "4K", "HD", "720p", "SD"
    let width: Int
    let height: Int
    let fpsOptions: [Int]
    let format: AVCaptureDevice.Format
}

/// What the current device can honestly do. Everything the UI gates on
/// comes from here — nothing is shown as available that the hardware did
/// not report.
struct CameraCapabilities {
    var hasUltraWide = false
    var minZoom: Double = 1
    var maxZoom: Double = 1
    var zoomStops: [Double] = [1]
    var sloMoRates: [Int] = []            // e.g. [120, 240]
    var cinematicSupported = false
    var portraitSupported = false         // depth and/or portrait matte
    var videoFormats: [VideoFormatOption] = []
    var hasTorch = false
}

/// AVFoundation engine for the SwiftUI camera. All session work happens on
/// a dedicated queue; completions are delivered on the main queue.
final class CameraEngine: NSObject {
    let session = AVCaptureSession()
    let previewLayer = AVCaptureVideoPreviewLayer()
    private let sessionQueue = DispatchQueue(label: "net.adnan120hz.camera.session")
    private let photoOutput = AVCapturePhotoOutput()
    private let movieOutput = AVCaptureMovieFileOutput()
    private var input: AVCaptureDeviceInput?
    private(set) var device: AVCaptureDevice?
    private(set) var position: AVCaptureDevice.Position = .back
    private(set) var capabilities = CameraCapabilities()
    /// 35mm-equivalent focal length at 1x for the active format.
    private(set) var baseFocalMM: Double = 24
    var stabilizationMode: AVCaptureVideoStabilizationMode = .auto

    private var photoCompletion: ((AVCapturePhoto?, Error?) -> Void)?
    private var recordCompletion: ((URL?, Error?) -> Void)?
    private var movieOutputAttached = false

    override init() {
        super.init()
        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
    }

    // MARK: - Lifecycle

    func configure(position: AVCaptureDevice.Position, completion: @escaping (Bool) -> Void) {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let ok = self.configureOnQueue(position: position)
            DispatchQueue.main.async { completion(ok) }
        }
    }

    private func configureOnQueue(position: AVCaptureDevice.Position) -> Bool {
        self.position = position
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        if let old = input {
            session.removeInput(old)
            input = nil
        }
        guard let dev = bestDevice(for: position),
              let newInput = try? AVCaptureDeviceInput(device: dev) else { return false }
        device = dev
        if session.canAddInput(newInput) {
            session.addInput(newInput)
            input = newInput
        } else { return false }

        if !session.outputs.contains(photoOutput), session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }
        photoOutput.maxPhotoQualityPrioritization = .quality
        if photoOutput.isDepthDataDeliverySupported {
            photoOutput.isDepthDataDeliveryEnabled = true
        }
        if photoOutput.isPortraitEffectsMatteDeliverySupported {
            photoOutput.isPortraitEffectsMatteDeliveryEnabled = true
        }
        refreshCapabilities(for: dev)
        return true
    }

    private func bestDevice(for position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let types: [AVCaptureDevice.DeviceType] = [
            .builtInTripleCamera, .builtInDualWideCamera, .builtInDualCamera,
            .builtInWideAngleCamera, .builtInUltraWideCamera, .builtInTrueDepthCamera,
        ]
        let discovery = AVCaptureDevice.DiscoverySession(deviceTypes: types, mediaType: .video, position: position)
        // Prefer a virtual device (gives real 0.5x / telephoto switching).
        if let virtual = discovery.devices.first(where: { $0.deviceType == .builtInTripleCamera || $0.deviceType == .builtInDualWideCamera || $0.deviceType == .builtInDualCamera }) {
            return virtual
        }
        return discovery.devices.first
    }

    func start() {
        sessionQueue.async { [weak self] in
            guard let self, !self.session.isRunning else { return }
            self.session.startRunning()
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    // MARK: - Capabilities

    private func refreshCapabilities(for dev: AVCaptureDevice) {
        var caps = CameraCapabilities()
        caps.hasTorch = dev.hasTorch
        caps.minZoom = Double(dev.minAvailableVideoZoomFactor)
        caps.maxZoom = Double(dev.maxAvailableVideoZoomFactor)
        caps.hasUltraWide = dev.constituentDevices.contains { $0.deviceType == .builtInUltraWideCamera } || caps.minZoom < 0.99

        // Zoom stops, same rule as Android: 0.5 (ultra-wide only) · 1x · 2x ·
        // 8x or the device's real maximum in its place.
        var stops: [Double] = []
        if caps.hasUltraWide { stops.append((caps.minZoom * 10).rounded() / 10) }
        stops.append(1)
        if caps.maxZoom >= 2 { stops.append(2) }
        if caps.maxZoom > 2 { stops.append(min(8, caps.maxZoom)) }
        caps.zoomStops = Array(Set(stops)).sorted()

        var slo: Set<Int> = []
        var cinematic = false
        var buckets: [String: (label: String, w: Int, h: Int, format: AVCaptureDevice.Format, fps: Set<Int>)] = [:]
        for format in dev.formats {
            let dims = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
            let maxFps = format.videoSupportedFrameRateRanges.map { $0.maxFrameRate }.max() ?? 30
            if maxFps >= 119 { slo.insert(Int(maxFps.rounded())) }
            if format.isVideoStabilizationModeSupported(.cinematic) ||
                format.isVideoStabilizationModeSupported(.cinematicExtended) {
                cinematic = true
            }
            // Bucket recording formats by resolution (landscape sizes).
            let w = Int(max(dims.width, dims.height))
            let h = Int(min(dims.width, dims.height))
            guard w >= 640 else { continue }
            let label: String
            switch w {
            case 3840...: label = "4K"
            case 1920...: label = "HD"
            case 1280...: label = "720p"
            default: label = "SD"
            }
            let key = "\(label)-\(w)x\(h)"
            var fpsSet: Set<Int> = buckets[key]?.fps ?? []
            for candidate in [24, 30, 60] where Double(candidate) <= maxFps + 0.5 {
                fpsSet.insert(candidate)
            }
            // Keep the widest format per bucket as representative.
            if let existing = buckets[key], existing.w >= w {
                buckets[key] = (existing.label, existing.w, existing.h, existing.format, fpsSet.union(existing.fps))
            } else {
                buckets[key] = (label, w, h, format, fpsSet)
            }
        }
        caps.sloMoRates = slo.sorted()
        caps.cinematicSupported = cinematic
        caps.portraitSupported = photoOutput.isDepthDataDeliverySupported || photoOutput.isPortraitEffectsMatteDeliverySupported
        caps.videoFormats = buckets.values
            .map { VideoFormatOption(id: "\($0.label)-\($0.w)x\($0.h)", label: $0.label, width: $0.w, height: $0.h,
                                     fpsOptions: $0.fps.sorted(), format: $0.format) }
            .sorted { $0.width > $1.width }
        capabilities = caps
        updateBaseFocal()
    }

    private func updateBaseFocal() {
        guard let dev = device else { return }
        let fov = Double(dev.activeFormat.videoFieldOfView) // horizontal degrees
        if fov > 1, fov < 179 {
            baseFocalMM = 18.0 / tan(fov * .pi / 360.0)
        }
    }

    /// Current display zoom ratio (1 = wide camera) and its MM equivalent.
    var zoomRatio: Double { Double(device?.videoZoomFactor ?? 1) }
    var focalMM: Int { Int((baseFocalMM * zoomRatio).rounded()) }

    // MARK: - Mode / outputs

    /// Attach or detach the movie output depending on the mode family.
    func setVideoOutputAttached(_ attached: Bool) {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.session.beginConfiguration()
            if attached, !self.movieOutputAttached, self.session.canAddOutput(self.movieOutput) {
                self.session.addOutput(self.movieOutput)
                self.movieOutputAttached = true
            } else if !attached, self.movieOutputAttached {
                self.session.removeOutput(self.movieOutput)
                self.movieOutputAttached = false
            }
            self.session.commitConfiguration()
            if attached { self.applyStabilization() }
        }
    }

    // MARK: - Video format / fps / stabilization

    func applyVideoFormat(_ option: VideoFormatOption, fps: Int) {
        sessionQueue.async { [weak self] in
            guard let self, let dev = self.device else { return }
            do {
                try dev.lockForConfiguration()
                dev.activeFormat = option.format
                let dur = CMTime(value: 1, timescale: CMTimeScale(fps))
                if dev.activeFormat.videoSupportedFrameRateRanges.contains(where: { Double(fps) >= $0.minFrameRate && Double(fps) <= $0.maxFrameRate }) {
                    dev.activeVideoMinFrameDuration = dur
                    dev.activeVideoMaxFrameDuration = dur
                }
                // Zoom resets to 1x on a format change (honest MM baseline).
                if dev.videoZoomFactor != 1, dev.isRampingVideoZoom == false {
                    dev.videoZoomFactor = 1
                }
                dev.unlockForConfiguration()
                self.updateBaseFocal()
                self.applyStabilization()
            } catch { /* keep current format */ }
        }
    }

    /// ACTION mode: use the strongest stabilization the active format
    /// honestly supports; the UI labels the actual source.
    func applyStabilization() {
        guard movieOutputAttached,
              let connection = movieOutput.connection(with: .video),
              connection.isVideoStabilizationSupported else { return }
        let format = device?.activeFormat
        let mode: AVCaptureVideoStabilizationMode
        switch stabilizationMode {
        case .cinematicExtended where format?.isVideoStabilizationModeSupported(.cinematicExtended) == true:
            mode = .cinematicExtended
        case .cinematic where format?.isVideoStabilizationModeSupported(.cinematic) == true:
            mode = .cinematic
        default:
            mode = stabilizationMode
        }
        connection.preferredVideoStabilizationMode = mode
    }

    func stabilizationLabel() -> String {
        guard movieOutputAttached,
              let connection = movieOutput.connection(with: .video),
              connection.isVideoStabilizationSupported else { return "Tidak tersedia di perangkat ini" }
        switch connection.preferredVideoStabilizationMode {
        case .cinematicExtended: return "Stabilisasi sinematik diperluas (AVFoundation)"
        case .cinematic: return "Stabilisasi sinematik (AVFoundation)"
        case .standard: return "Stabilisasi standar (AVFoundation)"
        case .auto: return "Stabilisasi otomatis (AVFoundation)"
        default: return "Nonaktif"
        }
    }

    /// Selects a format that truly records at `rate` fps (SLO-MO).
    /// Prefers a 1080p-class format; returns false when the device has no
    /// high-speed format at all (UI keeps SLO-MO dimmed then).
    @discardableResult
    func applyHighSpeedFormat(rate: Int) -> Bool {
        guard let dev = device else { return false }
        let supporting = dev.formats.filter { format in
            format.videoSupportedFrameRateRanges.contains {
                Double(rate) >= $0.minFrameRate && Double(rate) <= $0.maxFrameRate
            }
        }
        guard !supporting.isEmpty else { return false }
        let chosen = supporting.first { CMVideoFormatDescriptionGetDimensions($0.formatDescription).width == 1920 }
            ?? supporting.min { a, b in
                let wa = CMVideoFormatDescriptionGetDimensions(a.formatDescription).width
                let wb = CMVideoFormatDescriptionGetDimensions(b.formatDescription).width
                return abs(Int(wa) - 1920) < abs(Int(wb) - 1920)
            }
            ?? supporting[0]
        do {
            try dev.lockForConfiguration()
            dev.activeFormat = chosen
            let dur = CMTime(value: 1, timescale: CMTimeScale(rate))
            dev.activeVideoMinFrameDuration = dur
            dev.activeVideoMaxFrameDuration = dur
            dev.unlockForConfiguration()
            updateBaseFocal()
            return true
        } catch { return false }
    }

    var lowLightBoostSupported: Bool { device?.isLowLightBoostSupported ?? false }

    func setLowLightBoost(_ on: Bool) {
        guard let dev = device, dev.isLowLightBoostSupported else { return }
        do {
            try dev.lockForConfiguration()
            dev.isLowLightBoostEnabled = on
            dev.unlockForConfiguration()
        } catch { /* ignore */ }
    }

    // MARK: - Zoom

    func setZoom(ratio: Double, animated: Bool) {
        guard let dev = device else { return }
        let clamped = min(max(ratio, Double(dev.minAvailableVideoZoomFactor)), Double(dev.maxAvailableVideoZoomFactor))
        do {
            try dev.lockForConfiguration()
            if animated {
                dev.cancelVideoZoomRamp()
                dev.ramp(toVideoZoomFactor: CGFloat(clamped), rate: 6.0)
            } else {
                dev.videoZoomFactor = CGFloat(clamped)
            }
            dev.unlockForConfiguration()
        } catch { /* ignore */ }
    }

    // MARK: - Focus / exposure / torch / flash

    func focus(at devicePoint: CGPoint, exposureBias: Float) {
        guard let dev = device else { return }
        do {
            try dev.lockForConfiguration()
            if dev.isFocusPointOfInterestSupported {
                dev.focusPointOfInterest = devicePoint
                dev.focusMode = .autoFocus
            }
            if dev.isExposurePointOfInterestSupported {
                dev.exposurePointOfInterest = devicePoint
                dev.exposureMode = .autoExpose
            }
            let clamped = min(max(exposureBias, dev.minExposureTargetBias), dev.maxExposureTargetBias)
            dev.setExposureTargetBias(clamped, completionHandler: nil)
            dev.unlockForConfiguration()
        } catch { /* ignore */ }
    }

    func setExposureBias(_ bias: Float) {
        guard let dev = device else { return }
        let clamped = min(max(bias, dev.minExposureTargetBias), dev.maxExposureTargetBias)
        dev.setExposureTargetBias(clamped, completionHandler: nil)
    }

    var exposureBiasRange: ClosedRange<Float> {
        guard let dev = device else { return -2...2 }
        return dev.minExposureTargetBias...dev.maxExposureTargetBias
    }

    func setTorch(_ on: Bool) {
        guard let dev = device, dev.hasTorch else { return }
        do {
            try dev.lockForConfiguration()
            dev.torchMode = on ? .on : .off
            dev.unlockForConfiguration()
        } catch { /* ignore */ }
    }

    // MARK: - Photo

    func capturePhoto(flash: FlashSetting, wantDepth: Bool, completion: @escaping (AVCapturePhoto?, Error?) -> Void) {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let settings = AVCapturePhotoSettings()
            settings.flashMode = flash.photoFlashMode
            settings.photoQualityPrioritization = .quality
            if wantDepth, self.photoOutput.isDepthDataDeliverySupported {
                settings.isDepthDataDeliveryEnabled = true
            }
            if wantDepth, self.photoOutput.isPortraitEffectsMatteDeliverySupported {
                settings.isPortraitEffectsMatteDeliveryEnabled = true
            }
            self.photoCompletion = completion
            self.photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }

    // MARK: - Video recording

    func startRecording(completion: @escaping (URL?, Error?) -> Void) {
        sessionQueue.async { [weak self] in
            guard let self, self.movieOutputAttached else {
                DispatchQueue.main.async { completion(nil, NSError(domain: "KameraIOS", code: -1)) }
                return
            }
            let url = URL(fileURLWithPath: NSTemporaryDirectory())
                .appendingPathComponent("kamera-\(UUID().uuidString).mov")
            self.recordCompletion = completion
            self.applyStabilization()
            self.movieOutput.startRecording(to: url, recordingDelegate: self)
        }
    }

    func stopRecording() {
        sessionQueue.async { [weak self] in
            guard let self, self.movieOutput.isRecording else { return }
            self.movieOutput.stopRecording()
        }
    }

    var isRecording: Bool { movieOutput.isRecording }
}

// MARK: - Delegates

extension CameraEngine: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let completion = photoCompletion
        photoCompletion = nil
        DispatchQueue.main.async { completion?(photo, error) }
    }
}

extension CameraEngine: AVCaptureFileOutputRecordingDelegate {
    func fileOutput(_ output: AVCaptureFileOutput, didFinishRecordingTo outputFileURL: URL,
                    from connections: [AVCaptureConnection], error: Error?) {
        let completion = recordCompletion
        recordCompletion = nil
        DispatchQueue.main.async { completion?(error == nil ? outputFileURL : nil, error) }
    }
}

// MARK: - Photos saving

enum PhotoSaver {
    static func savePhoto(_ data: Data, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { completion(false) }
                return
            }
            PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
            } completionHandler: { ok, _ in
                DispatchQueue.main.async { completion(ok) }
            }
        }
    }

    static func saveVideo(_ url: URL, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { completion(false) }
                return
            }
            PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: .video, fileURL: url, options: nil)
            } completionHandler: { ok, _ in
                DispatchQueue.main.async { completion(ok) }
            }
        }
    }
}
