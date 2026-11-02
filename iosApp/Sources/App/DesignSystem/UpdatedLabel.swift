import SwiftUI

extension EnvironmentValues {
    /// "Now" for relative labels. The real app uses the wall clock; previews and tests can pin it.
    @Entry var referenceDate: Date?
}

/// "Updated 12s ago": always shows the server's timestamp as an age, so stale data never looks current.
struct UpdatedLabel: View {
    let updatedAt: Date?
    @Environment(\.referenceDate) private var referenceDate

    init(epochMillis: Int64?) {
        updatedAt = epochMillis.map { Date(timeIntervalSince1970: Double($0) / 1000) }
    }

    var body: some View {
        if let updatedAt {
            let age = RelativeAge.text(seconds: (referenceDate ?? Date()).timeIntervalSince(updatedAt))
            Text(ShellLabel.commonUpdatedAgo.text(age))
                .font(Typography.secondary)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityIdentifier("updated-label")
        }
    }
}

#Preview("Updated") {
    UpdatedLabel(epochMillis: 1_000_000)
        .environment(\.referenceDate, Date(timeIntervalSince1970: 1012))
        .padding()
}
