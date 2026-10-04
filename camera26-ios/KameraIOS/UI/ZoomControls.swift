import SwiftUI

/// Quick zoom stops (0.5 · 1x · 2x · 8x pattern from the real lens range)
/// plus the press-and-hold arc dial. Buttons jump; the dial sweeps.
/// Dragging right always zooms in — the dial never fights the finger.
struct ZoomControls: View {
    @ObservedObject var vm: CameraViewModel
    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        HStack(spacing: 9) {
            ForEach(vm.caps.zoomStops, id: \.self) { stop in
                Button {
                    vm.setZoom(ratio: stop)
                } label: {
                    Text(label(stop))
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(isCurrent(stop) ? yellow : .white)
                        .frame(width: 37, height: 37)
                        .background(Circle().fill(Color.black.opacity(0.45)))
                        .overlay(
                            Circle().stroke(isCurrent(stop) ? yellow : Color.white.opacity(0.25),
                                            lineWidth: isCurrent(stop) ? 1.4 : 1)
                        )
                }
                .buttonStyle(.plain)
                .simultaneousGesture(
                    LongPressGesture(minimumDuration: 0.35).onEnded { _ in
                        vm.dialVisible = true
                    }
                )
            }
            Text("\(vm.engine.focalMM)mm")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.white.opacity(0.75))
                .frame(minWidth: 44, alignment: .leading)
        }
    }

    private func isCurrent(_ stop: Double) -> Bool {
        abs(vm.zoomRatio - stop) < 0.06
    }

    private func label(_ stop: Double) -> String {
        if stop == floor(stop) { return "\(Int(stop))x" }
        return String(format: "%.1fx", stop)
    }
}

/// Semicircular zoom dial shown after long-pressing a stop. Horizontal
/// drag maps exponentially to zoom ratio (right = in), no spring fights
/// the value while dragging.
struct ZoomDial: View {
    @ObservedObject var vm: CameraViewModel
    @State private var startRatio: Double = 1
    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        VStack(spacing: 4) {
            ZStack {
                Circle()
                    .trim(from: 0.52, to: 0.98)
                    .stroke(Color.white.opacity(0.25), lineWidth: 2)
                    .frame(width: 210, height: 210)
                ForEach(0..<26, id: \.self) { i in
                    ZStack(alignment: .top) {
                        Capsule()
                            .fill(Color.white.opacity(i % 5 == 0 ? 0.8 : 0.35))
                            .frame(width: 2, height: i % 5 == 0 ? 12 : 7)
                    }
                    .frame(width: 210, height: 210)
                    .rotationEffect(.degrees(-84 + Double(i) / 25.0 * 168))
                }
                VStack(spacing: 1) {
                    Text(String(format: "%.1fx", vm.zoomRatio))
                        .font(.system(size: 21, weight: .bold))
                        .foregroundStyle(yellow)
                    Text("\(vm.engine.focalMM) MM")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.7))
                }
                .offset(y: 26)
            }
            .frame(height: 128)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let factor = exp(Double(value.translation.width) / 260.0)
                        vm.setZoom(ratio: startRatio * factor, animated: false)
                    }
                    .onEnded { _ in
                        // Authoritative snapshot from the device itself,
                        // so the next drag continues exactly where the
                        // zoom actually is — never a stale view value.
                        startRatio = vm.engine.zoomRatio
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            vm.dialVisible = false
                        }
                    }
            )
            .onAppear { startRatio = vm.zoomRatio }
        }
    }
}

/// Yellow tap-to-focus square with the sun slider on its right track.
/// Dragging the sun up brightens (+EV), down darkens — value follows the
/// finger exactly and stays where released.
struct FocusReticle: View {
    @ObservedObject var vm: CameraViewModel
    @State private var startBias: Float = 0

    private let yellow = Color(red: 1.0, green: 0.8, blue: 0.0)

    var body: some View {
        if let point = vm.focusViewPoint {
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .stroke(yellow, lineWidth: 1.5)
                    .frame(width: 74, height: 74)
                HStack(spacing: 0) {
                    Spacer().frame(width: 74)
                    ZStack {
                        Rectangle()
                            .fill(Color.white.opacity(0.5))
                            .frame(width: 1.5, height: 74)
                        let range = vm.engine.exposureBiasRange
                        let fraction = Double((vm.focusBias - range.lowerBound) / (range.upperBound - range.lowerBound))
                        Image(systemName: "sun.max.fill")
                            .font(.system(size: 15))
                            .foregroundStyle(yellow)
                            .offset(y: 37 - fraction * 74)
                    }
                    .frame(width: 26, height: 74)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let range = vm.engine.exposureBiasRange
                                let span = range.upperBound - range.lowerBound
                                let delta = -Float(value.translation.height) / 74 * span / 2
                                vm.setFocusBias(min(max(startBias + delta, range.lowerBound), range.upperBound))
                            }
                            .onEnded { _ in startBias = vm.focusBias }
                    )
                }
                .frame(width: 100)
            }
            .frame(width: 100, height: 74)
            .position(point)
            .id(vm.focusNonce)
            .onAppear { startBias = vm.focusBias }
            .transition(.scale(scale: 1.25).combined(with: .opacity))
        }
    }
}
