import Foundation

/// The mocks' compact ages ("12s", "8m", "1h", "3d") in the person's own language, and the same ages spelled out for
/// VoiceOver ("12 seconds"). Each age is cut down to its largest whole unit, never rounded up.
enum RelativeAge {
    static func text(seconds: Double, locale: Locale = .current) -> String {
        format(seconds, width: .narrow, locale: locale)
    }

    static func spoken(seconds: Double, locale: Locale = .current) -> String {
        format(seconds, width: .wide, locale: locale)
    }

    private static func format(
        _ seconds: Double, width: Duration.UnitsFormatStyle.UnitWidth, locale: Locale
    ) -> String {
        let value = Int(max(0, seconds))
        let (unit, size): (Duration.UnitsFormatStyle.Unit, Int) =
            switch value {
            case ..<60: (.seconds, 1)
            case ..<3600: (.minutes, 60)
            case ..<86400: (.hours, 3600)
            default: (.days, 86400)
            }
        var style = Duration.UnitsFormatStyle(allowedUnits: [unit], width: width)
        style.locale = locale
        return Duration.seconds(value / size * size).formatted(style)
    }
}
