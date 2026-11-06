import Foundation

/// A web address has no spaces, so at large text sizes a single line is clipped. The address is shown with invisible
/// break opportunities after its separators; the characters (and what VoiceOver reads) are unchanged.
enum BreakableAddress {
    static func text(_ address: String) -> String {
        var shown = ""
        for character in address {
            shown.append(character)
            if "/.-?&=".contains(character) { shown.append("\u{200B}") }
        }
        return shown
    }
}
