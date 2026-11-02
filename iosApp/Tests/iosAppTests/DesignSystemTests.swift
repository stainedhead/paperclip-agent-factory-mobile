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
}
