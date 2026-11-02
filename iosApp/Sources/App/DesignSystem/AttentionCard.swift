import PaperclipKit
import SwiftUI

/// The card pattern of the Inbox and every "needs you" list: severity, kind, age, title, one line of context, then
/// the actions. It renders what it is given; it knows no rules.
struct AttentionCard<Actions: View>: View {
    let severity: SeverityUi?
    let kind: String
    let age: String
    let title: String
    var subtitle: String?
    @ViewBuilder var actions: Actions
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            header
            Text(title)
                .font(Typography.cardTitle)
                .foregroundStyle(Palette.textPrimary.color)
                .accessibilityAddTraits(.isHeader)
            if let subtitle {
                Text(subtitle).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
            }
            actions
                .padding(.top, Spacing.extraSmall)
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("attention-card")
    }

    @ViewBuilder private var header: some View {
        let layout =
            typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading)) : AnyLayout(HStackLayout())
        layout {
            HStack(spacing: Spacing.medium) {
                if let severity { SeverityLabel(severity: severity) }
                Text("\u{00B7} \(kind)")
                    .font(Typography.label)
                    .foregroundStyle(Palette.textSecondary.color)
            }
            if !typeSize.isAccessibilitySize { Spacer(minLength: Spacing.medium) }
            Text(age)
                .font(Typography.label)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityLabel(age)
        }
    }
}

#Preview("Attention cards") {
    ScrollView {
        VStack(spacing: Spacing.large) {
            AttentionCard(
                severity: .critical, kind: "Decision \u{00B7} Plans queue", age: "8m",
                title: "Ship the billing migration to production?",
                subtitle: "CTO proposes 3 options \u{00B7} PAP-1182 \u{00B7} blocks 4 tasks"
            ) {
                Button("Review options") {}.buttonStyle(.app(.primary))
            }
            AttentionCard(
                severity: .high, kind: "Approval \u{00B7} Hire agent", age: "1h", title: "Hire a QA engineer agent",
                subtitle: "Requested by CEO \u{00B7} reports to CTO \u{00B7} $150/mo"
            ) {
                HStack {
                    Button("Reject") {}.buttonStyle(.app(.destructive))
                    Button("Details") {}.buttonStyle(.app(.muted))
                    Button("Approve") {}.buttonStyle(.app(.primary))
                }
            }
        }
        .padding()
    }
    .background(Palette.background.color)
}

#Preview("Attention card, dark, large type") {
    AttentionCard(severity: .critical, kind: "Budget alert", age: "2h", title: "Engineer 2 hit its monthly budget") {
        Button("Raise and resume") {}.buttonStyle(.app(.primary))
    }
    .padding()
    .background(Palette.background.color)
    .preferredColorScheme(.dark)
    .environment(\.dynamicTypeSize, .accessibility3)
}
