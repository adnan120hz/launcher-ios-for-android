import AVFoundation
import CoreMotion
import SwiftUI
import UIKit

/// Glue between the SwiftUI surface and the AVFoundation engine.
/// Mirrors the Android CameraState/CameraController split: every control
/// in the UI is wired to a real engine behaviour, and anything the device
/// cannot do stays visibly dimmed with an honest explanation.
@MainActor
final class CameraViewModel: ObservableObject {
    let engine = CameraEngine()
    private let defaults = UserDefaults.standard
    private let motion = CMMotionManager()

    // Lifecycle / permissions
    /// Explicit, platform-native launch sequence: the capture engine is
    /// only ever configured after camera authorization is known-granted,
    /// asking is an explicit user action, and every failure lands on a
    /// visible screen with a reason — never a black, dead preview.
    enum LaunchState: Equatable {
        case checkingPermission
        case needsPermission   // notDetermined: wait for the user to tap
        case denied
        case starting
        case ready
        case failed(String)
    }
    @Published var launchState: LaunchState = .checkingPermission
    var isReady: Bool { launchState == .ready }
    private var didBootstrap = false

    // Modes & sheets
    @Published var mode: CameraMode = .photo
    @Published var sheet: SheetKind = .none
    @Published var settingsOpen = false
    @Published var onboardingVisible = false
    @Published var facingFront = false

    // Photo settings
    @Published var flash: FlashSetting = .auto
    @Published var timerSec = 0
    @Published var aspect: PhotoAspect = .ratio43
    @Published var gridOn = false
    @Published var nightOn = false

    // Grades & CONFIG
    @Published var styleId: String? = nil
    @Published var filterId: String? = nil
    @Published var styleIntensity: Double = 1.0   // 0...1.5
    @Published var styleTone: Double = 0         // -1...1 warmth shift
    @Published var apertureF: Double = 2.8       // portrait blur guide label
    @Published var configPresetId = "default"
    @Published var config = PhotoConfig()

    // Video / ACTION
    @Published var actionOn = false
    @Published var torchOn = false
    @Published var eisEnabled = true
    @Published var stabSourceLabel = ""
    @Published var selectedFormat: VideoFormatOption? = nil
    @Published var fps = 30

    // Zoom
    @Published var zoomRatio: Double = 1
    @Published var dialVisible = false

    // Carousel
    @Published var carouselDragPx: CGFloat = 0
    @Published var carouselPressed = false

    // Focus
    @Published var focusViewPoint: CGPoint? = nil
    @Published var focusBias: Float = 0
    @Published var focusNonce = 0

    // Transient UI
    @Published var bannerText: String? = nil
    @Published var bannerNonce = 0
    @Published var toastText: String? = nil
    @Published var countdown: Int? = nil

    // Capture state
    @Published var isRecording = false
    @Published var recordingSeconds = 0
    @Published var timelapseRunning = false
    @Published var timelapseFrames = 0
    @Published var panoActive = false
    @Published var panoProgress: Double = 0
    @Published var panoTooFast = false
    @Published var panoFrameCount = 0
    @Published var lastThumbnail: UIImage? = nil

    // Update
    @Published var updateTag: String? = nil
    @Published var updateURL: String? = nil

    private var recordTimer: Timer?
    private var zoomPollTimer: Timer?
    private var timelapseTimer: Timer?
    private var timelapseImages: [UIImage] = []
    private var panoImages: [Data] = []
    private var panoStartYaw: Double?
    private var panoLastYaw: Double = 0
    private var onboarded: Bool { defaults.bool(forKey: "onboarded") }

    var caps: CameraCapabilities { engine.capabilities }

    var videoPillLabel: String {
        guard let fmt = selectedFormat else { return "HD RES \(fps) FPS" }
        return "\(fmt.label) RES \(fps) FPS"
    }

    // MARK: - Bootstrap

    func bootstrap() {
        guard !didBootstrap else { return }
        didBootstrap = true
        loadPersisted()
        if !onboarded { onboardingVisible = true }
        engine.onRuntimeError = { [weak self] reason in
            guard let self else { return }
            if self.launchState == .ready {
                self.showToast(reason)
            } else {
                self.launchState = .failed(reason)
            }
        }
        evaluateCameraAuthorization(requestIfNeeded: false)
    }

    /// Reads the current authorization first; the system prompt is only
    /// triggered from the permission screen's button (`requestIfNeeded`).
    private func evaluateCameraAuthorization(requestIfNeeded: Bool) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            launchState = .starting
            startEngine()
        case .notDetermined:
            if requestIfNeeded {
                Permissions.requestCamera { [weak self] granted in
                    guard let self else { return }
                    if granted {
                        self.launchState = .starting
                        self.startEngine()
                    } else {
                        self.launchState = .denied
                    }
                }
            } else {
                launchState = .needsPermission
            }
        default:
            launchState = .denied
        }
    }

    /// The permission screen's button — asking is an explicit user action.
    func requestCameraPermission() {
        evaluateCameraAuthorization(requestIfNeeded: true)
    }

    /// Retry from the failed-state screen.
    func retryStart() {
        launchState = .starting
        startEngine()
    }

    private func startEngine() {
        engine.configure(position: facingFront ? .front : .back) { [weak self] ok in
            guard let self else { return }
            guard ok else {
                self.launchState = .failed("Kamera tidak ditemukan atau gagal dimulai di perangkat ini.")
                return
            }
            self.engine.start()
            self.engine.setVideoOutputAttached(false)
            self.selectedFormat = self.engine.capabilities.videoFormats.first { $0.label == "HD" }
                ?? self.engine.capabilities.videoFormats.first
            if let fmt = self.selectedFormat, !fmt.fpsOptions.contains(self.fps) {
                self.fps = fmt.fpsOptions.first ?? 30
            }
            self.refreshStabLabel()
            self.launchState = .ready
            self.checkForUpdate()
        }
    }

    func shutdown() {
        engine.stop()
        stopRecordTimer()
    }

    func resume() {
        if launchState == .ready { engine.start() }
    }

    /// Carousel settle — one gesture, at most one hop, always landing on
    /// the next *available* mode in the drag direction (dimmed modes are
    /// skipped, never landed on, never blocking the swipe).
    func settleCarousel(translation: CGFloat) {
        let modes = CameraMode.allCases
        guard let idx = modes.firstIndex(of: mode) else { return }
        let threshold: CGFloat = 30
        var step = 0
        if translation < -threshold { step = 1 }
        else if translation > threshold { step = -1 }
        guard step != 0 else { return }
        var target = idx + step
        while target >= 0 && target < modes.count && !modeAvailable(modes[target]) {
            target += step
        }
        if target >= 0 && target < modes.count && target != idx {
            selectMode(modes[target])
        }
    }

    // MARK: - Persistence

    private func loadPersisted() {
        flash = FlashSetting(rawValue: defaults.string(forKey: "flash") ?? "auto") ?? .auto
        aspect = PhotoAspect(rawValue: defaults.string(forKey: "aspect") ?? "ratio43") ?? .ratio43
        gridOn = defaults.bool(forKey: "grid")
        styleId = defaults.string(forKey: "style_id")
        filterId = defaults.string(forKey: "filter_id")
        eisEnabled = defaults.object(forKey: "eis_enabled") as? Bool ?? true
        configPresetId = defaults.string(forKey: "config_preset") ?? "default"
        config = PhotoConfig(
            sharpness: defaults.double(forKey: "config_sharpness"),
            saturation: defaults.object(forKey: "config_saturation") as? Double ?? 1,
            contrast: defaults.object(forKey: "config_contrast") as? Double ?? 1,
            gamma: defaults.object(forKey: "config_gamma") as? Double ?? 1,
            denoise: defaults.double(forKey: "config_denoise")
        )
    }

    func persist() {
        defaults.set(flash.rawValue, forKey: "flash")
        defaults.set(aspect.rawValue, forKey: "aspect")
        defaults.set(gridOn, forKey: "grid")
        defaults.set(styleId, forKey: "style_id")
        defaults.set(filterId, forKey: "filter_id")
        defaults.set(eisEnabled, forKey: "eis_enabled")
        defaults.set(configPresetId, forKey: "config_preset")
        defaults.set(config.sharpness, forKey: "config_sharpness")
        defaults.set(config.saturation, forKey: "config_saturation")
        defaults.set(config.contrast, forKey: "config_contrast")
        defaults.set(config.gamma, forKey: "config_gamma")
        defaults.set(config.denoise, forKey: "config_denoise")
    }

    func applyConfigPreset(_ preset: PhotoConfigPreset) {
        configPresetId = preset.id
        config = preset.config
        persist()
    }

    func markConfigCustom() {
        if configPresetId != "custom" { configPresetId = "custom" }
    }

    // MARK: - Banner / toast

    func showBanner(_ text: String) {
        bannerText = text
        bannerNonce += 1
    }

    func showToast(_ text: String) {
        toastText = text
    }

    // MARK: - Mode availability & selection

    func modeAvailable(_ m: CameraMode) -> Bool {
        // All five remaining modes run on every device (photo-output based
        // modes need nothing exotic; VIDEO uses the movie output). Nothing
        // is listed that the engine cannot actually do.
        return true
    }

    func selectMode(_ m: CameraMode) {
        guard m != mode else { return }
        guard modeAvailable(m) else { return }
        // Leaving a running activity cleanly.
        if isRecording { stopVideoRecording() }
        if timelapseRunning { stopTimelapse() }
        if panoActive { cancelPano() }
        mode = m
        sheet = .none
        dialVisible = false
        engine.setVideoOutputAttached(m == .video)
        if m == .video {
            applyVideoSettings()
            ensureMicrophoneForVideo()
        }
    }

    func flipCamera() {
        if isRecording { stopVideoRecording() }
        facingFront.toggle()
        engine.configure(position: facingFront ? .front : .back) { [weak self] ok in
            guard let self, ok else { return }
            // Formats belong to a device: re-pick from the new camera's
            // real list before anything applies the old pick to it.
            self.selectedFormat = self.engine.capabilities.videoFormats.first { $0.label == "HD" }
                ?? self.engine.capabilities.videoFormats.first
            self.engine.setVideoOutputAttached(self.mode == .video)
            self.zoomRatio = 1
            if self.mode == .video {
                self.applyVideoSettings()
                self.ensureMicrophoneForVideo()
            }
        }
    }

    // MARK: - Video settings

    private func applyVideoSettings() {
        guard let fmt = selectedFormat else { return }
        engine.applyVideoFormat(fmt, fps: fps)
        engine.stabilizationMode = (actionOn && eisEnabled) ? .cinematicExtended : .off
        engine.applyStabilization()
        refreshStabLabel()
    }

    /// Microphone, asked for in its real context — the user has just
    /// entered VIDEO mode or is about to record — never at launch. If
    /// access is refused, recording still works, only without sound, and
    /// the toast says exactly that.
    private func ensureMicrophoneForVideo() {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized:
            engine.setAudioInputAttached(true)
        case .notDetermined:
            Permissions.requestMicrophone { [weak self] granted in
                guard let self else { return }
                if granted {
                    self.engine.setAudioInputAttached(true)
                } else {
                    self.showToast("Mikrofon tidak diizinkan — video direkam tanpa suara.")
                }
            }
        default:
            showToast("Mikrofon mati — video direkam tanpa suara. Aktifkan di Pengaturan iOS bila ingin bersuara.")
        }
    }

    func selectFormat(_ fmt: VideoFormatOption) {
        selectedFormat = fmt
        if !fmt.fpsOptions.contains(fps) { fps = fmt.fpsOptions.first ?? 30 }
        applyVideoSettings()
    }

    func selectFps(_ value: Int) {
        fps = value
        applyVideoSettings()
    }

    func refreshStabLabel() {
        stabSourceLabel = (actionOn && eisEnabled) ? engine.stabilizationLabel() : "Stabilisasi nonaktif"
    }

    func setAction(_ on: Bool) {
        actionOn = on
        applyVideoSettings()
        showBanner(on ? "ACTION MODE ON" : "ACTION MODE OFF")
    }

    func setEisEnabled(_ on: Bool) {
        eisEnabled = on
        persist()
        applyVideoSettings()
        showBanner(on ? "STABILISASI AKTIF" : "STABILISASI NONAKTIF")
    }

    func setTorch(_ on: Bool) {
        torchOn = on
        engine.setTorch(on)
    }

    // MARK: - Zoom

    func setZoom(ratio: Double, animated: Bool = true) {
        engine.setZoom(ratio: ratio, animated: animated)
        startZoomPolling()
    }

    private func startZoomPolling() {
        zoomPollTimer?.invalidate()
        zoomPollTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] timer in
            guard let self else { timer.invalidate(); return }
            let current = self.engine.zoomRatio
            if abs(current - self.zoomRatio) > 0.005 {
                self.zoomRatio = current
            } else if self.engine.device?.isRampingVideoZoom == false {
                self.zoomRatio = current
                timer.invalidate()
            }
        }
    }

    func zoomByPinch(scale: CGFloat, baseRatio: Double) {
        let target = baseRatio * Double(scale)
        engine.setZoom(ratio: target, animated: false)
        // Engine mutations now land on the session queue, so publish the
        // (clamped) target instead of reading the device back too early;
        // the zoom poll keeps this honest while a ramp runs.
        zoomRatio = min(max(target, caps.minZoom), caps.maxZoom)
    }

    // MARK: - Focus

    func focus(at viewPoint: CGPoint, in viewSize: CGSize) {
        let layer = engine.previewLayer
        let devicePoint = layer.captureDevicePointConverted(fromLayerPoint: viewPoint)
        engine.focus(at: devicePoint, exposureBias: focusBias)
        focusViewPoint = viewPoint
        focusNonce += 1
    }

    func setFocusBias(_ bias: Float) {
        focusBias = bias
        engine.setExposureBias(bias)
    }

    // MARK: - Shutter

    func shutterTapped() {
        switch mode {
        case .photo, .portrait:
            runWithTimer { self.captureStill() }
        case .video:
            isRecording ? stopVideoRecording() : startVideoRecording()
        case .timeLapse:
            timelapseRunning ? stopTimelapse() : startTimelapse()
        case .pano:
            panoActive ? finishPano() : startPano()
        }
    }

    private func runWithTimer(_ action: @escaping () -> Void) {
        guard timerSec > 0 else { action(); return }
        countdown = timerSec
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] timer in
            guard let self else { timer.invalidate(); return }
            if let c = self.countdown, c > 1 {
                self.countdown = c - 1
            } else {
                timer.invalidate()
                self.countdown = nil
                action()
            }
        }
    }

    private func captureStill() {
        let wantDepth = (mode == .portrait)
        engine.capturePhoto(flash: flash, wantDepth: wantDepth) { [weak self] photo, _ in
            guard let self, let photo, let raw = photo.fileDataRepresentation() else { return }
            let depth = wantDepth ? photo.depthData : nil
            let cfg = self.config
            let grade = self.activeGrade()
            let intensity = self.styleIntensity
            let asp = self.aspect
            DispatchQueue.global(qos: .userInitiated).async {
                let processed = PhotoProcessor.process(photoData: raw, config: cfg, grade: grade,
                                                       styleIntensity: intensity, aspect: asp,
                                                       portraitDepth: depth)
                PhotoSaver.savePhoto(processed) { ok in
                    if ok, let thumb = UIImage(data: processed) {
                        self.lastThumbnail = thumb
                    }
                }
            }
        }
    }

    func activeGrade() -> GradePreset? {
        if let fid = filterId, let preset = filterPresets.first(where: { $0.id == fid }), fid != "none" {
            return GradePreset(id: preset.id, label: preset.label, saturation: preset.saturation,
                               contrast: preset.contrast, warmth: preset.warmth + styleTone * 600, mono: preset.mono)
        }
        if let sid = styleId, let preset = stylePresets.first(where: { $0.id == sid }), sid != "standard" {
            return GradePreset(id: preset.id, label: preset.label, saturation: preset.saturation,
                               contrast: preset.contrast, warmth: preset.warmth + styleTone * 600, mono: preset.mono)
        }
        return nil
    }

    // MARK: - Video recording

    private func startVideoRecording() {
        // Permission was already requested on entering VIDEO; this just
        // re-attaches if it was granted in the meantime (no-op otherwise,
        // and the engine itself refuses without authorization).
        engine.setAudioInputAttached(true)
        engine.startRecording { [weak self] url, _ in
            guard let self else { return }
            self.isRecording = false
            self.stopRecordTimer()
            guard let url else { return }
            PhotoSaver.saveVideo(url) { _ in }
            self.setVideoThumbnail(url)
        }
        isRecording = true
        recordingSeconds = 0
        recordTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            self?.recordingSeconds += 1
        }
    }

    private func stopVideoRecording() {
        engine.stopRecording()
    }

    private func stopRecordTimer() {
        recordTimer?.invalidate()
        recordTimer = nil
    }

    private func setVideoThumbnail(_ url: URL) {
        let asset = AVAsset(url: url)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 120, height: 120)
        if let cg = try? generator.copyCGImage(at: CMTime(seconds: 0.1, preferredTimescale: 600), actualTime: nil) {
            lastThumbnail = UIImage(cgImage: cg)
        }
    }

    // MARK: - Time-lapse

    private func startTimelapse() {
        timelapseImages = []
        timelapseFrames = 0
        timelapseRunning = true
        captureTimelapseFrame()
        timelapseTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.captureTimelapseFrame()
        }
        showBanner("TIME-LAPSE MEREKAM")
    }

    private func captureTimelapseFrame() {
        engine.capturePhoto(flash: .off, wantDepth: false) { [weak self] photo, _ in
            guard let self, let photo, let data = photo.fileDataRepresentation(),
                  let image = UIImage(data: data) else { return }
            self.timelapseImages.append(image)
            self.timelapseFrames = self.timelapseImages.count
        }
    }

    private func stopTimelapse() {
        timelapseTimer?.invalidate()
        timelapseTimer = nil
        timelapseRunning = false
        let frames = timelapseImages
        timelapseImages = []
        guard frames.count > 1 else { return }
        VideoTools.assembleTimeLapse(images: frames) { url in
            if let url { PhotoSaver.saveVideo(url) { _ in } }
        }
        showBanner("TIME-LAPSE SELESAI")
    }

    // MARK: - Panorama (guided sweep; translation stitch — see PanoStitcher)

    private func startPano() {
        panoImages = []
        panoProgress = 0
        panoFrameCount = 0
        panoTooFast = false
        panoActive = true
        panoStartYaw = nil
        guard motion.isDeviceMotionAvailable else {
            // No motion sensor path: timed captures with the same guide.
            capturePanoFrame()
            return
        }
        motion.deviceMotionUpdateInterval = 0.05
        motion.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: .main) { [weak self] data, _ in
            guard let self, self.panoActive, let yaw = data?.attitude.yaw else { return }
            if self.panoStartYaw == nil {
                self.panoStartYaw = yaw
                self.panoLastYaw = yaw
                self.capturePanoFrame()
                return
            }
            let start = self.panoStartYaw ?? yaw
            var swept = abs(yaw - start)
            if swept > .pi { swept = 2 * .pi - swept }
            self.panoProgress = min(1, swept / (150 * .pi / 180))
            let step = abs(yaw - self.panoLastYaw)
            if step > (40 * .pi / 180) * 0.25 { self.panoTooFast = step > 0.30 }
            if step >= (12 * .pi / 180) {
                self.panoLastYaw = yaw
                self.capturePanoFrame()
            }
            if self.panoProgress >= 1 || self.panoImages.count >= 14 {
                self.finishPano()
            }
        }
        showBanner("Geser HP perlahan ke satu arah")
    }

    private func capturePanoFrame() {
        engine.capturePhoto(flash: .off, wantDepth: false) { [weak self] photo, _ in
            guard let self, let photo, let data = photo.fileDataRepresentation() else { return }
            self.panoImages.append(data)
            self.panoFrameCount = self.panoImages.count
        }
    }

    private func finishPano() {
        let frames = panoImages
        cancelPano()
        guard frames.count > 1 else {
            showToast("Geseran terlalu pendek — panorama belum cukup frame.")
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            if let result = PanoStitcher.stitch(images: frames) {
                PhotoSaver.savePhoto(result) { ok in
                    if ok, let thumb = UIImage(data: result) { self.lastThumbnail = thumb }
                }
            } else {
                // Honest fallback: keep the middle frame.
                PhotoSaver.savePhoto(frames[frames.count / 2]) { _ in }
                self.showToast("Jahitan panorama gagal — disimpan foto terbaik.")
            }
        }
    }

    private func cancelPano() {
        panoActive = false
        panoImages = []
        motion.stopDeviceMotionUpdates()
    }

    // MARK: - Onboarding / update

    func finishOnboarding() {
        defaults.set(true, forKey: "onboarded")
        onboardingVisible = false
    }

    func reopenOnboarding() {
        settingsOpen = false
        onboardingVisible = true
    }

    private func checkForUpdate() {
        UpdateChecker.shared.check { [weak self] hit in
            self?.updateTag = hit?.tag
            self?.updateURL = hit?.url
        }
    }

    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.0.0"
    }
}
