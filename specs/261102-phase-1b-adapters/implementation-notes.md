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
