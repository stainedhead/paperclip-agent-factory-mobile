import PaperclipKit
import SwiftUI

/// One option of a decision as a selectable card: the label, then every effect in plain words before anything is
/// chosen. A destructive option is red with a lock glyph (Face ID follows the confirm, inside the shared use case).
struct DecisionOptionCard: View {
    let option: OptionUi
    let isSelected: Bool
    let isDisabled: Bool
    let onSelect: () -> Void

    var body: some View {
        let selected = isSelected
        return Button(action: onSelect) {
            HStack(alignment: .top, spacing: Spacing.extraLarge) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.title3)
                    .foregroundStyle((selected ? Palette.accent : Palette.textSecondary).color)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.small) {
                    HStack(spacing: Spacing.medium) {
                        Text(option.label)
                            .font(Typography.cardTitle)
                            .foregroundStyle((option.isDestructive ? Palette.destructive : Palette.textPrimary).color)
                            .multilineTextAlignment(.leading)
                        if option.needsDeviceAuth {
                            Image(systemName: "lock.fill")
                                .font(Typography.secondary)
                                .foregroundStyle(Palette.destructive.color)
                                .accessibilityHidden(true)
                        }
                    }
                    if let summary = option.summary { effectText(summary) }
                    ForEach(Array(option.effects.enumerated()), id: \.offset) { _, effect in
                        effectText(DecisionPresentation.effectLine(effect))
                    }
                    if option.effects.isEmpty && option.summary == nil { effectText(DecisionLabel.noEffects.text) }
                    if option.effects.contains(where: \.isStrict) { effectText(DecisionLabel.effectStrict.text) }
                }
                Spacer(minLength: 0)
            }
            .padding(Spacing.card)
            .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
            .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card)
                    .strokeBorder(
                        (selected ? Palette.accent : Palette.hairline).color, lineWidth: selected ? 2 : 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: Radius.card))
        }
        .buttonStyle(.cardSelect)
        .accessibilityElement(children: .ignore)
        .disabled(isDisabled)
        .accessibilityLabel(DecisionPresentation.optionLabel(option))
        .accessibilityHint(DecisionLabel.optionHint.text)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier("decision-option-\(option.id)")
    }

    private func effectText(_ text: String) -> some View {
        Text(text)
            .font(Typography.secondary)
            .foregroundStyle(Palette.textSecondary.color)
            .multilineTextAlignment(.leading)
    }
}

/// What each effect did after the decision was confirmed: executed, skipped (with why) or failed.
struct DecisionExecutionSection: View {
    let execution: ExecutionUi

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.large) {
            SectionHeader(title: DecisionLabel.results.text)
            HStack(spacing: Spacing.medium) {
                if execution.isPolling || execution.status == .running { ProgressView() }
                Text(L10n.text(execution.status))
                    .font(Typography.body.weight(.semibold))
                    .accessibilityIdentifier("decision-execution-status")
            }
            ForEach(execution.results, id: \.index) { result in
                OutcomeRow(
                    title: DecisionPresentation.resultTitle(result), status: L10n.text(result.status),
                    detail: DecisionPresentation.reasonText(result.reason), tone: tone(result.status),
                    identifier: "decision-result-\(result.index)")
            }
        }
        .cardSurface()
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("decision-execution")
    }

    private func tone(_ status: EffectStatusUi) -> OutcomeRow.Tone {
        switch status {
        case .executed: .success
        case .skipped: .warn
        case .failed: .critical
        case .claimed, .unknown: .neutral
        }
    }
}
