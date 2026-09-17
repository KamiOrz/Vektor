import AVFoundation
import SwiftUI

struct PlaybackView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showingPlaylistOverlay = false

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                PlaybackHeader {
                    model.resetToScan()
                }

                AdaptiveVideoContainer(
                    videoShape: model.playerService.videoShape,
                    aspectRatio: model.playerService.videoAspectRatio
                ) {
                    VideoSection(player: model.playerService.player)
                }

                if model.playerService.videoShape == .portrait {
                    CurrentChannelCompact(
                        selected: model.selectedChannel,
                        onPrevious: model.previousChannel,
                        onNext: model.nextChannel
                    )
                    PortraitPlaylistTrigger(
                        count: model.visibleChannels.count,
                        selectedGroup: model.selectedGroup
                    ) {
                        showingPlaylistOverlay = true
                    }
                    Spacer(minLength: 0)
                } else {
                    CurrentChannelPanel(
                        selected: model.selectedChannel,
                        onPrevious: model.previousChannel,
                        onNext: model.nextChannel
                    )
                    ChannelListPanel(
                        groups: model.groups,
                        selectedGroup: model.selectedGroup,
                        channels: model.visibleChannels,
                        selected: model.selectedChannel,
                        onGroup: { model.selectedGroup = $0 },
                        onChannel: model.select
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if model.playerService.videoShape == .portrait, showingPlaylistOverlay {
                PlaylistOverlay(
                    groups: model.groups,
                    selectedGroup: model.selectedGroup,
                    channels: model.visibleChannels,
                    selected: model.selectedChannel,
                    onDismiss: { showingPlaylistOverlay = false },
                    onGroup: { model.selectedGroup = $0 },
                    onChannel: { channel in
                        model.select(channel)
                        showingPlaylistOverlay = false
                    }
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .onChange(of: model.playerService.videoShape) { _, shape in
            if shape != .portrait {
                showingPlaylistOverlay = false
            }
        }
    }
}

private struct PlaybackHeader: View {
    let onReset: () -> Void

    var body: some View {
        VektorBrandHeader {
            Button(action: onReset) {
                HStack(spacing: 8) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.system(size: 13, weight: .semibold))
                    Text("SCAN")
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                }
                .foregroundStyle(Color.vektorMuted)
                .padding(.horizontal, 13)
                .frame(height: 36)
                .background(Color.clear)
                .overlay(Rectangle().stroke(Color.white.opacity(0.18), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Scan QR Code")
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 12)
    }
}

private struct AdaptiveVideoContainer<Content: View>: View {
    let videoShape: VideoShape
    let aspectRatio: CGFloat?
    let content: () -> Content

    var body: some View {
        GeometryReader { proxy in
            let ratio = resolvedRatio
            let height = resolvedHeight(width: proxy.size.width, ratio: ratio)
            let width = videoShape == .portrait ? min(proxy.size.width, height * ratio) : proxy.size.width

            ZStack {
                Color.black
                content()
                    .frame(width: width, height: height)
            }
            .frame(width: proxy.size.width, height: height)
        }
        .frame(height: resolvedHeight(width: UIScreen.main.bounds.width, ratio: resolvedRatio))
        .background(Color.black)
    }

    private var resolvedRatio: CGFloat {
        switch videoShape {
        case .portrait:
            return (aspectRatio ?? 9 / 16).clamped(to: 0.46...0.70)
        case .squareOrNeutral:
            return (aspectRatio ?? 1).clamped(to: 0.85...1.25)
        case .landscape:
            return (aspectRatio ?? 16 / 9).clamped(to: 1.25...2.40)
        case .unknown:
            return 16 / 9
        }
    }

    private func resolvedHeight(width: CGFloat, ratio: CGFloat) -> CGFloat {
        let naturalHeight = width / ratio
        if videoShape == .portrait {
            return min(naturalHeight, UIScreen.main.bounds.height * 0.60)
        }
        return naturalHeight
    }
}

private struct VideoSection: View {
    let player: AVPlayer

    var body: some View {
        ZStack(alignment: .top) {
            VideoPlayerView(player: player)
                .background(Color.black)

            Rectangle()
                .fill(Color.vektorGreen)
                .frame(height: 2)
        }
    }
}

private struct CurrentChannelCompact: View {
    let selected: Channel?
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            ChannelTitleBlock(selected: selected, compact: true)

            HStack(spacing: 8) {
                ChannelActionButton("PREV", active: false, action: onPrevious)
                    .frame(width: 66)
                ChannelActionButton("NEXT", active: true, action: onNext)
                    .frame(width: 66)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(Color.vektorPanel)
    }
}

private struct PortraitPlaylistTrigger: View {
    let count: Int
    let selectedGroup: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text("EPISODES")
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color.vektorGreen)
                Text("•")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(Color.vektorMuted.opacity(0.72))
                Text(selectedGroup)
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(Color.vektorMuted.opacity(0.72))
                    .lineLimit(1)
                Spacer()
                Text("\(count) ITEMS")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(Color.vektorMuted.opacity(0.72))
            }
            .padding(.horizontal, 20)
            .frame(height: 46)
            .background(Color.black)
            .overlay(Rectangle().stroke(Color.white.opacity(0.12), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct PlaylistOverlay: View {
    let groups: [String]
    let selectedGroup: String
    let channels: [Channel]
    let selected: Channel?
    let onDismiss: () -> Void
    let onGroup: (String) -> Void
    let onChannel: (Channel) -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.42)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            ChannelListPanel(
                groups: groups,
                selectedGroup: selectedGroup,
                channels: channels,
                selected: selected,
                onGroup: onGroup,
                onChannel: onChannel,
                backgroundOpacity: 0.82,
                title: "EPISODES"
            )
            .frame(height: UIScreen.main.bounds.height * 0.56)
            .onTapGesture {}
        }
        .transition(.opacity)
    }
}

private struct CurrentChannelPanel: View {
    let selected: Channel?
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ChannelTitleBlock(selected: selected, compact: false)

            HStack(spacing: 10) {
                ChannelActionButton("PREV", active: false, action: onPrevious)
                ChannelActionButton("NEXT", active: true, action: onNext)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(Color.vektorPanel)
    }
}

private struct ChannelTitleBlock: View {
    let selected: Channel?
    let compact: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 4 : 8) {
            HStack(spacing: 8) {
                Text((selected?.group ?? "LIVE").uppercased())
                    .font(.system(size: compact ? 11 : 12, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color.vektorGreen)
                    .lineLimit(1)
                Text("•")
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundStyle(Color.vektorMuted.opacity(0.7))
                Text(compact ? "Now" : "Now Playing")
                    .font(.system(size: compact ? 11 : 12, weight: .medium, design: .monospaced))
                    .foregroundStyle(Color.vektorMuted.opacity(0.7))
            }

            Text(selected?.title ?? "Loading Channel")
                .font(.system(size: compact ? 17 : 22, weight: .bold))
                .foregroundStyle(Color.vektorText)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ChannelActionButton: View {
    let label: String
    let active: Bool
    let action: () -> Void

    init(_ label: String, active: Bool, action: @escaping () -> Void) {
        self.label = label
        self.active = active
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundStyle(active ? Color.black : Color.vektorText)
                .frame(maxWidth: .infinity)
                .frame(height: 36)
                .background(active ? Color.vektorGreen : Color.white.opacity(0.06))
                .overlay(Rectangle().stroke(active ? Color.vektorGreen : Color.white.opacity(0.16), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct ChannelListPanel: View {
    let groups: [String]
    let selectedGroup: String
    let channels: [Channel]
    let selected: Channel?
    let onGroup: (String) -> Void
    let onChannel: (Channel) -> Void
    var backgroundOpacity: Double = 1
    var title: String = "PLAYLIST"

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(title)
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color.vektorText)
                Spacer()
                Text("\(channels.count) ITEMS")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(Color.vektorMuted.opacity(0.7))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(groups, id: \.self) { group in
                        GroupTab(
                            title: group,
                            active: group == selectedGroup,
                            action: { onGroup(group) }
                        )
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.bottom, 12)

            Divider().overlay(Color.white.opacity(0.10))

            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(channels) { channel in
                        ChannelRow(
                            channel: channel,
                            isSelected: channel == selected,
                            action: { onChannel(channel) }
                        )
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .padding(.bottom, 18)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.vektorListPanel.opacity(backgroundOpacity))
        .overlay(Rectangle().stroke(Color.white.opacity(0.10), lineWidth: 1))
    }
}

private struct GroupTab: View {
    let title: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundStyle(active ? Color.black : Color.vektorMuted)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(active ? Color.vektorGreen : Color.white.opacity(0.06))
                .overlay(Rectangle().stroke(active ? Color.vektorGreen : Color.white.opacity(0.12), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct ChannelRow: View {
    let channel: Channel
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                ChannelCover(channel: channel)

                VStack(alignment: .leading, spacing: 4) {
                    Text("\(channel.displayNumber) \(channel.title)")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(isSelected ? Color.vektorGreen : Color.vektorText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                    Text(channel.group)
                        .font(.system(size: 12, weight: .medium, design: .monospaced))
                        .foregroundStyle(Color.vektorMuted.opacity(0.55))
                        .lineLimit(1)
                }

                Spacer(minLength: 8)

                if isSelected {
                    Image(systemName: "play.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color.vektorGreen)
                }
            }
            .padding(14)
            .background(isSelected ? Color.white.opacity(0.06) : Color.clear)
            .overlay(alignment: .leading) {
                if isSelected {
                    Rectangle()
                        .fill(Color.vektorGreen)
                        .frame(width: 3)
                }
            }
            .overlay(Rectangle().stroke(isSelected ? Color.vektorGreen.opacity(0.5) : .clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct ChannelCover: View {
    let channel: Channel

    var body: some View {
        ZStack {
            Rectangle()
                .fill(Color.black)
                .overlay(Rectangle().stroke(Color.white.opacity(0.16), lineWidth: 1))

            Text(channel.fallbackLogoText)
                .font(.system(size: 12, weight: .medium, design: .monospaced))
                .foregroundStyle(Color.vektorMuted.opacity(0.75))

            if let logoUrl = channel.logoUrl {
                AsyncImage(url: logoUrl) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                            .background(Color.black)
                    case .empty, .failure:
                        EmptyView()
                    @unknown default:
                        EmptyView()
                    }
                }
            }
        }
        .frame(width: 70, height: 44)
        .clipped()
    }
}

private extension Color {
    static let vektorPanel = Color(red: 8 / 255, green: 8 / 255, blue: 8 / 255)
    static let vektorListPanel = Color(red: 16 / 255, green: 16 / 255, blue: 16 / 255)
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
