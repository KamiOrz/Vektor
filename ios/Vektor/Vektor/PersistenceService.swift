import Foundation

struct PersistenceService {
    private let defaults: UserDefaults
    private let lastURLKey = "lastValidM3UURL"
    private let lastChannelIndexKey = "lastChannelIndex"
    private let scanHistoryKey = "scanHistoryItems"
    private let historyLimit = 10

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func loadLastURL() -> URL? {
        defaults.string(forKey: lastURLKey).flatMap(URL.init(string:))
    }

    func saveLastURL(_ url: URL) {
        defaults.set(url.absoluteString, forKey: lastURLKey)
    }

    func clearLastURL() {
        defaults.removeObject(forKey: lastURLKey)
    }

    func loadLastChannelIndex() -> Int {
        defaults.integer(forKey: lastChannelIndexKey)
    }

    func saveLastChannelIndex(_ index: Int) {
        defaults.set(index, forKey: lastChannelIndexKey)
    }

    func loadScanHistory() -> [ScanHistoryItem] {
        guard let data = defaults.data(forKey: scanHistoryKey) else { return [] }
        return (try? JSONDecoder().decode([ScanHistoryItem].self, from: data)) ?? []
    }

    func upsertScanHistory(url: URL, title: String) {
        let now = Date()
        let normalized = url.absoluteString
        var items = loadScanHistory()
        let existing = items.first { $0.url.absoluteString == normalized }
        items.removeAll { $0.url.absoluteString == normalized }
        items.insert(
            ScanHistoryItem(
                id: existing?.id ?? normalized,
                url: url,
                title: title,
                lastUsedAt: now,
                createdAt: existing?.createdAt ?? now
            ),
            at: 0
        )
        saveScanHistory(Array(items.prefix(historyLimit)))
    }

    func deleteScanHistory(id: String) {
        let removed = loadScanHistory().first { $0.id == id }
        let remaining = loadScanHistory().filter { $0.id != id }
        saveScanHistory(remaining)
        if removed?.url == loadLastURL() {
            clearLastURL()
        }
    }

    func clearScanHistory() {
        let lastURL = loadLastURL()
        let containsLastURL = lastURL.map { last in
            loadScanHistory().contains { $0.url == last }
        } ?? false
        defaults.removeObject(forKey: scanHistoryKey)
        if containsLastURL {
            clearLastURL()
        }
    }

    private func saveScanHistory(_ items: [ScanHistoryItem]) {
        guard let data = try? JSONEncoder().encode(items) else { return }
        defaults.set(data, forKey: scanHistoryKey)
    }
}
