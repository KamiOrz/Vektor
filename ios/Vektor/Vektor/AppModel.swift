import Foundation
import UIKit

@MainActor
final class AppModel: ObservableObject {
    @Published var route: AppRoute = .scan
    @Published var loadingState: LoadingState = .idle
    @Published var channels: [Channel] = []
    @Published var selectedChannel: Channel?
    @Published var selectedGroup = "All"
    @Published var scanHistory: [ScanHistoryItem] = []

    let playerService = PlayerService()
    let scannerService = CameraScannerService()

    private let m3uService = M3UService()
    private let persistence = PersistenceService()

    var groups: [String] {
        ["All"] + Array(Set(channels.map(\.group))).sorted()
    }

    var visibleChannels: [Channel] {
        selectedGroup == "All" ? channels : channels.filter { $0.group == selectedGroup }
    }

    init() {
        scannerService.onCode = { [weak self] raw in
            Task { await self?.handleScannedText(raw) }
        }
    }

    func bootstrap() {
        guard case .idle = loadingState else { return }
        scanHistory = persistence.loadScanHistory()
        if let url = persistence.loadLastURL() {
            Task { await load(url: url, source: "Restoring playlist...") }
        } else {
            route = .scan
        }
    }

    func handleScannedText(_ raw: String) async {
        guard let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              M3UService.isHTTPURL(url) else {
            loadingState = .failed("Scan a valid http or https M3U link.")
            return
        }

        scannerService.stopAndRelease()
        await load(url: url, source: "Loading playlist...")
    }

    func loadClipboard() async {
        guard let raw = UIPasteboard.general.string else {
            loadingState = .failed("Clipboard does not contain a playlist URL.")
            return
        }
        await handleScannedText(raw)
    }

    func load(url: URL, source: String, clearLastOnFailure: Bool = true) async {
        loadingState = .loading(source)
        let previousURL = persistence.loadLastURL()
        do {
            let parsed = try await m3uService.validateAndParse(url: url)
            channels = parsed
            selectedGroup = "All"
            route = .playback(sourceURL: url)
            persistence.saveLastURL(url)
            persistence.upsertScanHistory(url: url, title: historyTitle(for: url))
            scanHistory = persistence.loadScanHistory()
            let initial: Channel
            if previousURL == url {
                let restoredIndex = persistence.loadLastChannelIndex()
                initial = parsed.first(where: { $0.index == restoredIndex }) ?? parsed[0]
            } else {
                initial = parsed[0]
            }
            select(initial)
            loadingState = .idle
        } catch {
            if clearLastOnFailure {
                persistence.clearLastURL()
            }
            channels = []
            selectedChannel = nil
            route = .scan
            loadingState = .failed(error.localizedDescription)
        }
    }

    func select(_ channel: Channel) {
        selectedChannel = channel
        persistence.saveLastChannelIndex(channel.index)
        playerService.load(channel: channel)
    }

    func nextChannel() {
        guard let selectedChannel,
              let idx = channels.firstIndex(of: selectedChannel),
              !channels.isEmpty else { return }
        select(channels[(idx + 1) % channels.count])
    }

    func previousChannel() {
        guard let selectedChannel,
              let idx = channels.firstIndex(of: selectedChannel),
              !channels.isEmpty else { return }
        select(channels[(idx - 1 + channels.count) % channels.count])
    }

    func resetToScan() {
        playerService.stopAndRelease()
        channels = []
        selectedChannel = nil
        selectedGroup = "All"
        route = .scan
        scannerService.start()
    }

    func loadFromHistory(_ item: ScanHistoryItem) async {
        await load(url: item.url, source: "Loading history...", clearLastOnFailure: false)
    }

    func deleteHistoryItem(_ item: ScanHistoryItem) {
        persistence.deleteScanHistory(id: item.id)
        scanHistory = persistence.loadScanHistory()
    }

    func clearHistory() {
        persistence.clearScanHistory()
        scanHistory = []
    }

    private func historyTitle(for url: URL) -> String {
        let rawName = url.deletingPathExtension().lastPathComponent
            .removingPercentEncoding?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !rawName.isEmpty, rawName != "/" {
            return rawName
        }
        return url.host ?? url.absoluteString
    }
}
