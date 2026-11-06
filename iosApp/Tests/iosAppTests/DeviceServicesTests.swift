import Foundation
import LocalAuthentication
import PaperclipKit
import Testing

/// The device services that need the system daemons a bare Kotlin/Native test binary does not have.
struct DeviceServicesTests {
    private func makeBiometrics() -> any Biometrics {
        PlatformServices.shared.createBiometrics(unlockReason: "Unlock", confirmReason: "Confirm")
    }

    /// Observed on the simulator: the passcode policy can be evaluated (the simulator pretends to have one), so a
    /// real prompt would wait for a person; biometrics alone are not enrolled. The codes below are the ones the
    /// shared mapping handles (LAError.biometryNotEnrolled, passcodeNotSet and biometryNotAvailable are Unavailable).
    @Test func theSimulatorReportsNoEnrolledBiometricsWithTheSystemErrorCodeTheMappingExpects() {
        let context = LAContext()
        var error: NSError?
        let canEvaluate = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
        print("LA-EVIDENCE biometricsOnly can=\(canEvaluate) domain=\(error?.domain ?? "-") code=\(error?.code ?? 0)")
        #expect(canEvaluate == false)
        #expect(error?.domain == LAError.errorDomain)
        let expected = [LAError.biometryNotEnrolled.rawValue, LAError.biometryNotAvailable.rawValue]
        #expect(expected.contains(error?.code ?? 0))
    }

    /// A prompt that nobody answers stays open until the system times it out, so the caller must be able to cancel it.
    /// Cancelling the task invalidates the context and ends the call quickly with an error, not an approval.
    @Test func cancellingAPromptEndsItAndIsNeverAnApproval() async throws {
        let biometrics = makeBiometrics()
        let prompt = Task { try await biometrics.authenticate(reason: .confirmDestructiveAction) }
        try await Task.sleep(for: .milliseconds(500))
        prompt.cancel()
        let finished = await withTaskGroup(of: Bool.self) { group in
            group.addTask {
                _ = await prompt.result
                return true
            }
            group.addTask {
                try? await Task.sleep(for: .seconds(20))
                return false
            }
            let first = await group.next() ?? false
            group.cancelAll()
            return first
        }
        #expect(finished)
        if case .success(let result) = await prompt.result {
            #expect(result != .approved)
        }
    }

    @Test func connectivityReportsOnline() {
        let connectivity = PlatformServices.shared.createConnectivity()
        defer { PlatformServices.shared.closeConnectivity(connectivity: connectivity) }
        #expect(connectivity.isOnline.value == KotlinBoolean(value: true))
    }
}
