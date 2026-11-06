import SafariServices
import SwiftUI

/// Which server-sent links may be opened: https only (principle 1, TLS always). A Debug build also opens plain http
/// to the local development hosts, as the instance addresses themselves may be.
enum WebAddress {
    static var allowsLocalHttp: Bool {
        #if DEBUG
            true
        #else
            false
        #endif
    }

    static func url(_ address: String?) -> URL? {
        guard let address, let url = URL(string: address), let host = url.host, !host.isEmpty else { return nil }
        switch url.scheme?.lowercased() {
        case "https": return url
        case "http": return allowsLocalHttp && ["localhost", "10.0.2.2"].contains(host) ? url : nil
        default: return nil
        }
    }
}

/// A web address the Inbox opens (a card of a kind this app does not show). Only addresses `WebAddress` accepts.
struct WebLink: Identifiable, Equatable {
    let url: URL
    var id: URL { url }
}

/// The web app in the system's in-app browser, so the person stays in the app and can close it with Done.
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
