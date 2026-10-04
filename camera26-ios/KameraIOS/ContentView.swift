import SwiftUI

/// Main camera screen — same geometry family as the Android app:
/// black band with the pills on top, full-bleed preview in the middle,
/// black strip with zoom stops / shutter / mode carousel at the bottom.
struct ContentView: View {
    @StateObject private var vm = CameraViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var pinchBase: Double? = nil

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            switch vm.launchState {
            case .checkingPermission, .starting:
                launchProgressView
            case .needsPermission:
                permissionRequestView
            case .denied:
                permissionDeniedView
            case .failed(let reason):
                launchFailedView(reason)
            case .ready:
                cameraStack
            }

            if vm.settingsOpen {
                SettingsView(vm: vm)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                    .zIndex(30)
            }
            if vm.onboardingVisible {
                OnboardingView(vm: vm)
                    .transition(.opacity)
                    .zIndex(40)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: vm.settingsOpen)
        .animation(.easeInOut(duration: 0.25), value: vm.onboardingVisible)
        .onAppear { vm.bootstrap() }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .background: vm.shutdown()
            case .active: vm.resume()
            default: break
            }
        }
    }

    // MARK: - Camera stack

    private var cameraStack: some View {
        GeometryReader { geo in
            let topBand: CGFloat = 64 + geo.safeAreaInsets.top
            VStack(spacing: 0) {
                // Black band with pills (centred in the band, per user).
                ZStack(alignment: .bottom) {
                    Color.black
                    TopPills(vm: vm)
                        .padding(.bottom, 12)
                    if vm.isRecording {
                        HStack(spacing: 6) {
                            Circle().fill(.red).frame(width: 9, height: 9)
                            Text(timeString(vm.recordingSeconds))
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 28)
                        .background(GlassCapsuleBackground())
                        .padding(.bottom, 52)
                    }
                    if let banner = vm.bannerText {
                        StatusBanner(text: banner)
                            .padding(.bottom, 52)
                            .task(id: vm.bannerNonce) {
                                try? await Task.sleep(nanoseconds: 1_400_000_000)
                                vm.bannerText = nil
                            }
                    }
                }
                .frame(height: topBand)

                previewArea

                bottomStrip
            }
            .overlay(alignment: .bottom) {
                if vm.sheet != .none {
                    ControlSheet(vm: vm)
                        .padding(.horizontal, 10)
                        .padding(.bottom, 10)
                }
            }
            .animation(.spring(response: 0.42, dampingFraction: 0.86), value: vm.sheet)
            .overlay(alignment: .bottom) {
                if let toast = vm.toastText {
                    Text(toast)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(GlassCapsuleBackground())
                        .padding(.horizontal, 30)
                        .padding(.bottom, 215)
                        .task(id: toast) {
                            try? await Task.sleep(nanoseconds: 2_600_000_000)
                            vm.toastText = nil
                        }
                }
            }
        }
    }

    private var previewArea: some View {
        GeometryReader { p in
            ZStack {
                CameraPreviewView(engine: vm.engine)
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        SpatialTapGesture()
                            .onEnded { value in
                                vm.focus(at: value.location, in: p.size)
                            }
                    )
                    .simultaneousGesture(
                        MagnifyGesture()
                            .onChanged { value in
                                if pinchBase == nil { pinchBase = vm.zoomRatio }
                                vm.zoomByPinch(scale: value.magnification, baseRatio: pinchBase ?? 1)
                            }
                            .onEnded { _ in pinchBase = nil }
                    )
                if vm.gridOn { GridOverlay() }
                if vm.aspect == .square && !vm.mode.isVideoFamily {
                    let bar = max(0, (p.size.height - p.size.width) / 2)
                    VStack(spacing: 0) {
                        Color.black.frame(height: bar)
                        Spacer()
                        Color.black.frame(height: bar)
                    }
                    .allowsHitTesting(false)
                }
                FocusReticle(vm: vm)
                if vm.mode == .pano { panoGuide }
                if let c = vm.countdown {
                    Text("\(c)")
                        .font(.system(size: 96, weight: .bold))
                        .foregroundStyle(.white)
                        .shadow(radius: 12)
                }
            }
        }
    }

    private var panoGuide: some View {
        VStack {
            Spacer()
            ZStack(alignment: .leading) {
                Rectangle().fill(Color.white.opacity(0.55)).frame(height: 1.5)
                Rectangle().fill(Color(red: 1, green: 0.8, blue: 0))
                    .frame(width: nil, height: 2.5)
                    .scaleEffect(x: max(0.02, vm.panoProgress), y: 1, anchor: .leading)
                RoundedRectangle(cornerRadius: 3)
                    .stroke(Color.white, lineWidth: 1.5)
                    .frame(width: 26, height: 18)
                    .overlay(
                        Image(systemName: "chevron.right")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(.white)
                    )
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 40)
            Text(vm.panoActive
                 ? (vm.panoTooFast ? "Terlalu cepat — geser lebih perlahan" : "Geser perlahan… \(vm.panoFrameCount) frame")
                 : "Ketuk tombol rana, lalu geser HP perlahan")
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .frame(height: 30)
                .background(GlassCapsuleBackground())
                .padding(.top, 12)
            Spacer()
        }
    }

    private var bottomStrip: some View {
        VStack(spacing: 4) {
            if vm.dialVisible {
                ZoomDial(vm: vm)
                    .frame(height: 132)
            } else {
                ZoomControls(vm: vm)
                    .frame(height: 42)
            }
            shutterRow
            HStack(spacing: 10) {
                thumbnail
                ModeCarousel(vm: vm)
                flipButton
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
        }
        .padding(.top, 6)
        .padding(.bottom, 10)
        .background(Color.black)
    }

    private var shutterRow: some View {
        Button {
            vm.shutterTapped()
        } label: {
            ZStack {
                Circle()
                    .stroke(Color.white, lineWidth: 4)
                    .frame(width: 72, height: 72)
                if vm.isRecording || vm.timelapseRunning {
                    RoundedRectangle(cornerRadius: 7)
                        .fill(Color.red)
                        .frame(width: 29, height: 29)
                } else {
                    Circle()
                        .fill(vm.mode.isVideoFamily ? Color.red : Color.white)
                        .frame(width: 60, height: 60)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("shutterButton")
        .frame(height: 84)
    }

    private var thumbnail: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color.white.opacity(0.10))
                .frame(width: 46, height: 46)
            if let thumb = vm.lastThumbnail {
                Image(uiImage: thumb)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 46, height: 46)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
        .frame(width: 52)
    }

    private var flipButton: some View {
        Button {
            vm.flipCamera()
        } label: {
            Image(systemName: "arrow.triangle.2.circlepath.camera")
                .font(.system(size: 21))
                .foregroundStyle(.white)
                .frame(width: 46, height: 46)
                .background(Circle().fill(Color.white.opacity(0.10)))
        }
        .buttonStyle(.plain)
        .frame(width: 52)
    }

    // MARK: - Launch states

    private var launchProgressView: some View {
        VStack(spacing: 16) {
            Image(systemName: "camera.fill")
                .font(.system(size: 40))
                .foregroundStyle(Color.white.opacity(0.85))
            Text("Kamera iOS")
                .font(.system(size: 19, weight: .bold)).foregroundStyle(.white)
            ProgressView().tint(.white)
            Text("Menyiapkan kamera…")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.6))
        }
    }

    private var permissionRequestView: some View {
        VStack(spacing: 14) {
            Image(systemName: "camera")
                .font(.system(size: 44))
                .foregroundStyle(Color.white.opacity(0.6))
            Text("Akses kamera diperlukan")
                .font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
            Text("Kamera iOS memakai kamera hanya untuk mengambil foto dan merekam video dari dalam app. Tidak ada yang diunggah.")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.6))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button("Izinkan Akses Kamera") {
                vm.requestCameraPermission()
            }
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(.black)
            .padding(.horizontal, 22)
            .frame(height: 46)
            .background(Capsule().fill(Color(red: 1, green: 0.8, blue: 0)))
            Text("Kamu bisa mengubah izin kapan saja di Pengaturan iOS.")
                .font(.system(size: 11))
                .foregroundStyle(Color.white.opacity(0.4))
        }
    }

    private func launchFailedView(_ reason: String) -> some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 44))
                .foregroundStyle(Color(red: 1, green: 0.8, blue: 0))
            Text("Kamera gagal dimulai")
                .font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
            Text(reason)
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.65))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button("Coba Lagi") {
                vm.retryStart()
            }
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(.black)
            .padding(.horizontal, 22)
            .frame(height: 46)
            .background(Capsule().fill(Color(red: 1, green: 0.8, blue: 0)))
            Button("Buka Pengaturan") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Color(red: 1, green: 0.8, blue: 0))
        }
    }

    private var permissionDeniedView: some View {
        VStack(spacing: 14) {
            Image(systemName: "camera.slash")
                .font(.system(size: 44))
                .foregroundStyle(Color.white.opacity(0.6))
            Text("Izin kamera diperlukan")
                .font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
            Text("Berikan akses kamera (dan mikrofon untuk video) agar Kamera iOS dapat bekerja.")
                .font(.system(size: 13))
                .foregroundStyle(Color.white.opacity(0.6))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button("Buka Pengaturan") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Color(red: 1, green: 0.8, blue: 0))
        }
    }

    private func timeString(_ seconds: Int) -> String {
        String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}
