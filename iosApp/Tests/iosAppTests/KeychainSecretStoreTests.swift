import Foundation
import PaperclipKit
import Security
import Testing

/// The Keychain needs an app's entitlements, so these tests run inside the app test host (a bare Kotlin/Native test
/// binary gets errSecMissingEntitlement). Each test uses its own Keychain service so it cannot touch real app items.
struct KeychainSecretStoreTests {
    private let service = "pafm.tests.\(UUID().uuidString)"
    private let token = "pcp_board_TESTONLY_keychain_value"
    private let open = SecretPolicy(requireUserPresence: false)

    private func key(_ instance: String, _ name: SecretName = .boardToken) -> SecretKey {
        SecretKey(instanceId: InstanceId(value: instance), name: name)
    }

    private func makeStore() -> any SecretStore {
        PlatformServices.shared.createKeychainSecretStore(service: service)
    }

    private func attributes(account: String, synchronizable: Any = kSecAttrSynchronizableAny) -> [String: Any]? {
        var query = itemQuery(account: account, synchronizable: synchronizable)
        query[kSecReturnAttributes as String] = true
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? [String: Any]
    }

    private func itemQuery(account: String?, synchronizable: Any) -> [String: Any] {
        var query: [String: Any] = [kSecClass as String: kSecClassGenericPassword]
        query[kSecAttrService as String] = service
        query[kSecAttrAccount as String] = account
        query[kSecAttrSynchronizable as String] = synchronizable
        return query
    }

    /// Every item has an access control; a protected one lists a constraint (`cbio` biometry, `cpo` user presence),
    /// an open one prints just its accessibility (`aku`).
    private func requiresAuthentication(_ item: [String: Any]) -> Bool {
        let control = String(describing: item[kSecAttrAccessControl as String] as Any)
        return control.contains("cbio") || control.contains("cpo")
    }

    private func clean() {
        SecItemDelete(itemQuery(account: nil, synchronizable: kSecAttrSynchronizableAny) as CFDictionary)
    }

    @Test func theSharedSecretStoreContractPassesOnTheRealKeychain() async throws {
        try await PlatformServices.shared.runKeychainContract()
    }

    @Test func aStoredItemIsWhenUnlockedThisDeviceOnlyAndNotSynchronizable() async throws {
        defer { clean() }
        let store = makeStore()
        let written = try await store.put(key: key("a"), value: Secret.companion.of(value: token), policy: open)
        #expect(written == .stored)
        let found = try #require(attributes(account: "instance/a/boardToken"))
        #expect(
            found[kSecAttrAccessible as String] as? String == kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String)
        #expect((found[kSecAttrSynchronizable as String] as? NSNumber)?.boolValue ?? false == false)
        #expect(attributes(account: "instance/a/boardToken", synchronizable: true) == nil)
        #expect(found[kSecAttrService as String] as? String == service)
    }

    @Test func aSecretSurvivesANewStoreInstance() async throws {
        defer { clean() }
        _ = try await makeStore().put(key: key("a"), value: Secret.companion.of(value: token), policy: open)
        let read = try await makeStore().get(key: key("a"), reason: "restart")
        let found = try #require(read as? SecretReadResultFound)
        #expect(found.secret == Secret.companion.of(value: token))
    }

    @Test func deleteNamespaceRemovesOnlyThatInstanceAndNeverAPrefixSibling() async throws {
        defer { clean() }
        let store = makeStore()
        for name in [SecretName.boardToken, .clientCertificatePassword] {
            _ = try await store.put(key: key("a", name), value: Secret.companion.of(value: "x"), policy: open)
        }
        _ = try await store.put(key: key("ab"), value: Secret.companion.of(value: "y"), policy: open)
        try await store.deleteNamespace(instanceId: InstanceId(value: "a"))
        #expect(attributes(account: "instance/a/boardToken") == nil)
        #expect(attributes(account: "instance/a/clientCertificatePassword") == nil)
        #expect(attributes(account: "instance/ab/boardToken") != nil)
    }

    @Test func noAttributeOfAnItemHoldsTheValue() async throws {
        defer { clean() }
        _ = try await makeStore().put(key: key("a"), value: Secret.companion.of(value: token), policy: open)
        let found = try #require(attributes(account: "instance/a/boardToken"))
        let printed = String(describing: found)
        #expect(!printed.contains(token))
    }

    /// On the simulator the biometric access control is accepted even with nothing enrolled (observed). A real device
    /// without biometrics falls back to user presence inside the store; that fallback is covered with a fake backend.
    @Test func aProtectedWriteKeepsAnAccessControlWhenUnlockedThisDeviceOnlyAndLeavesAnOpenMarker() async throws {
        defer { clean() }
        let store = makeStore()
        let policy = SecretPolicy(requireUserPresence: true)
        let written = try await store.put(key: key("a"), value: Secret.companion.of(value: token), policy: policy)
        #expect(written == .stored)
        let item = try #require(attributes(account: "instance/a/boardToken"))
        #expect(requiresAuthentication(item))
        #expect(item[kSecAttrAccessible as String] as? String == kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String)
        #expect((item[kSecAttrSynchronizable as String] as? NSNumber)?.boolValue ?? false == false)
        let marker = try #require(attributes(account: "instance/a/boardToken#protected"))
        #expect(!requiresAuthentication(marker))
    }

    /// A changed biometric set removes the protected item and leaves the marker. That cannot be produced on a
    /// simulator, so the item is removed directly and the store must report Invalidated, not Missing.
    @Test func aProtectedItemThatVanishedIsInvalidatedAndAnUnknownKeyIsMissing() async throws {
        defer { clean() }
        let store = makeStore()
        let policy = SecretPolicy(requireUserPresence: true)
        _ = try await store.put(key: key("a"), value: Secret.companion.of(value: token), policy: policy)
        let query = itemQuery(account: "instance/a/boardToken", synchronizable: false)
        #expect(SecItemDelete(query as CFDictionary) == errSecSuccess)
        let invalidated = try await store.get(key: key("a"), reason: "check")
        #expect(invalidated is SecretReadResultInvalidated)
        let missing = try await store.get(key: key("zz"), reason: "check")
        #expect(missing is SecretReadResultMissing)
        try await store.delete(key: key("a"))
        #expect(attributes(account: "instance/a/boardToken#protected") == nil)
    }

    @Test func writingOpenOverAProtectedSecretReplacesItAndClearsTheMarker() async throws {
        defer { clean() }
        let store = makeStore()
        _ = try await store.put(
            key: key("a"), value: Secret.companion.of(value: "one"), policy: SecretPolicy(requireUserPresence: true))
        let written = try await store.put(key: key("a"), value: Secret.companion.of(value: "two"), policy: open)
        #expect(written == .stored)
        let item = try #require(attributes(account: "instance/a/boardToken"))
        #expect(!requiresAuthentication(item))
        #expect(attributes(account: "instance/a/boardToken#protected") == nil)
        let read = try await store.get(key: key("a"), reason: "check")
        #expect((read as? SecretReadResultFound)?.secret == Secret.companion.of(value: "two"))
    }
}
