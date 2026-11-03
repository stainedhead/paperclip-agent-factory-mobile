import SwiftUI

extension EnvironmentValues {
    /// "Now" for relative labels. The real app uses the wall clock; previews and tests can pin it.
    @Entry var referenceDate: Date?
}

/// What "now" is for a relative label. A pinned clock (the demo's frozen one, a preview, a test) is used as it is; a
/// live app takes the date of a periodic timeline, so the text keeps moving with no timer of its own in any view.
struct TickingReference<Content: View>: View {
    let every: TimeInterval
    @ViewBuilder let content: (Date?) -> Content
    @Environment(\.referenceDate) private var pinned

    var body: some View {
        if let pinned {
            content(pinned)
        } else {
            TimelineView(.periodic(from: .now, by: every)) { context in
                content(context.date)
            }
        }
    }
}

/// How often a relative label moves, and what "now" it measures against.
enum LiveClock {
    /// Ages move on a coarse tick; a running run's elapsed time moves every second.
    static let ageTick: TimeInterval = 15
    static let elapsedTick: TimeInterval = 1

    static func now(pinned: Date?, tick: Date) -> Date { pinned ?? tick }
}

/// "Updated 12s ago": always shows the server's timestamp as an age, so stale data never looks current, also when the
/// phone is offline or the screen is final and no longer refreshes.
struct UpdatedLabel: View {
    let updatedAt: Date?

    init(epochMillis: Int64?) {
        updatedAt = epochMillis.map { Date(timeIntervalSince1970: Double($0) / 1000) }
    }

    static func text(updatedAt: Date, now: Date, locale: Locale = .current) -> String {
        ShellLabel.commonUpdatedAgo.text(RelativeAge.text(seconds: now.timeIntervalSince(updatedAt), locale: locale))
    }

    var body: some View {
        if let updatedAt {
            TickingReference(every: LiveClock.ageTick) { now in
                Text(Self.text(updatedAt: updatedAt, now: now ?? Date()))
                    .font(Typography.secondary)
                    .foregroundStyle(Palette.textSecondary.color)
                    .accessibilityIdentifier("updated-label")
            }
        }
    }
}

#Preview("Updated") {
    UpdatedLabel(epochMillis: 1_000_000)
        .environment(\.referenceDate, Date(timeIntervalSince1970: 1012))
        .padding()
}
