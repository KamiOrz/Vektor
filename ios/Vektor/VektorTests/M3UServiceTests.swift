import XCTest
@testable import Vektor

final class M3UServiceTests: XCTestCase {
    func testParserExtractsCoreFields() throws {
        let body = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="https://example.com/logo.png" group-title="News",Global News HD
        https://example.com/live.m3u8
        """

        let channels = try M3UService().parse(body)

        XCTAssertEqual(channels.count, 1)
        XCTAssertEqual(channels[0].title, "Global News HD")
        XCTAssertEqual(channels[0].group, "News")
        XCTAssertEqual(channels[0].logoUrl?.absoluteString, "https://example.com/logo.png")
        XCTAssertEqual(channels[0].streamUrl.absoluteString, "https://example.com/live.m3u8")
    }

    func testParserUsesFallbacks() throws {
        let body = """
        #EXTM3U
        #EXTINF:-1,
        https://example.com/live.m3u8
        """

        let channel = try XCTUnwrap(M3UService().parse(body).first)

        XCTAssertEqual(channel.title, "Channel 1")
        XCTAssertEqual(channel.group, "Ungrouped")
    }

    func testParserRejectsEmptyPlaylist() {
        XCTAssertThrowsError(try M3UService().parse("#EXTM3U\n#EXTINF:-1,Empty"))
    }

    func testURLValidationRejectsNonHTTP() {
        XCTAssertFalse(M3UService.isHTTPURL(URL(string: "ftp://example.com/list.m3u")!))
        XCTAssertTrue(M3UService.isHTTPURL(URL(string: "https://example.com/list.m3u")!))
    }

    func testScanHistoryKeepsTenItemsAndMovesDuplicatesToTop() throws {
        let defaults = try makeDefaults()
        let persistence = PersistenceService(defaults: defaults)

        for index in 0..<11 {
            persistence.upsertScanHistory(url: URL(string: "https://example.com/list-\(index).m3u")!, title: "List \(index)")
        }

        var items = persistence.loadScanHistory()
        XCTAssertEqual(items.count, 10)
        XCTAssertEqual(items.first?.url.absoluteString, "https://example.com/list-10.m3u")
        XCTAssertFalse(items.contains { $0.url.absoluteString == "https://example.com/list-0.m3u" })

        persistence.upsertScanHistory(url: URL(string: "https://example.com/list-5.m3u")!, title: "Updated")
        items = persistence.loadScanHistory()
        XCTAssertEqual(items.count, 10)
        XCTAssertEqual(items.first?.url.absoluteString, "https://example.com/list-5.m3u")
        XCTAssertEqual(items.first?.title, "Updated")
    }

    func testScanHistoryHandlesCorruptPayload() throws {
        let defaults = try makeDefaults()
        defaults.set(Data("not-json".utf8), forKey: "scanHistoryItems")

        XCTAssertEqual(PersistenceService(defaults: defaults).loadScanHistory(), [])
    }

    func testDeletingHistoryClearsMatchingLastURLOnly() throws {
        let defaults = try makeDefaults()
        let persistence = PersistenceService(defaults: defaults)
        let first = URL(string: "https://example.com/first.m3u")!
        let second = URL(string: "https://example.com/second.m3u")!
        persistence.upsertScanHistory(url: first, title: "First")
        persistence.upsertScanHistory(url: second, title: "Second")
        persistence.saveLastURL(first)

        let secondItem = try XCTUnwrap(persistence.loadScanHistory().first { $0.url == second })
        persistence.deleteScanHistory(id: secondItem.id)
        XCTAssertEqual(persistence.loadLastURL(), first)

        let firstItem = try XCTUnwrap(persistence.loadScanHistory().first { $0.url == first })
        persistence.deleteScanHistory(id: firstItem.id)
        XCTAssertNil(persistence.loadLastURL())
    }

    func testClearingHistoryClearsLastURLWhenIncluded() throws {
        let defaults = try makeDefaults()
        let persistence = PersistenceService(defaults: defaults)
        let url = URL(string: "https://example.com/list.m3u")!
        persistence.upsertScanHistory(url: url, title: "List")
        persistence.saveLastURL(url)

        persistence.clearScanHistory()

        XCTAssertEqual(persistence.loadScanHistory(), [])
        XCTAssertNil(persistence.loadLastURL())
    }

    private func makeDefaults() throws -> UserDefaults {
        let suiteName = "VektorTests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
        return defaults
    }
}
