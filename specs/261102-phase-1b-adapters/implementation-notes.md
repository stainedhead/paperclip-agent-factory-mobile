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
