import Foundation
import Testing

/// The generated Info.plist of the app (project.yml sets the keys; GENERATE_INFOPLIST_FILE builds the plist).
struct InfoPlistTests {
    /// F-B-9: a device with Face ID refuses the biometric prompt of an app that does not say why it asks.
    @Test func theAppExplainsItsFaceIDUse() throws {
        let text = Bundle.main.object(forInfoDictionaryKey: "NSFaceIDUsageDescription") as? String
        let reason = try #require(text)
        #expect(reason.contains("Face ID"))
        #expect(reason.count > 20)
    }
}
