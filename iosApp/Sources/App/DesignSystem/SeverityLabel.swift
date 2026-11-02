import PaperclipKit
import SwiftUI

/// "CRITICAL", "HIGH"...: the word carries the meaning, the color only reinforces it.
struct SeverityLabel: View {
    let severity: SeverityUi

    var body: some View {
        Text(text.uppercased())
            .font(Typography.label)
            .foregroundStyle(color.color)
            .accessibilityLabel(text)
            .accessibilityIdentifier("severity-label")
    }

    private var text: String {
        switch severity {
        case .critical: ShellLabel.severityCritical.text
        case .high: ShellLabel.severityHigh.text
        case .medium: ShellLabel.severityMedium.text
        case .low: ShellLabel.severityLow.text
        }
    }

    private var color: Palette.Token {
        switch severity {
        case .critical: Palette.destructive
        case .high: Palette.warn
        case .medium, .low: Palette.textSecondary
        }
    }
}

#Preview("Severity") {
    VStack(alignment: .leading) {
        SeverityLabel(severity: .critical)
        SeverityLabel(severity: .high)
        SeverityLabel(severity: .medium)
        SeverityLabel(severity: .low)
    }
    .padding()
}
