# Implementation notes: Phase 1b shared adapters

**Date:** 2026-11-02

Record decisions, edge cases, deviations and lessons as the work lands, with commit hashes. Update after every task.

## Technical decisions

**1b-1 (2026-11-02).** ADR-038 to ADR-041 hold the decisions; the short list:
- Versions: Ktor 3.6.1, kotlinx-serialization-json 1.12.0, serialization plugin at the Kotlin version (2.4.21). SQLDelight 2.4.1 is the candidate for 1b-2 and is not in the catalog yet. Ktor is built with Kotlin 2.3.21 and works under 2.4.21 on all three test targets.
- One `PaperclipHttpClient` serves every instance (not one per instance): the gateways pass the destination per call because health and pairing run before an instance exists and `meWithKey` must target an explicit host. The per-instance part (address, pins, key) is resolved per call by `StoredCredentials`.
- Retry is not in infrastructure: `retryingRead` is `internal` to `application` and the use cases already wrap their reads. The client sends once; a test proves a failing mutation is sent once.
- `isCurrentDevice` of a stored-key `listBoardKeys` is `key.id == instance.boardKeyId`; for `listBoardKeysWithKey` (a key not yet stored) no key is marked, because the id of the pending key is unknown until `me`.
- `approvalUrl: null` falls back to `{baseUri}/cli-auth/{id}?token={token}` (the server's own `approvalPath`).
- `revokeBoardKey` with a non-UUID id returns `NotFound` without a request; this keeps the shared account contract (`"no-such-key"` expects `NotFound`) honest against a server that answers 400 for such ids. An unknown UUID goes to the stub/server and is `NotFound` from its 404. The shared suite was therefore not changed.
- Ktor Darwin: every non-timeout failure is a `DarwinHttpRequestException(NSError)`; `classifyUrlError` maps NSURLErrorDomain -1200..-1206 and -1022 to `CertificateInvalid`, -1001 to `Timeout`, everything else `Unreachable`. A real Darwin request to a closed loopback port is `Unreachable` (iOS simulator test).
- Pins: stored-pin enforcement is deferred; with pins and no enforcing engine the request fails closed (ADR-040).

## Edge cases and solutions

- `kotlin.test` names on Kotlin/Native cannot contain a comma: iOS test names were reworded.
- Ktor exception messages contain URLs; a mutation test makes `ErrorMapper` return `Rejected(cause.message)` and the secrets audit goes red (mutation check done once by hand, then reverted).
- `ChallengeCreatedDto` has seven fields and tripped detekt `LongParameterList`; `approvalPath` was dropped from the DTO because the fallback URL can be computed.
- DTOs that hold credentials are plain classes (no generated `toString`).

## Deviations from plan

- Fixtures are Kotlin strings in `ServerFixtures`, not files under `src/commonTest/resources` (ADR-041).
- Pin enforcement and a live self-signed TLS test on the simulator are not in 1b-1 (ADR-040, research Q4); both move to 1b-5 with the live graph.
- The security-test commit `d5d5a97` was made before `AuthGatewayAdapter` was committed (`2414a94`), so `d5d5a97` alone does not compile; the branch tip is green.
- A detekt exception for `TooGenericExceptionCaught` was added for `PaperclipHttpClient.kt` only (commit `chore(detekt)`, own commit).

## Red evidence (1b-1)

- ErrorMapper: `ErrorMapperTest` failed to compile with `Unresolved reference 'ErrorMapper'` before the class existed; platform tests were written with their actuals.
- Client: `PaperclipHttpClientTest` failed with `Unresolved reference 'PaperclipHttpClient'` / `'ApiRequest'`; after the first implementation the insecure-http test failed for a test bug (a http `TestData.uri`), fixed in the test.
- Health: `HealthGatewayAdapterTest` failed to compile (`Unresolved reference 'HealthGatewayAdapter'`); the shared `HealthContract` then passed on the adapter.
- Auth: `AuthGatewayAdapterTest` and the three contract subclasses failed to compile (`Unresolved reference 'AuthGatewayAdapter'`); two tests failed at first run (a test that stripped spaces from a JSON value, and a blank `CompanyId` that was not mapped to `Malformed` because the first `map` edit had not applied); both fixed.
- Security: the audit passed on first run; a manual mutation (error carries the exception message) turned `no secret appears in any error trace line...` red, then was reverted.

## Lessons learned

(none yet)

---

# 1b-2 SQLDelight persistence (2026-11-02)

## Group 1: build wiring, schema v1, SqlInstanceRepository (AD-020, AD-021, AD-022)

- SQLDelight 2.4.1 (`app.cash.sqldelight`) works with Kotlin 2.4.21 and the AGP 9.5.0 KMP library plugin with no extra
  settings: plugin alias declared `apply false` at the root, applied in the module after `pafm.kmp-library`. Generated
  code lands in `build/generated/sqldelight`, which Spotless, detekt (`exclude("**/build/**")`) and Android Lint already
  skip, so no exclusion commit was needed (evidence: `check lint` green with generated classes present).
- Test targets run: JVM and the Android host test use the JDBC `sqlite-driver` (the Android host JVM has only stubbed
  framework SQLite classes); the iOS simulator test uses the native driver on a temp file (sqlite3 links with no flags).
- `schemaOutputDirectory` is `src/commonMain/sqldelight/databases`; `1.db` is committed and `verifyCommonMainPaperclipDatabaseMigration`
  is part of `check`.
- Instants are stored as ISO-8601 text (nanoseconds survive), enums by name, pins as space separated digests.
- Unknown enum names read as the safe value (`Unknown`, `ManualBoardKey`, `SignedOut`); an unreadable row is skipped.
- `delete` removes the cache rows of the instance in the same transaction; no foreign keys or cascade are relied on.
- Red evidence: `SqlInstanceRepositoryTest` failed to compile with `Unresolved reference 'SqlInstanceRepository'` before
  the class and queries existed.

## Group 2: SqlAppSettingsStore

- One row (`id = 1` enforced by CHECK). With no row the store emits `AppSettings(DEFAULT_DEVICE_NAME)` at once (a `first()`
  on an empty database would otherwise hang); the shared contract never covers that case, so a persistence test does.
- An unknown idle period name reads as the domain default. `selectedInstanceId` is not cleared when its instance is
  deleted (the domain use cases do not do it either; the reader falls back); noted, not changed.
- Red evidence: `Unresolved reference 'SqlAppSettingsStore'` before the class existed.

## Group 3: schema version 2 and the migration test

- Why a migration exists before any release: the machinery (`.sqm`, `N.db` snapshots, `verifyMigrations`, a runtime
  test on every target) is worth proving while it is cheap. Version 2 adds `attention_cache.format` (the payload layout
  that wrote a row) so a later layout change drops old rows in SQL instead of failing to decode them. Nothing shipped
  at version 1, so the apps will only ever see version 2 on a fresh install; the path is exercised, not needed yet.
- Test: `MigrationTest` builds the version 1 schema from hand-written DDL (never edited), inserts rows, calls
  `Schema.migrate(driver, 1, Schema.version)` and reads the rows through `SqlInstanceRepository` and
  `SqlAppSettingsStore`. It runs on JVM, the Android host test and the iOS simulator (native driver on a temp file).
- `verifyCommonMainPaperclipDatabaseMigration` is wired into `check`; `1.db` and `2.db` are committed snapshots.
- Red evidence: the schema-version and `format` tests failed (`Schema.version` was 1; no `format` column) before `1.sqm`.

## Group 4: SqlAttentionCache

- The page is one JSON row written through persistence-internal payload classes (`payload/`), not through the domain
  types, so the domain stays free of serialization annotations and a domain edit cannot silently reinterpret old rows.
  `RichFeed` (test fixture) carries every `AttentionDetail` variant, `DecideBy.OnDate`, the `Unsupported` source kind,
  approval type, interaction kind and detail, `Dismissal`, `ResolverAudience`, `relatedIssue`, and a nanosecond `Instant`.
- 200-item cap: when a longer page is cut the stored `nextCursor` is cleared (the original would point past the dropped
  items and a reader paging on would skip them); `totalCount` and the company-wide counts are kept so a screen can see
  that the cached page is partial. A page of exactly 200 keeps its cursor.
- A row with a damaged payload or an unknown `format` is a miss and is deleted on read.
- `updatedAt(scope)` is a public method of `SqlAttentionCache` outside the port (the Clock reading of the write).
- Red evidence: `Unresolved reference 'SqlAttentionCache'` before the class existed; detekt then flagged
  `LongParameterList` on the payload classes, which became data classes (the domain does the same).

## Group 5: recovery, serial executor, secrets audit, orphan pruning

- Recovery decision (ADR-042): an unusable database is recreated empty, not failed closed. It holds only non-secret
  configuration and a cache. Recreating orphans the Keychain/Keystore items under `instance/{id}/` because `SecretStore`
  cannot list them; the person adds the factories again and the new pairing writes new keys. `PaperclipPersistence.recreated`
  tells the composition root so it can say so on first screen.
- "Unusable" = open or first statement throws, `user_version` newer than this build, or a table is missing. SQLite opens
  a garbage file without complaint and fails on the first statement; a 0-byte file is a valid empty database.
- detekt `TooGenericExceptionCaught` does not flag `catch (_: Exception)` (its default allows `_`), so no config change.
- Concurrency: the JDBC in-memory driver shares one connection and a parallel transaction failed with `cannot start a
  transaction within a transaction` (red). All three ports now run on ONE `Dispatchers.Default.limitedParallelism(1)` per
  database (`PaperclipPersistence` makes it; tests share `TestDatabase.dispatcher`). Reads and writes of a database are
  therefore ordered, whatever the driver does with its connections.
- A cache write that lands after its factory was deleted (refresh in flight) would leave an orphan row, and the cache
  contract requires writes for factories with no config row, so a guard in `write` is not possible. Instead opening the
  database prunes cache rows with no factory (`deleteOrphans`), tested.
- `update` replaces the whole row including `sort_order` (same as the fake): a caller holding a stale `Instance` undoes a
  reorder. Port semantics, not changed.
- Secrets audit (`SecretsAuditTest`): the exact table/column list is asserted (a new column fails until reviewed), no
  column is named like a secret, and every value of a populated database (RichFeed included) is scanned for
  `pcp_board_`. It passed on first run; its detector is proven by a fourth test that stores `TestData.TOKEN` in a column
  and shows the scan finds it (this is the mutation check, kept as a test).
- Red evidence: `DatabaseRecoveryTest` failed `Unresolved reference 'openRecovering'`; `ConcurrencyTest` failed with the
  SQLite nested-transaction error; the orphan test failed on the leftover row before `deleteOrphans`.

## Group 6: JVM factory (`openJvmPersistence`)

- File or in-memory. JDBC keeps no schema version, so `prepareSchema` maintains SQLite `user_version` (0 = create, lower =
  migrate, higher = rejected by the health check and recreated). Create plus setting the version is not one transaction:
  a crash in between leaves version 0 and tables, the next open fails on "table exists", and recovery recreates a file that
  held no data yet.
- Garbage file, newer-version file and stale `-wal`/`-shm` sidecars are tested on a real file; a 0-byte file is an empty
  database and is not counted as a recreation. A version 1 file on disk is migrated at open.
- Red evidence: `Unresolved reference 'openJvmPersistence'`. The parallel-writers test through the production wiring
  was written after the serial executor and passed at once; it guards the wiring, not a new behaviour.

## Group 7: iOS factory (`openIosPersistence`) and backup exclusion

- Location: `Application Support/PaperclipData/paperclip.db` (`iosDataDirectory()`, asserted by a test). Not `Caches`
  (iOS may purge it) and not `Documents` (visible in Files). The native driver's own default base path could not be
  located from a Kotlin/Native test host (the file written without a `basePath` was in neither the simulator data
  container nor the project), which is one more reason the path is always explicit.
- Backup: `NSURLIsExcludedFromBackupKey` on the folder (covers the `-wal`/`-shm` sidecars SQLite creates later; the key is
  not inherited in `resourceValues`, so each file that exists is also flagged) and file protection class
  `CompleteUntilFirstUserAuthentication`. Failing to set the backup flag throws.
- Verified on the simulator by reading the resource values back: folder and database file are excluded after a normal
  open and after a recreation from a corrupt file, and after later writes and close.
- Surprise recorded: on a database that was just recreated (the first open threw on a garbage file) the file flag set
  immediately after open was gone after the first write in `PaperclipPersistence`'s constructor; applying the flags after
  the constructor fixed it, and the folder flag, which never changes, is the primary guard. The cause was not found.
- Not verifiable: the file protection class (the simulator returns no `NSFileProtectionKey` attribute: a test asserting it
  failed with null and was removed) and real iCloud/iTunes backups.
- Red evidence: `Unresolved reference 'openIosPersistence'`; the recreation test then failed with the flag false until
  the order of protect and construct was changed.

## 1b-2 summary

- Commits: 366fcf5 (schema v1, `SqlInstanceRepository`), 64daef9 (settings), 32134d8 (schema v2 + migration test), b7e7c82
  (attention cache), ed1639b (recovery, serial executor, audit, pruning), 794e0b2 (JVM factory), f3f0350 (iOS factory),
  760d320 (Android factory, Konsist rules), fb9ca07 (docs).
- Tests of the persistence module (JUnit XML): 41 common tests run on every target; the JVM adds 8 JVM-only tests
  (`JvmPersistenceTest`) and iOS adds 5 iOS-only tests (`IosPersistenceTest`). The counts in the final report are from a later run.
- Outside this stream's ownership (minimal, test-first, own commit): `shared/domain` got `AttentionCache.MAX_ITEMS = 200`
  and `shared/testing` got a cap scenario in `AttentionCacheContract`, a "never saved store still emits" scenario in
  `AppSettingsStoreContract`, and the cap in `FakeAttentionCache`, so demo mode pages like production (AGENTS.md: fakes stay
  behaviourally honest).
- A newer-version file on iOS: tried a `RefuseDowngrade` schema wrapper after a first, flawed test run (the throwaway driver
  is lazy and never wrote its version); once the test was right, plain `Schema` already passed, so the wrapper was dropped.
- No separate detekt/Spotless exclusion commit was made: generated code lives under `build/`, already excluded, and
  `check lint` was green with it present.
- Deviations: no notification-defaults column (the domain `AppSettings` has no such field, so it would be dead); a real
  schema v2 exists before any release to exercise migrations (group 3); the Android factory has no test (framework SQLite
  does not run on the host JVM); iOS file protection class is requested but not verifiable on the simulator.
- Deferred: wiring `PaperclipPersistence` into `createLiveGraph` (1b-5); deleting the factory's Keychain items on removal is
  the use case's job through `SecretStore` (1b-3); an Android emulator test.

## 1b-3 device services (AD-030 to AD-033)

### Group 1: research spike and fail-closed/system services

- Spike first (research Q5a): the Keychain cannot be used from a bare Kotlin/Native test binary (-25291 standalone,
  -34018 on a booted simulator, re-signed binary refused by `simctl spawn`); in the app test host it fails with -34018
  when built unsigned and works ad-hoc signed. The spike test and a temporary `KotlinNativeSimulatorTest` block in the
  module's build script were deleted before the first commit (CI has no such simulator).
- Red evidence: `SystemServicesTest` failed `Unresolved reference 'SystemClock'` (and the other five classes);
  `UnavailableSecretStoreTest` failed `Unresolved reference 'UnavailableSecretStore'`; `KeychainSecretStoreTest` failed
  `Unresolved reference 'KeychainBackend'/'Protection'`. Green after the minimum code, on JVM, Android host and iOS.
- `UnavailableSecretStore.get` returns `Missing` (nothing was ever stored and never will be); a test shows a second
  instance finds nothing either, so nothing is retained anywhere. It deliberately fails `SecretStoreContract`.
- Kotlin/Native test names cannot contain commas (compile error), JVM-style rules apply.
- detekt: the store first had 13 functions; the write side (protection choice, replace on duplicate, marker) moved to
  `KeychainWriter`.

### Group 2: Keychain store (decisions in Kotlin, real Keychain in the app host)

- Layout and mapping: ADR-051. The `#protected` marker is how `Invalidated` is told from `Missing`; it was added after
  reading `CheckCredentials`, which maps both to state changes, so a transient failure on read is `Denied` and nothing else.
- Red evidence: the scripted-backend tests failed to compile (`KeychainBackend`, `Protection` unresolved) before the store
  existed; the Swift tests failed `cannot find 'PlatformServices' in scope` before the umbrella factory existed, then
  passed on first run of the real backend. The attribute checks (`kSecAttrAccessible` = `ak..u` this-device-only,
  `kSecAttrSynchronizable` = 0, no match when querying for a synchronizable copy), restart (second store instance),
  namespace prefix (`a` vs `ab`) and the "no attribute holds the value" audit all run against the real Keychain.
- Learned by failing: open items also carry an `accc` (`<SecAccessControlRef: aku>`), so "protected" is detected by a
  constraint (`cbio`/`cpo`) in its description, not by the presence of `accc`. Two Swift tests failed on that and were
  corrected.
- On the simulator `.biometryCurrentSet` is accepted with nothing enrolled. The `.userPresence` fallback and the
  "neither works, so Failed and nothing stored" branch are tested with the scripted backend only.
- Cross-ownership edits (own commits, minimal): `scripts/ios-test.sh` now signs ad hoc (e23904d); two test-only Swift
  files in `iosApp/Tests/iosAppTests` (`KeychainSecretStoreTests.swift`, `DeviceServicesTests.swift`). The umbrella gained
  `iosMain/PlatformServices.kt` and an `iosMain` dependency on `securestore` and `platform`. Stream 2i runs `ios-test.sh` too.
- Deferred to iosApp: `NSFaceIDUsageDescription` in the app's Info.plist (a device refuses Face ID without it).

### Group 3: LocalAuthentication and NWPathMonitor

- Red evidence: `Unresolved reference 'DeviceAuthenticator'/'LocalAuthenticationBiometrics'/'PathSource'`. A first live
  test then failed with `Collection should contain element 4099L`: a bare test binary has no LocalAuthentication daemon, so
  the error is an XPC error outside `LAErrorDomain`. `AuthOutcome.ServiceUnreachable` (Unavailable) was added and tested
  (red then green). The Swift host test showed that on the simulator the passcode policy *can* be evaluated (a real
  prompt waits 320 s), so a live "no passcode" path does not exist there; the live checks are the biometrics-only policy
  (-7) and prompt cancellation. A first version of that Swift test hung for 320 s and was replaced.
- `ReasonTexts` is public so the app can pass localized sentences; the umbrella takes two strings.
- `NwConnectivity` is online until the first path (optimistic), counts updates for the test, ignores updates after close,
  closes idempotently.

### Group 4: Konsist and docs

- `InfrastructureDeviceRulesTest` (6 tests): imports allowed, no database/file/user-defaults API in `securestore`, no
  `Secret` import in `platform`, no print/log, exact public surface of both modules, and a detector self-test. They passed
  on first run (the code was already conforming); the detector test is the proof they can fail.
- Existing rule `Secret.reveal` only under `shared/infrastructure/` already covers the store.

### 1b-3 summary

- Commits: d692179 (platform services, fail-closed store, Keychain store logic), e23904d (ios-test.sh ad-hoc signing),
  cbc492f (Security.framework backend, `PlatformServices`), 48e784e (Swift Keychain tests), 26e5230 (biometrics,
  connectivity, umbrella factories), c8a15a9 (Konsist), 8223abf (Swift device tests); docs commit follows.
- Tests (JUnit XML of this run): securestore jvm 3, android host 3, iOS simulator 22; platform jvm 6, android host 6,
  iOS simulator 17; Konsist 55 (all modules); Swift app host: 8 Keychain tests + 3 device-service tests.
- Not verifiable here: real Face ID/Touch ID, lockout, passcode-not-set on a device, real-device Keychain behaviour
  (locked device `errSecInteractionNotAllowed`, `.biometryCurrentSet` invalidation after an enrollment change, StrongBox),
  iCloud Keychain sync (only the `sync = 0` attribute and the empty synchronizable query are checked), and ad-hoc signing on
  the CI runner until the PR workflow runs.
