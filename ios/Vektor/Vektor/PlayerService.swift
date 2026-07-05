import AVFoundation
import MediaPlayer

@MainActor
final class PlayerService: ObservableObject {
    @Published private(set) var player = AVPlayer()
    @Published private(set) var currentChannel: Channel?
    @Published private(set) var videoShape: VideoShape = .unknown
    @Published private(set) var videoAspectRatio: CGFloat?

    private var presentationSizeObservation: NSKeyValueObservation?

    init() {
        configureAudioSession()
        configureRemoteCommands()
    }

    func load(channel: Channel) {
        currentChannel = channel
        videoShape = .unknown
        videoAspectRatio = nil
        pause()
        let item = AVPlayerItem(url: channel.streamUrl)
        observePresentationSize(for: item)
        player.replaceCurrentItem(with: item)
        updateNowPlaying(channel: channel)
        updatePlaybackRate(0)
    }

    func play() {
        player.play()
        updatePlaybackRate(1)
    }

    func pause() {
        player.pause()
        updatePlaybackRate(0)
    }

    func stopAndRelease() {
        pause()
        presentationSizeObservation = nil
        player.replaceCurrentItem(with: nil)
        currentChannel = nil
        videoShape = .unknown
        videoAspectRatio = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func observePresentationSize(for item: AVPlayerItem) {
        presentationSizeObservation = item.observe(\.presentationSize, options: [.initial, .new]) { [weak self] item, _ in
            let size = item.presentationSize
            Task { @MainActor in
                self?.updateVideoShape(size)
            }
        }
    }

    private func updateVideoShape(_ size: CGSize) {
        guard size.width > 0, size.height > 0 else {
            videoShape = .unknown
            videoAspectRatio = nil
            return
        }

        let ratio = size.width / size.height
        videoAspectRatio = ratio
        if ratio < 0.85 {
            videoShape = .portrait
        } else if ratio > 1.25 {
            videoShape = .landscape
        } else {
            videoShape = .squareOrNeutral
        }
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Audio session setup failed: \(error.localizedDescription)")
        }
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.play() }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }
    }

    private func updateNowPlaying(channel: Channel) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: channel.title,
            MPMediaItemPropertyAlbumTitle: channel.group,
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: 0
        ]
    }

    private func updatePlaybackRate(_ rate: Double) {
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        info[MPNowPlayingInfoPropertyPlaybackRate] = rate
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
