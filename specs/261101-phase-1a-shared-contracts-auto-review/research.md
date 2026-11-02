# Research: Phase 1a review fixes

**Date:** 2026-11-01 · **Source PRD:** [phase-1a-shared-contracts-auto-review-PRD.md](./phase-1a-shared-contracts-auto-review-PRD.md)

## Research questions

1. **Binding a probe to an address (F-B-1, F-A-4).** Should `ProbeInstance` return a `ProbeResult.Accepted(baseUri, report)` that `AddInstance` re-checks, or should `AddInstance` call the probe itself? Which is smaller and keeps the holders thin? Check how `FirstRunStateHolder` and `EditInstanceStateHolder` use the result today.
2. **Where to mark "Signed out" on a 401 (F-A-3).** A decorator over the company-scoped gateways, a helper used by every mutation/read use case, or the Phase 1b API adapter? Count the use cases that call gateways and check whether a single choke point exists (`retryingRead`, `MutationResult` helpers).
3. **Cancellation shielding (F-A-2).** `RemoveInstance` already shields local deletion with `NonCancellable`; can `SignOut` share that helper? What does the fake revoke need to expose to test cancellation during the suspended call?
4. **Re-reading vs caller-supplied entities (F-A-8).** Which mutation use cases decide device-auth or eligibility from a caller-supplied entity, and what is the cheapest safe read before acting (one read per mutation)? Does the one-gateway-call rule allow a read first?
5. **Atomic guards on `Dispatchers.Default` (F-B-6).** Do the holders get an injected scope bound to a single-threaded dispatcher, or should guards use `MutableStateFlow.update` / `compareAndSet`? Check how the iOS layer calls `onIntent` (main thread only?).
6. **Server truth for the Decision contract (F-C-1).** Confirm in `server/src/services/decisions.ts` the exact conditions for success vs conflict on a repeated decide and dismiss, and encode them.
7. **Role refresh after 403 (F-D-2).** What is the smallest way for a holder to re-resolve `CompanyContext` after `Forbidden` without a loop?

## Industry standards

OWASP MASVS L2 (credential handling, no secret to unintended hosts); Kotlin coroutines cancellation guidelines.

## Existing implementations

`RemoveInstance` (cancellation shielding), `DeviceAuthGuard` (fixed mutation order), `MutationResult` (one re-read on timeout).

## API documentation

Pinned server 2026.1023.0 source at `~/Code/Test/paperclip/server/src`.

## Best practices

Failing test first; one commit per finding; host-recording fakes for credential tests.

## Open questions

See the PRD's open questions (policy for unreproduced findings; `createGraph` mode seam; empty "Continue to pairing" action).

## References

[phase-1a-shared-contracts-auto-review-PRD.md](./phase-1a-shared-contracts-auto-review-PRD.md); parent research in `../archive/261101-phase-1a-shared-contracts/research.md`.
