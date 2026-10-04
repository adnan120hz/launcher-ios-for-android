import Foundation

/// Checks this repo's GitHub Releases for a newer "camera-v*" release than
/// the running app, exactly like the Android UpdateChecker: asynchronous,
/// cached for 24 hours, and completely silent when offline or on error.
final class UpdateChecker {
    static let shared = UpdateChecker()

    private let lastCheckKey = "update_last_check"
    private let cachedTagKey = "update_cached_tag"
    private let cachedURLKey = "update_cached_url"
    private let cacheLifetime: TimeInterval = 24 * 60 * 60

    private init() {}

    var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.0.0"
    }

    /// Calls back on the main queue with (tag, releasePageURL) only when a
    /// strictly newer camera release exists; nil otherwise.
    func check(completion: @escaping ((tag: String, url: String)?) -> Void) {
        let defaults = UserDefaults.standard
        let last = defaults.double(forKey: lastCheckKey)
        if Date().timeIntervalSince1970 - last < cacheLifetime,
           let tag = defaults.string(forKey: cachedTagKey),
           let url = defaults.string(forKey: cachedURLKey) {
            let hit = isNewer(tag: tag) ? (tag: tag, url: url) : nil
            completion(hit)
            return
        }
        guard let url = URL(string: AppLinks.releasesAPI) else { completion(nil); return }
        var request = URLRequest(url: url)
        request.setValue("KameraIOS/2.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8
        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            guard let self, let data,
                  let releases = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            let cameraReleases = releases.filter { release in
                guard let tag = release["tag_name"] as? String else { return false }
                return tag.hasPrefix("camera-v") && (release["draft"] as? Bool) != true
            }
            let newest = cameraReleases
                .compactMap { release -> (tag: String, url: String)? in
                    guard let tag = release["tag_name"] as? String,
                          let page = release["html_url"] as? String else { return nil }
                    return (tag, page)
                }
                .max { self.compareVersions($0.tag, $1.tag) == .orderedAscending }

            defaults.set(Date().timeIntervalSince1970, forKey: self.lastCheckKey)
            if let newest {
                defaults.set(newest.tag, forKey: self.cachedTagKey)
                defaults.set(newest.url, forKey: self.cachedURLKey)
            }
            let hit = newest.flatMap { self.isNewer(tag: $0.tag) ? $0 : nil }
            DispatchQueue.main.async { completion(hit) }
        }.resume()
    }

    private func isNewer(tag: String) -> Bool {
        compareVersions(tag, currentVersion) == .orderedDescending
    }

    /// Semver-ish compare: strips "camera-v"/"v" prefixes and compares
    /// numeric components ("camera-v1.0.0" vs "2.0.0").
    private func compareVersions(_ a: String, _ b: String) -> ComparisonResult {
        let pa = parse(a), pb = parse(b)
        for i in 0..<max(pa.count, pb.count) {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x > y ? .orderedDescending : .orderedAscending }
        }
        return .orderedSame
    }

    private func parse(_ version: String) -> [Int] {
        var v = version
        if v.hasPrefix("camera-v") { v = String(v.dropFirst("camera-v".count)) }
        if v.hasPrefix("v") { v = String(v.dropFirst()) }
        return v.split(separator: ".").map { Int($0) ?? 0 }
    }
}
