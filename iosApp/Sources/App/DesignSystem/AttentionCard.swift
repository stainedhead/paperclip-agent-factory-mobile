import PaperclipKit
import SwiftUI

/// The card pattern of the Inbox and every "needs you" list: severity, kind, age, title, one line of context, then
/// the actions. It renders what it is given; it knows no rules.
///
/// With `onOpen` the text block is one button that opens the card: VoiceOver reads it as `combinedLabel` (one
/// sentence), and the action buttons below it stay separate elements, so each can be found and tapped on its own.
struct AttentionCard<Actions: View>: View {
    let severity: SeverityUi?
    let kind: String
    let age: String
    var ageSpoken: String?
    let title: String
    var subtitle: String?
    var identifier = "attention-card"
    var combinedLabel: String?
    var showsChevron = false
    var onOpen: (() -> Void)?
    @ViewBuilder var actions: Actions
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            if let onOpen {
                Button(action: onOpen) {
                    HStack(alignment: .center, spacing: Spacing.medium) {
                        textBlock
                        if showsChevron {
                            Image(systemName: "chevron.right")
                                .font(Typography.secondary)
                                .foregroundStyle(Palette.textSecondary.color)
                                .accessibilityHidden(true)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: Spacing.minimumTarget, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(combinedLabel ?? "\(title). \(kind)")
                .accessibilityIdentifier("\(identifier)-open")
            } else {
                textBlock
            }
            actions
                .padding(.top, Spacing.extraSmall)
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }

    private var textBlock: some View {
        VStack(alignment: .leading, spacing: Spacing.medium) {
            header
            Text(title)
                .font(Typography.cardTitle)
                .foregroundStyle(Palette.textPrimary.color)
                .multilineTextAlignment(.leading)
                .accessibilityAddTraits(onOpen == nil ? .isHeader : [])
                .accessibilityIdentifier("\(identifier)-title")
            if let subtitle {
                Text(subtitle)
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                    .multilineTextAlignment(.leading)
                    .accessibilityIdentifier("\(identifier)-context")
            }
        }
    }

    @ViewBuilder private var header: some View {
        let layout =
            typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading)) : AnyLayout(HStackLayout())
        layout {
            let inner =
                typeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.extraSmall))
                : AnyLayout(HStackLayout(spacing: Spacing.medium))
            inner {
                if let severity { SeverityLabel(severity: severity).fixedSize(horizontal: false, vertical: true) }
                Text("\u{00B7} \(kind)")
                    .font(Typography.label)
                    .foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("\(identifier)-kind")
            }
            if !typeSize.isAccessibilitySize { Spacer(minLength: Spacing.medium) }
            Text(age)
                .font(Typography.label)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityLabel(ageSpoken ?? age)
                .accessibilityIdentifier("\(identifier)-age")
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
