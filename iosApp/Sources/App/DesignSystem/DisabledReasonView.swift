import PaperclipKit
import SwiftUI

/// Why the action buttons next to it are disabled, in words (PRD FR-095).
struct DisabledReasonView: View {
    let reason: ActionsDisabledReason

    var body: some View {
        Label {
            Text(L10n.text(reason))
        } icon: {
            Image(systemName: symbol).accessibilityHidden(true)
        }
        .font(Typography.secondary)
        .foregroundStyle(Palette.textSecondary.color)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("disabled-reason")
    }

    private var symbol: String {
        switch reason {
        case .offline: "wifi.slash"
        case .viewerRole, .ownerRole: "lock.fill"
        }
    }
}

#Preview("Disabled reasons") {
    VStack(alignment: .leading) {
        DisabledReasonView(reason: .offline)
        DisabledReasonView(reason: .viewerRole)
        DisabledReasonView(reason: .ownerRole)
    }
    .padding()
}
