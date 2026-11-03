import Foundation
import PaperclipKit
import Testing

@testable import iosApp

/// UI-005: the tokens come from the design mocks and keep text readable in light and dark.
struct DesignTokenTests {
    @Test func theMocksBrandValuesAreTheLightTokens() {
        #expect(Palette.background.light == 0xF4F4F1)
        #expect(Palette.surface.light == 0xFFFFFF)
        #expect(Palette.textPrimary.light == 0x15171C)
        #expect(Palette.textSecondary.light == 0x5B606B)
        #expect(Palette.accent.light == 0x2649C9)
        #expect(Palette.destructive.light == 0xB3261E)
        #expect(Palette.warn.light == 0xA84300)
        #expect(Palette.success.light == 0x1F7A4D)
        #expect(Palette.hairline.light == 0xE3E3DE)
    }

    struct Pair: Sendable, CustomTestStringConvertible {
        let name: String
        let foreground: Palette.Token
        let background: Palette.Token
        var testDescription: String { name }

        init(_ name: String, _ foreground: Palette.Token, _ background: Palette.Token) {
            self.name = name
            self.foreground = foreground
            self.background = background
        }
    }

    private static let readablePairs: [Pair] = [
        Pair("primary on background", Palette.textPrimary, Palette.background),
        Pair("primary on surface", Palette.textPrimary, Palette.surface),
        Pair("secondary on background", Palette.textSecondary, Palette.background),
        Pair("secondary on surface", Palette.textSecondary, Palette.surface),
        Pair("accent on surface", Palette.accent, Palette.surface),
        Pair("onAccent on accent", Palette.onAccent, Palette.accent),
        Pair("destructive on surface", Palette.destructive, Palette.surface),
        Pair("destructive on destructiveSoft", Palette.destructive, Palette.destructiveSoft),
        Pair("warn on surface", Palette.warn, Palette.surface),
        Pair("warn on warnSoft", Palette.warn, Palette.warnSoft),
        Pair("success on surface", Palette.success, Palette.surface),
        Pair("success on successSoft", Palette.success, Palette.successSoft),
        Pair("primary on muted", Palette.textPrimary, Palette.muted)
    ]

    @Test(arguments: readablePairs)
    func textPairsMeetWCAGAAInLightAndDark(pair: Pair) {
        #expect(Contrast.ratio(pair.foreground.light, pair.background.light) >= 4.5, "light: \(pair.name)")
        #expect(Contrast.ratio(pair.foreground.dark, pair.background.dark) >= 4.5, "dark: \(pair.name)")
    }

    @Test func contrastMatchesKnownReferenceValues() {
        #expect(abs(Contrast.ratio(0x000000, 0xFFFFFF) - 21) < 0.01)
        #expect(abs(Contrast.ratio(0xFFFFFF, 0xFFFFFF) - 1) < 0.01)
    }

    @Test func spacingAndRadiusScalesComeFromTheMocks() {
        #expect(Spacing.page == 20)
        #expect(Spacing.card == 14)
        #expect(Radius.card == 14)
        #expect(Radius.button == 10)
        #expect(Radius.chip == 16)
        #expect(Spacing.minimumTarget == 44)
    }
}

struct RelativeAgeTests {
    private static let ages: [(Double, String)] = [
        (12, "12s"), (59, "59s"), (60, "1m"), (480, "8m"), (3540, "59m"),
        (3600, "1h"), (82800, "23h"), (86400, "1d"), (259_200, "3d")
    ]

    @Test(arguments: ages)
    func abbreviatesLikeTheMocks(seconds: Double, text: String) {
        #expect(RelativeAge.text(seconds: seconds) == text)
    }

    @Test func aFutureTimestampReadsAsJustNowNotNegative() {
        #expect(RelativeAge.text(seconds: -5) == "0s")
    }

    @Test func unitsFollowTheLocaleNotJustEnglish() {
        let german = Locale(identifier: "de_DE")
        #expect(RelativeAge.text(seconds: 480, locale: german) == "8min")
        #expect(RelativeAge.text(seconds: 259_200, locale: Locale(identifier: "fr_FR")) == "3j")
    }

    @Test func voiceOverHearsWordsNotLetters() {
        let english = Locale(identifier: "en_US")
        #expect(RelativeAge.spoken(seconds: 12, locale: english) == "12 seconds")
        #expect(RelativeAge.spoken(seconds: 480, locale: english) == "8 minutes")
        #expect(RelativeAge.spoken(seconds: 3600, locale: english) == "1 hour")
        #expect(RelativeAge.spoken(seconds: 259_200, locale: english) == "3 days")
        #expect(RelativeAge.spoken(seconds: 65, locale: Locale(identifier: "de_DE")) == "1 Minute")
    }
}

/// A1/B-01: a label is a function of the clock, so it cannot stay frozen at the moment of its last render.
@MainActor
struct ClockDrivenLabelTests {
    private let english = Locale(identifier: "en_US")
    private let updated = Date(timeIntervalSince1970: 1000)

    @Test func theSameUpdateTimeReadsOlderAsTheClockMovesOn() {
        let first = UpdatedLabel.text(updatedAt: updated, now: Date(timeIntervalSince1970: 1012), locale: english)
        let later = UpdatedLabel.text(updatedAt: updated, now: Date(timeIntervalSince1970: 1000 + 600), locale: english)
        let muchLater = UpdatedLabel.text(
            updatedAt: updated, now: Date(timeIntervalSince1970: 1000 + 7200), locale: english)
        #expect(first == "Updated 12s ago")
        #expect(later == "Updated 10m ago")
        #expect(muchLater == "Updated 2h ago")
    }

    @Test func aPinnedDemoClockWinsOverTheTickAndALiveOneFollowsIt() {
        let tick = Date(timeIntervalSince1970: 5000)
        #expect(LiveClock.now(pinned: nil, tick: tick) == tick)
        #expect(
            LiveClock.now(pinned: Date(timeIntervalSince1970: 1012), tick: tick)
                == Date(timeIntervalSince1970: 1012))
    }

    @Test func aRunsElapsedTimeKeepsCountingWhileItIsRunningAndStopsWhenItEnds() {
        let started = Date(timeIntervalSince1970: 1000)
        let running = { (now: Double) in
            RunRowPresentation.elapsedText(
                startedAt: started, finishedAt: nil, now: Date(timeIntervalSince1970: now))
        }
        #expect(running(1045) == "45s")
        #expect(running(1252) == "4m 12s")
        let finished = Date(timeIntervalSince1970: 1100)
        #expect(
            RunRowPresentation.elapsedText(
                startedAt: started, finishedAt: finished, now: Date(timeIntervalSince1970: 9999))
                == "1m 40s")
    }

    @Test func aCardsSpokenAgeUsesWordsAtTheClocksNow() {
        let card = InboxCardPresentation(
            kind: .decision, rawKind: nil, title: "Ship?", identifier: "PAP-1", severity: .high,
            whyNow: "Needs you", origin: nil, activityAtEpochMillis: 1_000_000,
            referenceDate: Date(timeIntervalSince1970: 1480), locale: english)
        #expect(card.age == "8m")
        #expect(card.accessibilityLabel.contains("8 minutes ago"))
        #expect(!card.accessibilityLabel.contains("8m ago"))
    }
}

/// B-05: a button that says Retry retries; a banner that can only be closed says Dismiss.
struct ErrorBannerButtonTests {
    @Test func aBannerThatCanOnlyBeClosedOffersDismissNeverRetry() {
        #expect(ErrorBannerButton.choose(signedOut: false, signIn: false, retry: false, dismiss: true) == .dismiss)
        #expect(ErrorBannerButton.dismiss.title == "Dismiss")
    }

    @Test func retryWinsOverDismissAndSignInWinsWhenSignedOut() {
        #expect(ErrorBannerButton.choose(signedOut: false, signIn: true, retry: true, dismiss: true) == .retry)
        #expect(ErrorBannerButton.choose(signedOut: true, signIn: true, retry: true, dismiss: true) == .signIn)
        #expect(ErrorBannerButton.choose(signedOut: true, signIn: false, retry: true, dismiss: false) == .retry)
    }

    @Test func nothingToDoOffersNoButton() {
        #expect(ErrorBannerButton.choose(signedOut: false, signIn: false, retry: false, dismiss: false) == nil)
    }
}

/// C8: Reduce Motion switches the two explicit animations off.
struct MotionTests {
    @Test func reduceMotionTurnsAnimationOffAndOtherwiseItIsOn() {
        #expect(Motion.animation(reduceMotion: true) == nil)
        #expect(Motion.animation(reduceMotion: false) != nil)
    }
}
