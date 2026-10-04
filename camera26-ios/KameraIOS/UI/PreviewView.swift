import AVFoundation
import SwiftUI

/// Hosts the engine's AVCaptureVideoPreviewLayer.
struct CameraPreviewView: UIViewRepresentable {
    let engine: CameraEngine

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.attach(engine.previewLayer)
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {}
}

final class PreviewUIView: UIView {
    private var previewLayer: AVCaptureVideoPreviewLayer?

    func attach(_ layer: AVCaptureVideoPreviewLayer) {
        previewLayer?.removeFromSuperlayer()
        previewLayer = layer
        self.layer.addSublayer(layer)
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }
}

/// 3x3 composition grid, shown when the user enables it.
struct GridOverlay: View {
    var body: some View {
        GeometryReader { geo in
            Path { path in
                for i in 1...2 {
                    let x = geo.size.width * CGFloat(i) / 3
                    path.move(to: CGPoint(x: x, y: 0))
                    path.addLine(to: CGPoint(x: x, y: geo.size.height))
                    let y = geo.size.height * CGFloat(i) / 3
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: geo.size.width, y: y))
                }
            }
            .stroke(Color.white.opacity(0.35), lineWidth: 0.7)
        }
        .allowsHitTesting(false)
    }
}
