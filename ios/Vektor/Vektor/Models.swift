import Foundation

struct Channel: Identifiable, Equatable, Hashable {
    let id: String
    let streamUrl: URL
    let title: String
    let logoUrl: URL?
    let group: String
    let index: Int
}

struct ScanHistoryItem: Codable, Identifiable, Equatable, Hashable {
    let id: String
    let url: URL
    let title: String
    let lastUsedAt: Date
    let createdAt: Date
}

enum AppRoute: Equatable {
    case scan
    case playback(sourceURL: URL)
}

enum LoadingState: Equatable {
    case idle
    case loading(String)
    case failed(String)
}

enum VideoShape: Equatable {
    case unknown
    case portrait
    case landscape
    case squareOrNeutral
}

extension Channel {
    var displayNumber: String {
        String(format: "%02d", index)
    }

    var fallbackLogoText: String {
        let words = title.split(separator: " ").prefix(3)
        let initials = words.compactMap { $0.first }.map(String.init).joined()
        return initials.isEmpty ? displayNumber : initials.uppercased()
    }
}
