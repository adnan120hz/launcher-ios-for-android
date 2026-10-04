import SwiftUI

/// Single doorway for the Liquid Glass look.
///
/// - iOS 26 and later: SwiftUI's real Liquid Glass (`glassEffect`).
/// - iOS 18: our own imitation, mirroring the Android app — a blur
///   material, a specular highlight along the top edge and a bright rim.
///   The user-facing behaviour (bubble swell on the mode capsule,
///   translucent pills) is the same on both paths; only the rendering
///   technology differs, which is stated honestly in Settings.
enum GlassStyle {
    static var usesNativeGlass: Bool {
        if #available(iOS 26.0, *) { return true }
        return false
    }

    static var description: String {
        usesNativeGlass
            ? "Liquid Glass asli iOS 26"
            : "Liquid Glass ala-ala (blur + highlight) untuk iOS 18"
    }
}

/// Translucent glass background for pills/panels with a fully rounded shape.
/// Deliberately calm: a system blur material plus a whisper of top light —
/// no stacked specular gradients, so the preview stays the brightest thing
/// on screen and the layer cost stays at one material.
struct GlassCapsuleBackground: View {
    var tint: Color = .black.opacity(0.22)

    var body: some View {
        if #available(iOS 26.0, *) {
            Capsule()
                .fill(.clear)
                .glassEffect(.regular.tint(tint), in: Capsule())
        } else {
            Capsule()
                .fill(.ultraThinMaterial)
                .overlay(
                    Capsule().fill(
                        LinearGradient(
                            colors: [Color.white.opacity(0.10), Color.white.opacity(0.02), Color.black.opacity(0.05)],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                )
                .overlay(Capsule().stroke(Color.white.opacity(0.22), lineWidth: 1))
        }
    }
}

/// Same idea for rounded-rectangle panels (sheets, cards).
struct GlassPanelBackground: View {
    var cornerRadius: CGFloat = 28
    var tint: Color = .black.opacity(0.26)

    var body: some View {
        if #available(iOS 26.0, *) {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.clear)
                .glassEffect(.regular.tint(tint), in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        } else {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous).fill(
                        LinearGradient(
                            colors: [Color.white.opacity(0.09), Color.white.opacity(0.01), Color.black.opacity(0.06)],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(Color.white.opacity(0.20), lineWidth: 1)
                )
        }
    }
}

/// The selected-mode capsule in the carousel: a glass bubble that swells
/// gently like jelly while pressed/dragged and springs back on release —
/// the same behaviour the Android app ships, kept visually quiet (one
/// glass layer, one soft rim, no specular stack). `squash` is -1...1
/// (drag velocity direction), `active` is true while a finger is down.
struct GlassBubble: View {
    var active: Bool
    var squash: CGFloat

    var body: some View {
        let scaleX = active ? 1.08 + abs(squash) * 0.06 : 1.0
        let scaleY = active ? 1.05 - abs(squash) * 0.03 : 1.0
        GlassCapsuleBackground(tint: .white.opacity(0.08))
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(active ? 0.40 : 0.26), lineWidth: 1)
            )
            .scaleEffect(x: scaleX, y: scaleY)
            .animation(.spring(response: 0.32, dampingFraction: 0.42), value: active)
            .animation(.spring(response: 0.32, dampingFraction: 0.42), value: squash)
    }
}
