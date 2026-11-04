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

    @Test func serverErrorBreaksTheFactoryAgentsAndRunReadsOnlyWhenAsked() {
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).failsReads == false)
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo", "-demo-server-error"]).failsReads)
    }

    @Test func theDemoRefreshTimerRunsUnlessPaused() {
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).pausesAutoRefresh == false)
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo", "-demo-no-auto-refresh"]).pausesAutoRefresh)
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

struct FirstRunDemoLaunchTests {
    @Test func theFirstRunDemoFlagSelectsTheEmptyScriptedGraphAndNotTheFullDemo() {
        let config = LaunchConfiguration(arguments: ["iosApp", "-demo-first-run"])
        #expect(config.useFirstRunDemo)
        #expect(config.useDemo == false)
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).useFirstRunDemo == false)
    }
}

struct StartTabLaunchTests {
    @Test func theStartTabAndStartRouteFlagsSelectWhereAScreenshotOrTestBegins() {
        let config = LaunchConfiguration(arguments: [
            "iosApp", "-demo", "-demo-tab", "factories", "-demo-open", "settings"
        ])
        #expect(config.startTab == .factories)
        #expect(config.startRoute == "settings")
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo-tab", "nonsense"]).startTab == nil)
        #expect(LaunchConfiguration(arguments: ["iosApp"]).startRoute == nil)
    }
}

struct OfflineLaunchTests {
    @Test func theOfflineFlagAsksForTheDemoPhoneToGoOfflineOnceTheInboxHasLoaded() {
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo", "-demo-offline"]).goesOffline)
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).goesOffline == false)
    }

    @Test func aBuildWithoutDemoControlsIgnoresEveryDemoFlag() {
        let flags = "iosApp -demo -demo-first-run -demo-no-auto-refresh -demo-biometrics approved -demo-diagnostics"
        let more = "-demo-idle immediately -demo-offline -demo-tab factories"
        let arguments = (flags + " " + more).split(separator: " ").map(String.init)
        let config = LaunchConfiguration(arguments: arguments, allowsDemoControls: false)
        #expect(config.useDemo == false)
        #expect(config.useFirstRunDemo == false)
        #expect(config.pausesAutoRefresh == false)
        #expect(config.scriptedBiometrics.isEmpty)
        #expect(config.showsDiagnostics == false)
        #expect(config.demoIdlePeriod == nil)
        #expect(config.goesOffline == false)
        #expect(config.startTab == nil)
    }

    @Test func aDebugBuildHonoursTheDemoFlags() {
        #expect(LaunchConfiguration.buildAllowsDemoControls, "tests and UI tests build Debug")
        #expect(LaunchConfiguration(arguments: ["iosApp", "-demo"]).useDemo)
    }
}
