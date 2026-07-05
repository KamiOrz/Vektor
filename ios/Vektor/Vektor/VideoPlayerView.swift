import AVKit
import SwiftUI

struct VideoPlayerView: UIViewControllerRepresentable {
    let player: AVPlayer

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = true
        controller.videoGravity = .resizeAspect
        if #available(iOS 16.0, *) {
            controller.allowsVideoFrameAnalysis = false
        }
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        controller.player = player
        controller.showsPlaybackControls = true
        controller.videoGravity = .resizeAspect
        if #available(iOS 16.0, *) {
            controller.allowsVideoFrameAnalysis = false
        }
    }
}
