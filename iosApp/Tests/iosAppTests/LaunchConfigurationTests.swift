import PaperclipKit
import Testing

@testable import iosApp

struct LaunchConfigurationTests {
    @Test func noArgumentsMeansTheLiveSeamAndNothingScripted() {
        let config = LaunchConfiguration(arguments: ["iosApp"])
        #expect(config.useDemo == false)
        #expect(config.scriptedBiometrics.isEmpty)
        #expect(config.demoIdlePeriod == nil)
        #expect(config.showsDiagnostics == false)
    }

    @Test func demoFlagSelectsTheDemoGraph() {
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).useDemo)
    }

    @Test func biometricsScriptIsAnOrderedListOfResults() {
        let config = LaunchConfiguration(arguments: [
            "iosApp", "-demo", "-demo-biometrics", "cancelled,failed,approved"
        ])
        #expect(config.scriptedBiometrics == [.cancelled, .failed, .approved])
    }

    @Test func unknownBiometricWordsAreIgnoredRatherThanGuessed() {
        let config = LaunchConfiguration(arguments: ["iosApp", "-demo-biometrics", "approved,sideways,lockedout"])
        #expect(config.scriptedBiometrics == [.approved, .lockedOut])
    }

    @Test func idlePeriodAndDiagnosticsAreRead() {
        let config = LaunchConfiguration(
            arguments: ["iosApp", "-demo", "-demo-idle", "immediately", "-demo-diagnostics"])
        #expect(config.demoIdlePeriod == .immediately)
        #expect(config.showsDiagnostics)
    }

    @Test func aMissingValueAfterAFlagIsIgnored() {
        let config = LaunchConfiguration(arguments: ["iosApp", "-demo-biometrics"])
        #expect(config.scriptedBiometrics.isEmpty)
    }
}
