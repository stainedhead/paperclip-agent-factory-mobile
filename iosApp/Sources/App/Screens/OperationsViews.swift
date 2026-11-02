import PaperclipKit
import SwiftUI

/// Pieces shared by Factory home, Agents, Agent detail and the Run viewer.

/// The circle with an agent's letters. Decoration only: the name is always written next to it.
struct AgentAvatar: View {
    let name: String
    var body: some View {
        Text(AgentRowPresentation.initials(name))
            .font(Typography.label)
            .lineLimit(1)
            .foregroundStyle(Palette.textPrimary.color)
            .padding(Spacing.large)
            .frame(minWidth: 40, minHeight: 40)
            .background(Palette.muted.color, in: Circle())
            .accessibilityHidden(true)
    }
}

/// The bar under the month spend. The figures next to it say the same thing in words.
struct SpendMeter: View {
    let percent: Int

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.muted.color)
                Capsule().fill(Palette.accent.color).frame(
                    width: proxy.size.width * SpendGauge.fraction(percent: percent))
            }
        }
        .frame(height: 8)
        .accessibilityHidden(true)
    }
}

/// One of the "Running now" rows (Factory home, Agent detail's runs): who and what, how long, a way into the run.
struct RunListRow: View {
    let row: RunRowUi
    let referenceDate: Date?
    let identifier: String
    let onOpen: () -> Void

    var body: some View {
        let presentation = RunRowPresentation(row: row, referenceDate: referenceDate)
        Button(action: onOpen) {
            HStack(spacing: Spacing.extraLarge) {
                VStack(alignment: .leading, spacing: Spacing.hairline) {
                    Text(presentation.title.isEmpty ? L10n.text(row.status) : presentation.title)
                        .font(Typography.body).foregroundStyle(Palette.textPrimary.color)
                        .multilineTextAlignment(.leading)
                    if let issue = row.issueTitle {
                        Text(issue).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                            .multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: Spacing.medium)
                VStack(alignment: .trailing, spacing: Spacing.hairline) {
                    Text(presentation.compact ?? "").font(Typography.body.monospacedDigit())
                    Text(L10n.text(row.status)).font(Typography.secondary)
                        .foregroundStyle(RunRowPresentation.tone(row.status).foreground.color)
                }
                Image(systemName: "chevron.right").font(Typography.micro)
                    .foregroundStyle(Palette.textSecondary.color).accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(
            [presentation.title, L10n.text(row.status), presentation.compact].compactMap { $0 }
                .joined(separator: ", ")
        )
        .accessibilityHint(FactoryLabel.openHint.text)
        .accessibilityIdentifier(identifier)
    }
}

/// Waits for a pull-to-refresh to finish: the intent returns at once, so the indicator stays up for a moment.
@MainActor
func settleRefresh(while busy: () -> Bool) async {
    try? await Task.sleep(for: .milliseconds(400))
    var waited = 0
    while busy(), waited < 50 {
        try? await Task.sleep(for: .milliseconds(100))
        waited += 1
    }
}
