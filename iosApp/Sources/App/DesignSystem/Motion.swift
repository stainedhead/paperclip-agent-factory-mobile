import SwiftUI

/// The one place the app decides whether an explicit animation runs: never when Reduce Motion is on.
enum Motion {
    static func animation(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : .default
    }
}
