import Foundation

enum M3UError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case emptyPlaylist

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            "Only http and https M3U URLs are supported."
        case .invalidResponse:
            "The link did not return a valid M3U playlist."
        case .emptyPlaylist:
            "No playable channels were found in this playlist."
        }
    }
}

struct M3UService {
    var session: URLSession = .shared

    func validateAndParse(url: URL) async throws -> [Channel] {
        guard Self.isHTTPURL(url) else { throw M3UError.invalidURL }

        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw M3UError.invalidResponse
        }
        guard let body = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
            throw M3UError.invalidResponse
        }

        return try parse(body, sourceURL: url)
    }

    func parse(_ body: String, sourceURL: URL? = nil) throws -> [Channel] {
        let lines = body
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .split(separator: "\n", omittingEmptySubsequences: true)
            .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }

        guard lines.contains(where: { $0.hasPrefix("#EXTM3U") || $0.hasPrefix("#EXTINF") }) else {
            throw M3UError.invalidResponse
        }

        var channels: [Channel] = []
        var pendingInfo: ParsedInfo?

        for line in lines {
            if line.hasPrefix("#EXTINF") {
                pendingInfo = Self.parseInfo(line)
                continue
            }

            guard !line.hasPrefix("#"), let streamURL = Self.makeURL(from: line, relativeTo: sourceURL) else {
                continue
            }

            let index = channels.count + 1
            let info = pendingInfo
            let title = info?.title.nonEmpty ?? "Channel \(index)"
            let channel = Channel(
                id: "\(index)-\(streamURL.absoluteString.hashValue)",
                streamUrl: streamURL,
                title: title,
                logoUrl: info?.logo.flatMap { Self.makeURL(from: $0, relativeTo: sourceURL) },
                group: info?.group?.nonEmpty ?? "Ungrouped",
                index: index
            )
            channels.append(channel)
            pendingInfo = nil
        }

        guard !channels.isEmpty else { throw M3UError.emptyPlaylist }
        return channels
    }

    static func isHTTPURL(_ url: URL) -> Bool {
        guard let scheme = url.scheme?.lowercased() else { return false }
        return scheme == "http" || scheme == "https"
    }

    private static func makeURL(from raw: String, relativeTo base: URL?) -> URL? {
        if let url = URL(string: raw), isHTTPURL(url) {
            return url
        }
        if let base, let url = URL(string: raw, relativeTo: base)?.absoluteURL, isHTTPURL(url) {
            return url
        }
        return nil
    }

    private static func parseInfo(_ line: String) -> ParsedInfo {
        let title = line.components(separatedBy: ",").dropFirst().joined(separator: ",").trimmingCharacters(in: .whitespacesAndNewlines)
        return ParsedInfo(
            title: title,
            logo: attribute("tvg-logo", in: line),
            group: attribute("group-title", in: line)
        )
    }

    private static func attribute(_ name: String, in line: String) -> String? {
        let pattern = #"\#(name)="([^"]*)""#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(line.startIndex..<line.endIndex, in: line)
        guard let match = regex.firstMatch(in: line, range: range), match.numberOfRanges > 1 else { return nil }
        return Range(match.range(at: 1), in: line).map { String(line[$0]) }
    }
}

private struct ParsedInfo {
    let title: String
    let logo: String?
    let group: String?
}

private extension String {
    var nonEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
