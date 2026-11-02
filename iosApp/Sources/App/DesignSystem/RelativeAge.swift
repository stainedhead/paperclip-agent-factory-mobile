import Foundation

/// The mocks' compact ages: "12s", "8m", "1h", "3d".
enum RelativeAge {
    static func text(seconds: Double) -> String {
        let value = Int(max(0, seconds))
        switch value {
        case ..<60: return "\(value)s"
        case ..<3600: return "\(value / 60)m"
        case ..<86400: return "\(value / 3600)h"
        default: return "\(value / 86400)d"
        }
    }
}
