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
struct GlassCapsuleBackground: View {
    var tint: Color = .black.opacity(0.28)

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
                            colors: [Color.white.opacity(0.20), Color.white.opacity(0.03), Color.black.opacity(0.10)],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                )
                .overlay(Capsule().stroke(Color.white.opacity(0.35), lineWidth: 1))
        }
    }
}

/// Same idea for rounded-rectangle panels (sheets, cards).
struct GlassPanelBackground: View {
    var cornerRadius: CGFloat = 28
    var tint: Color = .black.opacity(0.30)

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
                            colors: [Color.white.opacity(0.16), Color.white.opacity(0.02), Color.black.opacity(0.12)],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(Color.white.opacity(0.30), lineWidth: 1)
                )
        }
    }
}

/// The selected-mode capsule in the carousel: a glass bubble that swells
/// like jelly while pressed/dragged and springs back on release — the
/// same behaviour the Android app ships. `squash` is -1...1 (drag
/// velocity direction), `active` is true while a finger is down.
struct GlassBubble: View {
    var active: Bool
    var squash: CGFloat

    var body: some View {
        let scaleX = active ? 1.14 + abs(squash) * 0.10 : 1.0
        let scaleY = active ? 1.10 - abs(squash) * 0.04 : 1.0
        ZStack {
            GlassCapsuleBackground(tint: .white.opacity(0.10))
            Capsule()
                .fill(
                    LinearGradient(
                        colors: [Color.white.opacity(active ? 0.38 : 0.22), Color.white.opacity(0.05)],
                        startPoint: .top, endPoint: .bottom
                    )
                )
            Capsule()
                .stroke(Color.white.opacity(active ? 0.75 : 0.45), lineWidth: 1)
                .blur(radius: 0.4)
        }
        .scaleEffect(x: scaleX, y: scaleY)
        .animation(.spring(response: 0.32, dampingFraction: 0.42), value: active)
        .animation(.spring(response: 0.32, dampingFraction: 0.42), value: squash)
    }
}
