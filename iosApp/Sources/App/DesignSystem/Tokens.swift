import SwiftUI
import UIKit

/// Design tokens taken from the design mocks (`Paperclip-Remote-mobile-UI-design.html`, inline styles of each page).
/// Light values are the mocks' own; dark values are derived with the same roles and checked for contrast in tests.
enum Palette {
    struct Token {
        let light: UInt32
        let dark: UInt32

        /// A color that follows the system appearance.
        var color: Color {
            Color(
                uiColor: UIColor { traits in
                    UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
                })
        }
    }

    static let background = Token(light: 0xF4F4F1, dark: 0x111316)
    static let surface = Token(light: 0xFFFFFF, dark: 0x1C1F24)
    static let muted = Token(light: 0xF0F0EC, dark: 0x2A2E35)
    static let hairline = Token(light: 0xE3E3DE, dark: 0x2F333B)
    static let border = Token(light: 0xD6D6D0, dark: 0x454A54)
    static let textPrimary = Token(light: 0x15171C, dark: 0xF2F3F5)
    static let textSecondary = Token(light: 0x5B606B, dark: 0xA9AEB9)
    static let accent = Token(light: 0x2649C9, dark: 0x8FA5F7)
    static let onAccent = Token(light: 0xFFFFFF, dark: 0x0E1534)
    static let accentSoft = Token(light: 0xE6EBFA, dark: 0x1F2646)
    static let destructive = Token(light: 0xB3261E, dark: 0xFF9A92)
    static let destructiveSoft = Token(light: 0xFBE9E7, dark: 0x3B1D1B)
    static let warn = Token(light: 0xA84300, dark: 0xFFB37F)
    static let warnSoft = Token(light: 0xFFF1E5, dark: 0x3A2616)
    static let success = Token(light: 0x1F7A4D, dark: 0x6FD39F)
    static let successSoft = Token(light: 0xEAF4EE, dark: 0x14291F)
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1)
    }
}

/// WCAG 2.x contrast ratio between two sRGB colors given as 0xRRGGBB.
enum Contrast {
    static func ratio(_ first: UInt32, _ second: UInt32) -> Double {
        let (lighter, darker) = (max(luminance(first), luminance(second)), min(luminance(first), luminance(second)))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private static func luminance(_ hex: UInt32) -> Double {
        func channel(_ shift: UInt32) -> Double {
            let value = Double((hex >> shift) & 0xFF) / 255
            return value <= 0.03928 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}

enum Spacing {
    static let hairline: CGFloat = 2
    static let extraSmall: CGFloat = 4
    static let small: CGFloat = 6
    static let medium: CGFloat = 8
    static let large: CGFloat = 10
    static let extraLarge: CGFloat = 12
    static let card: CGFloat = 14
    static let roomy: CGFloat = 16
    static let page: CGFloat = 20

    /// The smallest tappable height (Apple HIG).
    static let minimumTarget: CGFloat = 44
}

enum Radius {
    static let dot: CGFloat = 4
    static let small: CGFloat = 6
    static let button: CGFloat = 10
    static let card: CGFloat = 14
    static let chip: CGFloat = 16
}

/// The mocks' type scale, expressed as Dynamic Type text styles so every size grows with the user's setting.
enum Typography {
    static let largeTitle = Font.largeTitle.weight(.bold)  // mock: 32 / 700
    static let title = Font.title3.weight(.semibold)  // 22 / 600
    static let cardTitle = Font.headline  // 16 / 600
    static let body = Font.subheadline  // 14 to 15
    static let button = Font.subheadline.weight(.semibold)  // 14 / 600
    static let secondary = Font.footnote  // 13
    static let label = Font.caption.weight(.semibold)  // 12 / 600, used uppercased
    static let micro = Font.caption2  // 11
}
