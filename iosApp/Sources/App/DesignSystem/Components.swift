import SwiftUI

extension View {
    /// The white card of the mocks: surface fill, hairline border, 14 pt radius.
    func cardSurface() -> some View {
        padding(Spacing.card)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
    }
}

extension StatusTone {
    var foreground: Palette.Token {
        switch self {
        case .success: Palette.success
        case .warn: Palette.warn
        case .critical: Palette.destructive
        case .neutral: Palette.textSecondary
        }
    }

    var background: Palette.Token {
        switch self {
        case .success: Palette.successSoft
        case .warn: Palette.warnSoft
        case .critical: Palette.destructiveSoft
        case .neutral: Palette.muted
        }
    }
}

/// A labelled text field with help or an issue underneath, as in the mocks. The text is the caller's state, edited
/// through `text`; the field owns nothing.
struct FormField: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    var help: String?
    var issue: String?
    var keyboard: UIKeyboardType = .default
    var capitalization: TextInputAutocapitalization = .words
    var isDisabled = false
    let identifier: String
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.small) {
            Text(title).font(Typography.button).foregroundStyle(Palette.textPrimary.color)
            TextField(placeholder, text: $text, axis: .vertical)
                .lineLimit(1...4)
                .submitLabel(.done)
                // A field that can wrap is still one line of text: Return ends the entry, it never adds a line.
                .focused($focused)
                .onChange(of: text) { _, new in
                    if new.contains("\n") {
                        text = new.replacingOccurrences(of: "\n", with: "")
                        focused = false
                    }
                }
                .font(Typography.body)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .keyboardType(keyboard)
                .disabled(isDisabled)
                .padding(.horizontal, Spacing.extraLarge)
                .padding(.vertical, Spacing.small)
                .frame(minHeight: Spacing.minimumTarget)
                .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.button))
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.button)
                        .strokeBorder((issue == nil ? Palette.border : Palette.destructive).color)
                )
                .accessibilityIdentifier(identifier)
            if let issue {
                Text(issue)
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.destructive.color)
                    .accessibilityIdentifier("\(identifier)-issue")
            } else if let help {
                Text(help)
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("\(identifier)-help")
            }
        }
    }
}

/// "1 Address  2 Pair  3 Secure": the current step is filled, the others are muted. The step number and name are
/// spoken, not only coloured.
struct StepIndicator: View {
    struct Step: Identifiable {
        let number: Int
        let title: String
        var id: Int { number }
    }

    let steps: [Step]
    let current: Int
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout =
            typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading)) : AnyLayout(HStackLayout())
        layout {
            ForEach(steps) { step in
                HStack(spacing: Spacing.small) {
                    Text("\(step.number)")
                        .font(Typography.label)
                        .frame(width: 24, height: 24)
                        .foregroundStyle((step.number == current ? Palette.onAccent : Palette.textSecondary).color)
                        .background(
                            (step.number == current ? Palette.accent : Palette.muted).color, in: Circle())
                    Text(step.title)
                        .font(Typography.secondary.weight(step.number == current ? .semibold : .regular))
                        .foregroundStyle(
                            (step.number == current ? Palette.textPrimary : Palette.textSecondary).color)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(ShellLabel.stepCurrent.text(step.number, step.title))
                .accessibilityAddTraits(step.number == current ? .isSelected : [])
                .accessibilityIdentifier("step-\(step.number)")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("step-indicator")
        .accessibilityValue("\(current)")
    }
}

/// A small uppercase section title above a grouped card, as in the Settings and Edit mocks.
struct SectionHeader: View {
    let title: String

    var body: some View {
        Text(title.uppercased())
            .font(Typography.label)
            .foregroundStyle(Palette.textSecondary.color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.extraSmall)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A coloured result box ("Server found"): title, detail lines, and a tone that backs the words up.
struct ResultCard<Detail: View>: View {
    let title: String
    let tone: StatusTone
    let identifier: String
    @ViewBuilder var detail: Detail

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.extraSmall) {
            Text(title).font(Typography.button).foregroundStyle(tone.foreground.color)
            detail.font(Typography.body).foregroundStyle(Palette.textPrimary.color)
        }
        .padding(Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.background.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }
}

/// Side by side at normal sizes; stacked at accessibility text sizes so nothing is squeezed to a letter per line.
struct AdaptiveStack<Content: View>: View {
    var alignment: VerticalAlignment = .center
    var spacing: CGFloat = Spacing.extraLarge
    @ViewBuilder var content: Content
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout =
            typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: spacing))
            : AnyLayout(HStackLayout(alignment: alignment, spacing: spacing))
        layout { content }
    }
}

/// A row of a grouped card: leading text (title and optional help), trailing content.
struct CardRow<Trailing: View>: View {
    let title: String
    var help: String?
    @ViewBuilder var trailing: Trailing
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        AdaptiveStack {
            VStack(alignment: .leading, spacing: Spacing.hairline) {
                Text(title).font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
                if let help {
                    Text(help).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                }
            }
            if !typeSize.isAccessibilitySize { Spacer(minLength: Spacing.medium) }
            trailing
        }
        .frame(minHeight: Spacing.minimumTarget)
        .padding(.vertical, Spacing.small)
    }
}

/// Rows separated by hairlines inside one card.
struct CardGroup<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .padding(.horizontal, Spacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.surface.color, in: RoundedRectangle(cornerRadius: Radius.card))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.hairline.color))
    }
}

struct HairlineDivider: View {
    var body: some View {
        Rectangle().fill(Palette.hairline.color).frame(height: 1).accessibilityHidden(true)
    }
}

/// A pill that selects one filter (Inbox kinds, Agents groups). The selected pill is filled and carries the selected
/// trait, so the choice is not colour alone.
struct FilterChip: View {
    let text: String
    let isSelected: Bool
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(Typography.body.weight(isSelected ? .semibold : .regular))
                .padding(.horizontal, Spacing.roomy)
                .frame(minHeight: Spacing.minimumTarget)
                .foregroundStyle((isSelected ? Palette.background : Palette.textPrimary).color)
                .background((isSelected ? Palette.textPrimary : Palette.surface).color, in: Capsule())
                .overlay(Capsule().strokeBorder(Palette.border.color))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier(identifier)
    }
}
