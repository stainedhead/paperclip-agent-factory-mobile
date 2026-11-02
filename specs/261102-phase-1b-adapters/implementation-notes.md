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

---

# 1b-4a Attention, decision, approval, budget gateways (2026-11-02)

## Order and commits

Budget first (smallest; it established the company-scoped stub, `WorkWorld` and the guard pattern), then approvals, decisions, attention. `70652a5` budget, `8ccea04` approvals, `ab05f55` decisions, `9a83e13` attention, `49afae2` audits. Docs: ADR-054 to ADR-059, research answers, technical details.

## Decisions (see the ADRs for the reasons)

- Money from the number's literal, rounded up to cents, USD only (ADR-054). Open parts of replies are read by hand (ADR-055). Budget resolve reads the overview first because the real server resolves twice with 200 (ADR-056). Approvals and decisions are checked against the scope's company and non-UUID ids are NotFound without a request (ADR-057). Port gaps recorded as deviations (ADR-058). One unreadable feed card fails the page (ADR-059).
- `CompanyCalls` is the one place that turns a `CompanyScope` into a request: the instance (address, pins) and key come from the scope's instance only. The three id-addressed gateways and the budget gateway do a GET before every mutation; the attention writes do not (the company is in their path).
- The stub follows the server's permissiveness, not the port's strictness: a second budget resolve answers 200 in the stub, so the adapter's guard is what turns it into `NotFound` (the mutation check below proves the test would notice).
- Fixtures sit in `BudgetFixtures`, `ApprovalFixtures`, `DecisionFixtures`, `FeedFixtures` instead of growing `ServerFixtures`: detekt's 11-function limit applies to test objects too, and each object states the server file and line it follows.
- `StubRequest` percent-decodes path segments, so dismissal keys (`attention:budget:...:2026-11-01T00:00:00.000Z:hard`) arrive as written.

## Deviations from the brief

- `AttentionGateway` has no list-dismissals method, so `GET inbox-dismissals` is not implemented; "poll" is `get` again; `get` fills neither `originAgentName` nor `decideBy`; `assign_issue` has no assignee name (ADR-058). No domain port was changed.
- Not done in this milestone: issues, interactions, agents, runs, dashboard (1b-4b); the live graph and contract test against the pinned container (1b-5).
- The existing shared contract suites were not changed; ids in the real-adapter subclasses are UUIDs (the adapter refuses non-UUID ids, ADR-057), while the suites' own unknown ids (`no-such-approval`, `no-such-decision`) are NotFound through that same rule.
- Attention mappers were first drafted before their tests; I moved the files away, wrote the tests, saw them fail to compile (`Unresolved reference 'AttentionGatewayAdapter'`), and put the files back. That is compile-red only, not behaviour-red for the mappers; the mapper behaviour is covered by tests that passed on first run (so they prove the code, not that they can fail). Mutation checks were done for the budget guard only.

## Red evidence

- Budget: `BudgetGatewayAdapterTest`, `BudgetMapperTest`, `BudgetGatewayContractTest` failed to compile (`Unresolved reference 'BudgetGatewayAdapter'`, `ceilToCents`). All 18 then passed. Mutation: removing the "still active" guard made 2 tests fail (the adapter test and the shared contract's "resolving twice"), then reverted.
- Approvals: `Unresolved reference 'ApprovalGatewayAdapter'`; then green (`ApprovalGatewayAdapterTest` 19, contract 1). Detekt then flagged `TooManyFunctions` (11) and `MayBeConst`; fixed.
- Decisions: `Unresolved reference 'DecisionGatewayAdapter'`; one build error (`Elvis operator always returns the left operand`) and one type error before green; detekt `LongParameterList`, `ComplexCondition`, `MatchingDeclarationName`, line length, then fixed.
- Attention: `Unresolved reference 'AttentionGatewayAdapter'`; after the adapter existed the shared feed contract failed at "counts are computed before paging" because my stub computed the counts over the page instead of the whole feed (a stub defect found by the contract). Fixed in `FeedFixtures.page` and the stub.

## Edge cases

- `DELETE .../inbox-dismissals/{key}` answers 204 with no body; `decodeAs` would call that Malformed, so unit-valued calls use `ack()`, which checks only the status. The triage PUT is the same: its reply row is not read.
- Clearing decide-by must send `{"decideBy":null}`; the shared `ApiJson` has `explicitNulls = false`, so request bodies are built with `buildJsonObject` (a test pins the exact bytes and the stub rejects `{}`).
- The attention stub's cursor is base64url `{v:1,sort,id}` like the server's and is bound to the sort mode; the adapter never inspects it.
- `Money` cannot hold a negative or a non-USD value; both a `RaiseAndResume` in another currency and one over 2,147,483,647 cents are refused before any request.
- A key that is not wiped on failure: `StoredCredentials.call` wipes in `finally`; the hostile-engine audit covers error and trace lines.

---

# 1b-4b Issue, interaction, agent, run, dashboard gateways (2026-11-02)

## Order and commits

TASK 0 first (re-verify 1b-4a against the pinned dist), then the gateways from small to large, one commit each: `c762ed9` exponent literals (fix), `265738a` stub fidelity (non-uuid ids, attention limit, triage snooze, calendar dates, badge), `da28e82` dashboard, `ff96f3f` agents, `fe91181` runs, `149ba34` byte reads with `Range` in the shared client and 416 as a reason, `5ede000` issues, `a95095b` interactions, `9a4714c` audits. Docs: ADR-060 to ADR-066, research ("1b-4a corrections vs pinned release" and "Answers: milestone 1b-4b"), technical details, status.

## Source of truth

The previous milestone read `~/Code/Test/paperclip` (a newer dev tree). Everything here is read from the installed pinned release (`@paperclipai/{server,shared,db}/dist`, compiled JS). The 1b-4a shapes held; the differences are listed in research.md. The incident ids of the 1b-4a tests (`inc-1`, `inc-2`) became uuids because the stub now answers a non-uuid id with the server's 500.

## Decisions (see the ADRs for the reasons)

- ADR-060 pinned dist and a stub that has the server's quirks. ADR-061 ownership: uuid guard, reply `companyId`, read before write, rows per company, an empty list asks the issue. ADR-062 cancel guard and byte log offsets. ADR-063 issue bodies (`order=asc`, `allowDuplicate: true`, explicit null assignee, no list description). ADR-064 adapter-only ranged attachment content. ADR-065 interactions (five answerable kinds, the rest never sent). ADR-066 all-or-nothing tiles and agent money.
- The log chunk (64 KiB) is a constant of the adapter. The contract subclass gives the stub a log of 150,000 bytes so the shared suite needs two reads without making the chunk artificially small.
- `IssueGateway.get` is two reads (issue, subtasks by `parentId`). A failing second read fails the whole call.
- Unreadable payload of a supported interaction kind degrades to `UnsupportedInteraction` instead of failing the thread (unlike a feed card, ADR-059): a thread is read to answer one card, and the web app can still answer it.

## Red evidence (honest)

- Exponent literals: `BudgetMapperTest` failed with `expected:<1> but was:<null>` for `5e-7`; fix; green. The old test that listed `1e3` as refused was changed with it.
- Stub fidelity: all five `PinnedStubFidelityTest` tests failed for the stated reasons (`expected ServerError but was NotFound`, `Success where Failure expected` for limit and snooze, `expected 4 but was 5` for the snooze hiding a card, `expected 0 but was 1` for the badge), then the stubs were fixed.
- Gateways: for each gateway the tests and stubs were written first and failed to compile (`Unresolved reference 'DashboardGatewayAdapter'`, and so on); that is compile-red only. After the adapter existed almost every test passed on the first run, so those tests prove the code but were not individually shown to fail. Two real failures were found by running: a test expectation about the number of log reads (the adapter now computes an offset for the last chunk, so the tail ends with one empty read) and one about `total` of a 200 reply.
- Mutation checks (change the code, see a test fail, revert): dashboard exact text before double (1 failed); agent reply ownership (1) and read-before-write (4); run byte length instead of characters (1) and cancel guard (2); issue `allowDuplicate` removed (3), explicit null assignee removed (1), `order=asc` removed (2); interaction unsupported guard, empty task selection, not-pending Conflict and kind fit (1 each). Not mutation-checked: the mappers' optional fields, the stubs themselves, the dashboard negative counts.

## Edge cases

- `JSON.stringify` prints 5e-7; so exponent literals must be read exactly (fix, ADR-060).
- The server's `cancel` answers 200 with the run unchanged for an ended run; `null` body possible.
- A log read ends without `nextOffset`; the byte length of the content, not `String.length`, is the next offset (the test log has `é`, `ö`, `✓`).
- `GET .../comments` is newest first and unpaged; deleted comments come back with an empty body.
- `POST /issues` quietly returns an existing open issue with the same title (200 `deduplicated`).
- Assigning an agent to an issue with a user assignee is 422 unless the user is nulled in the same patch.
- Interactions of a closed issue are listed as `expired` and answering is 409.
- A list that names no company (`issues/{id}/runs`) or is empty cannot prove ownership: the issue is read first or afterwards.
- `StubReply` gained binary bodies and response headers, `StubRequest` and `RecordedRequest` gained the `Range` header; `StubPaperclipServer` answers a reply with bytes.
- Kotlin/Native test names cannot contain commas or parentheses; two names were rewritten.

## Deviations from the brief

- `attachmentContent` is not on `IssueGateway` (no port method exists for file content; adding one is a domain change). It is a public method of `IssueGatewayAdapter`, unreachable from presentation until a port is added (ADR-064, deferred).
- Issue runs are `RunGateway.forIssue`; nothing was added to `IssueGateway`. The plan document is part of `IssueDetail`.
- "Live runs for the current issue" is `RunGateway.live` (company-wide live runs with agent name and issue id); there is no per-issue live route in the port.
- No domain port, fake or shared contract suite was changed.
- No Konsist rule was added: the nested `AttachmentSlice` keeps the public-surface rule (`InfrastructureApiRulesTest`) true, and the existing rules cover the new files.

## Not covered / not verified

- No fixture was recorded from a running server and the contract test against the pinned container is CI only (1b-5); Docker does not run here.
- Not verified from source (listed in research.md, "Not verified"): the pause/resume replies, a wake of an agent that cannot run, the unknown-agent and unknown-parent answers, the stub's 422 wordings, the status after `verdicts`, `attention=blocked` statuses. They meet the real server first in 1b-5.
- `attachmentContent` has no Darwin or OkHttp byte-level test: the bytes are checked through the Ktor `MockEngine` only.
- Real TLS, pins (still fail closed) and the WebSocket are unchanged and out of scope.

## Tests

JUnit XML of the final run (see the report for the module by target table); module `shared/infrastructure/api`: 299 tests on each of JVM, Android host and iOS simulator (0 failed, 0 skipped); Konsist 55 (all modules); Swift Testing in the app host 44 tests in 8 suites (including the 8 Keychain and 3 device-service tests) plus 1 XCUITest, all passed. New or changed this milestone: Dashboard 11, Agent 18, Run 17, Issue reads 14, writes 15, attachments 9, Interaction 21, binary client 5, stub fidelity 5, audit 5 (was 4), mapper 3 (was 2). Kover from a fresh XML: Core 97.98% (2568 of 2621 lines), Presentation 98.02%; the infrastructure modules are outside the gates.

## 1b-5a live graph (AD-040, AD-041)

### Order and commits

bb6a689 engine closed with the client (red first) · 085ba3c live graph core, `LivePlatform`, first-launch and lifecycle tests · 4355d48 end-to-end flows through state holders · a187291 Konsist rule (reached the remote with one detekt line-length failure, fixed by f3ce846; the commit was made after a failing gate because the gate command was piped into `tail`, whose exit code hid the failure) · 1e6dc7f public `createLiveGraph`, iOS/JVM/Android platforms, iOS and Android tests · 684ab90 `CallbackPairingBrowser` and `PairingBrowsers` · d8fb5ce Swift Testing suite (cross-ownership edit: a new test file under `iosApp/Tests/iosAppTests`, nothing in `iosApp/Sources`).

### Decisions (reasons in ADR-067 to ADR-070)

- Internal `LivePlatform` seam plus `expect fun defaultLivePlatform()`; the public member sits first in `PaperclipCore` to keep the diff away from where the 2i stream appends; `PaperclipGraph` got an optional resource closer and `close()`.
- The live flows are JVM tests only (the stub is copied into the umbrella's `jvmTest` compilation, not into the other targets); iOS gets a simulator test of the wiring and the Swift suite; Android host gets the refusal test.
- `LiveIosGraph` takes an `IosLiveSettings` data class (detekt allows 6 parameters; Swift-facing API avoids default arguments).
- The sqlite3 link flag is needed (research L4).
- `CallbackPairingBrowser` lives in `infrastructure/api` because the Konsist rule restricts `Secret.reveal()` to infrastructure; the first attempt in the umbrella failed that rule and the first attempt to pass the `Secret` to Swift failed to compile (`reveal` is hidden from Objective-C).

### Deviations from the brief

- The brief's `createLiveGraph(...)` "in iosMain": the public two-argument member is in common code (same surface on every platform, platform chosen by `defaultLivePlatform()`); the iOS-only entry for a chosen folder and Keychain service is `LiveIosGraph`.
- The Swift test probes `http://localhost:<port>`, not `127.0.0.1`: the allow-list (security rule) does not include the numeric address and was not widened.
- The Swift test does not drive pairing, inbox or decide (the loopback server answers only `GET /api/health`); those flows are proven on the JVM stub. "Sign out removes the Keychain item" is proven on `FakeSecretStore`, not on a scripted Keychain backend, which is internal to `securestore` (research L7).
- Added beyond the brief: the engine-closing fix in `PaperclipHttpClient`, `CallbackPairingBrowser` / `PairingBrowsers`, `newEngine()` and `isStubUuid` in the stub, and the `lifecyclePorts()` split in `SliceThreeModules.kt` (small edits outside the live files).

### Red evidence (honest)

- Engine close: `PaperclipHttpClientCloseTest.closing the client closes the engine it was built on` failed (the engine's `Job` stayed active), then `engine.close()` made it pass.
- Live graph: the first run of `LiveGraphTest` failed to compile (`Unresolved reference 'LivePlatform'`, `'createLiveGraph'`: compile-red). With `signalPorts()` wired into the live module, `every port the graph resolves is a real adapter...` failed (1 of 3 failed: `FakeLiveEvents` was bound); replacing it made it pass. `AD-041 an empty database yields an empty factory list` and `closing the graph twice...` passed on the first run once the code compiled; the first-launch test was strengthened afterwards because the holders' default state is already "empty" (it now waits for the device name, which only a database answer provides).
- Flows: `LiveFlowsTest` failures during development were all test-side (a holder that had not loaded when an intent was sent, a flow awaited under virtual time, a closed shared engine: which led to `StubPaperclipServer.newEngine()`); production code did not change for them. Probe, journey, relaunch, moved address and insecure-local tests passed once written correctly, so they were not individually shown to fail against a missing feature.
- Konsist: the rule failed on its first run for a real finding (`PlatformServices.kt` imports the testing module), then the file set was narrowed and documented. Mutation: binding `signalPorts()` in `LiveModules.kt` made the rule fail (reverted).
- Mutation checks on the graph: skipping `platform.release()` (2 tests failed), skipping `persistence.close()` (1 failed), removing the idempotence guard (2 failed); each reverted.
- iOS: `LiveIosGraphTest` first failed to compile (`Unresolved reference`, then a comma in a test name); the Swift suite failed to compile three times (protocol conformance of `PairingBrowser`, `Secret.reveal`, an enum case spelling) before passing. These are compile-reds; the Swift tests were not run against a deliberately broken implementation, except that the loopback test would fail if `local_trusted` were not refused (not mutated).
- Linking: removing `-lsqlite3` made the umbrella test link fail (research L4).

### Edge cases

- Closing a graph while screens are live throws in teardown; tests close holders first.
- A shared MockEngine cannot be reused by a second graph once the first closed its client; the stub hands out a new engine per client.
- A Kotlin/Native test name cannot contain a comma.
- `FactoriesStateHolder` loads companies only on `Refresh`; the first-run flow does not trigger it.

### Not covered / not verified

- No real server; no real TLS through Darwin or OkHttp; no pairing or sign-out through the real Keychain via the live graph; no device; Face ID and the passcode prompt (simulator offers no enrolment); the release-mode framework size.
- The live flows (pair, inbox, decide, relaunch) are not repeated on Android host or the iOS simulator; the Swift suite covers probe and creation on the simulator.
- Android has no live platform (ADR-069).

### Tests (from JUnit XML after the final `check lint`)

`shared/umbrella`: JVM 46, Android host 34, iOS simulator 34, Konsist 58 (all modules' rules; was 55), 0 failed, 0 skipped. `shared/infrastructure/api`: 303 on each of JVM, Android host and iOS simulator (299 before). All shared modules together: JVM 1227, Android host 1203, iOS simulator 1238, Konsist 58. Kover from a fresh XML: Core 97.98% (2568 of 2621 lines, unchanged), Presentation 98.02% (4217 of 4302). Swift Testing in the app host: 48 tests in 9 suites (was 44), plus 1 XCUITest; all passed. Framework size: research L9.


## 1b-5b: the contract test against the pinned server (AD-050, AD-051)

Built: `shared/infrastructure/api` JVM `contract` compilation and `contractTest` task (ADR-072); `ServerBootstrap`, `Pairing`, `Seeder`, `RawServer` (JDK HTTP/1.1 client for what the adapters do not do), `MemorySecretStore`, `LiveContext` (the production adapters on OkHttp), `LiveReport` plus a `LauncherSessionListener` that prints the live-versus-MockEngine table; 16 live tests in five classes and 3 offline helper tests (`ContractHelpersTest`, written first and seen red as `Unresolved reference 'InviteToken'`); `scripts/paperclip-test-server.sh invite` (ADR-071); `contract.yml` runs the test after the smoke script in the `pinned` job and in the nightly `latest` job (same steps, report uploaded as an artifact, `PAPERCLIP_CONTRACT_REQUIRED=true`, Android platform installed because AGP configures every module).

### What ran in CI (the only place the container runs)

| Run | Commit | Result | What it taught |
|---|---|---|---|
| 41506991104 | 24a58c7 | failed, "workflow file issue" | A step name containing `: ` is invalid YAML; names are quoted. |
| 41507159863 | 819ed77 | failed, 16 of 16 live tests | Server, smoke and invite steps worked; the JDK client's h2c upgrade made the server drop the connection (HTTP/1.1 only now). |
| 41507946641 | 603f63d | failed | Sign-up `403 INVALID_ORIGIN` (research L-1); compose got `BETTER_AUTH_TRUSTED_ORIGINS` and the auth rate limit off. |
| 41508734276 | 7e875df | failed | Sign-up, invite acceptance, pairing and company seeding passed; then `TestData.uri` refused `http://localhost` (a test-side defect, fixed). |
| 41509506174 | 0bea6e4 | success | First full pass: 46 of 52 operations live. |
| 41510683239 | b4585a7 | success | Observations: resume answered mid-transition, malformed id statuses, bodies. |
| 41511756580 | 1174690 | success | Observed how pause and resume settle (L-4). |
| 41513381521 | 48329d6 | success | Hard assertions for what was observed: pass again, 46 of 52 operations live, 0 MISMATCH, 0 FAIL. |
| 41514074651 | 48329d6 (manual dispatch) | success | Both jobs: pinned, and `latest` (the drift job, 46 of 52 as well). |

Pushes used: 8 for code (24a58c7, 819ed77, 603f63d, 7e875df, 0bea6e4, b4585a7, 1174690, and the 48329d6 group of three commits). Earlier hypotheses that were wrong: that `PAPERCLIP_PUBLIC_URL` makes Better Auth trust the test origin; that a resume reply is the stale row (it is the new, mid-transition row); that issues and agents answer 500 to a malformed id.

### Live-verified versus MockEngine only (from `live-operations.txt` of run 41513381521)

| Verified live (46) | MockEngine only (6) and why |
|---|---|
| Health: probe (authenticated, private, ok; before the bootstrap it is refused as BootstrapPending). Auth: createChallenge, pollChallenge, meWithKey (also its Unauthorized path), me, listCompanies, listBoardKeys, listBoardKeysWithKey, revokeBoardKey, revokeCurrentKey. Attention: feed, dismiss, snooze, restore, setDecideBy. Approval: get, linkedIssues, comments, approve, reject, requestRevision, addComment. Issue: list (open, in review, search), get, comments, addComment (also reopen), setStatus, assign (also clear), create, attachments. Interaction: list. Agent: list, get, pause, resume, invokeHeartbeat, setBudget. Run: get, events, log, cancel, forIssue, forAgent, live. Dashboard: dashboard, windowSpend. | `DecisionGateway.get/decide/dismiss`: a decision can only be created by an agent with a run (`routes/decisions.js:122-130`). `BudgetGateway.incident/resolve`: an incident needs spend above a policy. `InteractionGateway.answer`: interactions are created by agents. These stay covered by the MockEngine tests and the stub (research C7). |

Not covered live even among the verified operations: pagination of the attention feed (cursor) and of the issue list (the data set is tiny), the `blocked` filter, attachment bytes with `Range`, `forIssue` with real runs, a `Skipped` wake, the log with content, a non-empty window spend, and every 409/422 text of the mutations. Decision-based and budget-based attention cards were not in the feed.

### Corrections the live server forced

1. `ResumeAgent` (application layer, outside stream ownership, own test-first commit bebdbf2): reads once after a `resuming` reply (ADR-074). Red: `Expected Idle but was Paused`.
2. `StubIssues`/`StubAgents`: a non-uuid issue id is 404 and an agent id 422 (red: the stub answered 500; `PinnedStubFidelityTest`).
3. `StubAgents` has an opt-in `transitions` mode; `AgentGatewayAdapterTest` shows the adapter maps the mid-transition rows (red: `Unresolved reference 'transitions'`).
4. `docker/paperclip-test/compose.yml`: `BETTER_AUTH_TRUSTED_ORIGINS`, `PAPERCLIP_AUTH_RATE_LIMIT_ENABLED=false`.

### Deviations

- The base URL is `http://localhost:3199`, not `127.0.0.1:3199` as the brief had it: plain http is allowed for `localhost` and `10.0.2.2` only and the allow-list was not widened (`ContractTarget` rewrites a numeric loopback).
- The shared port contract suites are not run against the live server: they assume stub data (fixed ids, a closed issue, a scripted expiry, a shared `AuthPairingContract.letExpire` hook). The live tests are written for seeded data instead; the gap is listed above.
- The bootstrap invite is inserted with the image's `postgres` client, not with the CLI (ADR-071).
- Several steps use `soft`/`observe` in the first runs; after run 41511756580 every observed behaviour became a hard assertion, except lifecycle strings of a settled pause, which are only recorded.
- Local state: the developer's own Paperclip (port 3100, embedded Postgres 54329) is running on this machine, so no throwaway server was started from the npm copy; everything about the server was read from the dist and then observed in CI.

### Tests and gates (from generated artifacts after the final `check lint`)

`shared/infrastructure/api`: 305 on each of JVM, Android host and iOS simulator (303 before; +1 malformed ids, +1 resume mid-transition); `contractTest`: 19 in CI (3 offline, 16 live), skipped (16) without a server. `shared/application`: two new tests (mid-transition resume, failed follow-up read). All shared modules: JVM 1231, Android host 1207, iOS simulator 1242, Konsist 58. Kover from a fresh XML: Core 97.98% (2568 of 2621 lines), Presentation 98.02% (4217 of 4302).

## Review 1b, group F1 (security Must Fix and related Warnings), 2026-11-02

Method: a failing test first for every finding; the red evidence is the assertion message seen before the fix. Where a test could not compile without a new type, the type alone was added first and the red is the assertion that followed. Hashes are the commits on `feat/phase-1b-adapters`.

| Finding | Result | Commit | Red evidence |
|---|---|---|---|
| A-1 pins bypassed on pairing | reproduced | ef4acde | `createChallenge` expected `Failure(CertificateInvalid)` but was `Failure(Malformed)` (the request went out); `HealthGatewayAdapter` needed the repository first (compile) |
| A-2 Denied/Invalidated read as 401 | reproduced | bf1a7ed | `StoredCredentialsTest`: expected `DeviceAuthDeclined` but was `Unauthorized`, expected `KeyInvalidated` but was `Unauthorized`; application: revoke expected `Declined` but was `Unconfirmed`, sign out expected `Declined` but was `SignedOut(Unconfirmed)`, remove expected `NotAuthorized(Cancelled)` but was `Removed(Unconfirmed)`; presentation: `Expected ErrorUi(AuthDeclined) but actual was null`. The existing test `no usable stored secret is Unauthorized` encoded the bug and was changed in the same commit |
| A-3 approvalUrl opened unchecked | reproduced | 11962f2, 613eabc | `http://localhost:3100` instance accepted `http://localhost:9999`; `http://home.example.com` accepted for an https instance; token not percent-encoded. The loop test stops at the first offender, so the userinfo, backslash, javascript:, file: and intent: rows were checked green after the fix, not individually red. 613eabc names a constant after detekt (11962f2 was pushed with that one detekt finding, my chaining error) |
| B-1 wipe on any exception | reproduced | 89f55f5 | 4 unit tests expected `StorageUnavailableException` but a different exception or none; JVM real file: a version 1 file whose migration throws was replaced (`no exception was thrown`). Found on the iOS simulator that sqliter says `Database version 3 newer than config version 2`, so `newer than` is a wipe sign |
| B-2 `recreated` unread, orphan Keychain items | reproduced | 7b687fa | contract: `namespaces must list each instance once`; Keychain scripted: `expected [a, ab] but was []`; `SecretStore.namespaces()` is a new port method with fake, Keychain, unavailable store and contract. The graph test was written with the wiring (compile red only) |
| C-1 protect() null access control | reproduced | 07dde17 | restoring the fall-through gives `expected:<-50> but was:<-25291>` for both protections (the add reached SecItemAdd). Not exercised on a real Keychain: a Swift host test would need the injection exposed through `PlatformServices`, which I did not do |
| B-3 delete-then-add | reproduced | 3e1c344 | `expected Failed but was Stored` (new add failure ignored), `add spare before delete old` not true, and a failed protected replacement read as `Invalidated` (ClassCastException on `SecretReadResult.Invalidated`). The fake backend now reports a duplicate before other failures, as the Keychain does |
| B-4 close order and leaks | reproduced (order, construction leak); race not reproduced | 6d3f2ba | `[resources, container]` instead of `[container, resources]`; `releases expected 1 but was 0`. The claimed race between `persistence.close()` and an in-flight query did not fail in 50 parallel reads, so no change was made to `PaperclipPersistence.close()`; the iOS platform order test only shows no monitor is started, it was a compile red |
| A-4 ack accepts HTML 200 | reproduced | 7c01f47 | `text/html expected Failure(Malformed) but was Success(Unit)` |
| A-5 unknown outcome shown as failure | reproduced | 6c546de | pause with `ConnectionLost` was `Failed`; invoke with `Malformed` was `Failed` not `Unknown`; 502 was `ServerError`; `SocketException` was `Unreachable`; read retried once not four times |
| A-6 Resume stays live | reproduced | a218d52 | `agent.canResume` true after a failed follow-up read and after a follow-up that still shows `resuming` |

### Deviations and limits

- A-5: a 500 or 503 stays `ServerError` (the decision use case test pins "reported once, no reconcile"); only 502 and 504 and transport failures become `ConnectionLost`. 5xx from a proxy other than those is not covered.
- A-1: a factory is found by exact `BaseUri` equality; pins of two factories at one address are merged.
- B-1: wipe decisions use message text because the drivers raise untyped exceptions; a reworded driver message fails safe (storage unavailable, nothing deleted).
- B-2: the notice is per launch and not stored; the SwiftUI banner and handling of `StorageUnavailableException` at launch (B-5, `@Throws`) belong to the iOS stream and are not done.
- Not touched in this group: B-5 to B-12, A-7 to A-12, C-2 to C-13, the `NSFaceIDUsageDescription` (group F2).
- Swift-facing additions the iOS stream will see: `ErrorKey.AuthDeclined`, `ErrorKey.KeyInvalidated`, `ServerKeyOutcome.Declined`, `PaperclipGraph.startupNotice` (`StartupNotice`), `Agent.isResuming/isPausing` (via presentation action lists only).
- `SecretStore.namespaces()` was added to the port; the live `contractTest` `MemorySecretStore` was updated, the contract workflow was not re-run locally (no Docker).

