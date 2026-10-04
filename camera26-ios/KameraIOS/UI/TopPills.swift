import SwiftUI

/// Top-of-screen pills, mirroring the Android layout: the format pill on
/// the left (video family), the contextual pill on the right, both
/// centred in the black band above the preview. Every icon gets a 40pt
/// hit target even though the glyph is smaller (device-test fix).
struct TopPills: View {
    @ObservedObject var vm: CameraViewModel
    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        HStack(alignment: .center) {
            if vm.mode.isVideoFamily && vm.mode != .timeLapse {
                Button {
                    vm.sheet = vm.sheet == .resolution ? .none : .resolution
                } label: {
                    HStack(spacing: 4) {
                        Text(vm.selectedFormat?.label ?? "HD")
                            .font(.system(size: 12, weight: .bold))
                        Text("RES")
                            .font(.system(size: 9, weight: .bold))
                            .offset(y: 1)
                        Text("\(vm.fps) FPS")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .frame(height: 34)
                    .background(GlassCapsuleBackground())
                }
                .buttonStyle(.plain)
            } else {
                Spacer().frame(width: 1)
            }

            Spacer()

            HStack(spacing: 2) {
                pillIcon("moon.fill", active: vm.nightOn) {
                    vm.nightOn.toggle()
                    vm.engine.setLowLightBoost(vm.nightOn)
                    vm.showBanner(vm.nightOn ? "NIGHT MODE ON" : "NIGHT MODE OFF")
                }
                pillIcon("bolt.fill",
                         active: vm.flash != .off || vm.torchOn) {
                    vm.sheet = .flash
                }
                if vm.facingFront {
                    // LIVE on the front camera: not implemented on either
                    // platform build — dimmed and honest, never blocking.
                    Button {
                        vm.showToast("Live Photo belum tersedia di build ini.")
                    } label: {
                        Image(systemName: "livephoto.slash")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.white.opacity(0.32))
                            .frame(width: 40, height: 34)
                    }
                    .buttonStyle(.plain)
                }
                pillIcon("square.grid.2x2", active: vm.styleId != nil || vm.filterId != nil) {
                    vm.sheet = .styles
                }
                pillIcon("grid", active: vm.sheet == .grid) {
                    vm.sheet = vm.sheet == .grid ? .none : .grid
                }
            }
            .padding(.horizontal, 6)
            .frame(height: 34)
            .background(GlassCapsuleBackground())
        }
        .padding(.horizontal, 14)
    }

    private func pillIcon(_ symbol: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(active ? yellow : .white)
                .frame(width: 40, height: 34)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Transient status pill ("FLASH ON", "NIGHT MODE OFF", ...) auto-hiding
/// after ~1.4 seconds — same behaviour as the Android banner.
struct StatusBanner: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .frame(height: 30)
            .background(GlassCapsuleBackground())
            .transition(.opacity.combined(with: .scale(scale: 0.9)))
    }
}
