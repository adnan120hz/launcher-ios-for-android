import SwiftUI

/// The mode pill under the shutter: TIME-LAPSE · SLO-MO · CINEMATIC ·
/// VIDEO · PHOTO · PORTRAIT · PANO. Labels slide 1:1 with the finger, the
/// glass capsule stays centred and swells like jelly while pressed, and
/// settling moves at most one hop to the next *available* mode — the same
/// rules the Android app settled on after device testing.
struct ModeCarousel: View {
    @ObservedObject var vm: CameraViewModel
    private let slot: CGFloat = 84
    @State private var squash: CGFloat = 0

    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        GeometryReader { geo in
            let modes = CameraMode.allCases
            let current = modes.firstIndex(of: vm.mode) ?? 4
            ZStack {
                GlassCapsuleBackground()
                // Glass bubble behind the selected label.
                GlassBubble(active: vm.carouselPressed, squash: squash)
                    .frame(width: slot - 8, height: 34)
                HStack(spacing: 0) {
                    ForEach(modes) { m in
                        Text(m.label)
                            .font(.system(size: 12.5, weight: vm.mode == m ? .bold : .semibold))
                            .foregroundStyle(color(for: m))
                            .frame(width: slot, height: 40)
                    }
                }
                .offset(x: geo.size.width / 2 - slot / 2 - CGFloat(current) * slot + vm.carouselDragPx)
                .frame(width: geo.size.width, height: 44)
                .mask(
                    LinearGradient(
                        stops: [
                            .init(color: .clear, location: 0),
                            .init(color: .black, location: 0.12),
                            .init(color: .black, location: 0.88),
                            .init(color: .clear, location: 1),
                        ],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
            }
            .clipShape(Capsule())
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 2)
                    .onChanged { value in
                        vm.carouselPressed = true
                        vm.carouselDragPx = value.translation.width
                        squash = min(1, max(-1, value.velocity.width / 1600))
                    }
                    .onEnded { value in
                        vm.settleCarousel(translation: value.translation.width)
                        vm.carouselPressed = false
                        squash = 0
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.8)) {
                            vm.carouselDragPx = 0
                        }
                    }
            )
        }
        .frame(height: 44)
    }

    private func color(for m: CameraMode) -> Color {
        if m == vm.mode { return yellow }
        return vm.modeAvailable(m) ? Color.white.opacity(0.75) : Color.white.opacity(0.32)
    }
}
