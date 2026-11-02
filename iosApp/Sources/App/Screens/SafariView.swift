import SafariServices
import SwiftUI

/// A web address the Inbox opens (a card of a kind this app does not show). Only http and https reach here.
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
