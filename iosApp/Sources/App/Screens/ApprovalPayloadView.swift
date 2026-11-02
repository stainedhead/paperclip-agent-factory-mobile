import PaperclipKit
import SwiftUI

/// The payload of an approval as the mock shows it: label and value rows in one grouped card, then any long text
/// below. Which fields exist for which type is the shared state's; this only lays them out.
struct ApprovalPayloadView: View {
    let type: ApprovalTypeUi?
    let payload: ApprovalPayloadUi?

    var body: some View {
        switch payload {
        case let hire as ApprovalPayloadUiHireAgent: hireView(hire)
        case let strategy as ApprovalPayloadUiCeoStrategy:
            blocks([PayloadField(ApprovalLabel.plan.text, strategy.plan, "plan")])
        case let budget as ApprovalPayloadUiBudgetOverride: budgetView(budget)
        case let board as ApprovalPayloadUiBoardRequest: boardView(board)
        case is ApprovalPayloadUiUnsupported:
            Text(ApprovalLabel.unsupported.text)
                .font(Typography.body)
                .cardSurface()
                .accessibilityIdentifier("approval-unsupported")
        default: EmptyView()
        }
    }

    private func hireView(_ hire: ApprovalPayloadUiHireAgent) -> some View {
        let money = hire.monthlyBudget.map { MoneyFormat.text($0) }
        return VStack(alignment: .leading, spacing: Spacing.roomy) {
            rows([
                PayloadField(ApprovalLabel.name.text, hire.name, "name"),
                PayloadField(ApprovalLabel.role.text, ApprovalPresentation.roleName(hire.role), "role"),
                PayloadField(ApprovalLabel.jobTitle.text, hire.title, "title"),
                PayloadField(ApprovalLabel.reportsTo.text, hire.reportsToName ?? hire.reportsToAgentId, "reports-to"),
                PayloadField(ApprovalLabel.adapter.text, hire.adapterType, "adapter"),
                PayloadField(ApprovalLabel.monthlyBudget.text, money, "budget"),
                PayloadField(
                    ApprovalLabel.skills.text, hire.skills.isEmpty ? nil : hire.skills.joined(separator: ", "), "skills"
                )
            ])
            blocks([PayloadField(ApprovalLabel.capabilities.text, hire.capabilities, "capabilities")])
        }
    }

    private func budgetView(_ budget: ApprovalPayloadUiBudgetOverride) -> some View {
        VStack(alignment: .leading, spacing: Spacing.roomy) {
            rows([
                PayloadField(ApprovalLabel.scope.text, budget.scopeName, "scope"),
                PayloadField(ApprovalLabel.window.text, budget.windowKind.map(DecisionPresentation.prettify), "window"),
                PayloadField(ApprovalLabel.limit.text, budget.limit.map { MoneyFormat.text($0) }, "limit"),
                PayloadField(ApprovalLabel.observed.text, budget.observed.map { MoneyFormat.text($0) }, "observed")
            ])
            blocks([PayloadField(ApprovalLabel.guidance.text, budget.guidance, "guidance")])
        }
    }

    private func boardView(_ board: ApprovalPayloadUiBoardRequest) -> some View {
        blocks([
            PayloadField(ApprovalLabel.summary.text, board.summary, "summary"),
            PayloadField(ApprovalLabel.recommended.text, board.recommendedAction, "recommended"),
            PayloadField(ApprovalLabel.nextOnApproval.text, board.nextActionOnApproval, "next"),
            PayloadField(ApprovalLabel.risks.text, bullets(board.risks), "risks"),
            PayloadField(ApprovalLabel.proposedComment.text, board.proposedComment, "proposed-comment")
        ])
    }

    /// Rows of a grouped card; a missing value is left out, never drawn as a blank.
    @ViewBuilder private func rows(_ items: [PayloadField]) -> some View {
        let present = items.filter { $0.value != nil }
        if !present.isEmpty {
            CardGroup {
                ForEach(Array(present.enumerated()), id: \.offset) { index, item in
                    if index > 0 { HairlineDivider() }
                    PayloadRow(title: item.title, value: item.value ?? "", identifier: "approval-row-\(item.key)")
                }
            }
        }
    }

    /// Long text (a plan, a summary) with its heading. Markdown is rendered inline; anything it cannot parse shows
    /// as plain text.
    @ViewBuilder private func blocks(_ items: [PayloadField]) -> some View {
        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
            if let text = item.value, !text.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.medium) {
                    SectionHeader(title: item.title)
                    Text(Self.markdown(text))
                        .font(Typography.body)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .cardSurface()
                        .accessibilityIdentifier("approval-block-\(item.key)")
                }
            }
        }
    }

    private func bullets(_ items: [String]) -> String? {
        items.isEmpty ? nil : items.map { "\u{2022} \($0)" }.joined(separator: "\n")
    }

    private static func markdown(_ text: String) -> AttributedString {
        let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        return (try? AttributedString(markdown: text, options: options)) ?? AttributedString(text)
    }
}

/// One "Role  QA" row: the label on the left, the value on the right (stacked at accessibility text sizes). The row
/// is one element: its label is the title and its value the value.
struct PayloadRow: View {
    let title: String
    let value: String
    let identifier: String

    var body: some View {
        AdaptiveStack(alignment: .firstTextBaseline, spacing: Spacing.extraLarge) {
            Text(title).font(Typography.body).foregroundStyle(Palette.textSecondary.color)
            Spacer(minLength: Spacing.medium)
            Text(value)
                .font(Typography.body.weight(.semibold))
                .multilineTextAlignment(.trailing)
        }
        .frame(minHeight: Spacing.minimumTarget)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(value)
        .accessibilityIdentifier(identifier)
    }
}

/// A titled value of a payload; a `nil` value is left out of the screen.
struct PayloadField {
    let title: String
    let value: String?
    let key: String

    init(_ title: String, _ value: String?, _ key: String) {
        self.title = title
        self.value = value
        self.key = key
    }
}
