# Architecture: Phase 1b shared adapters

**Date:** 2026-11-02 · **Status:** Draft

## Architecture overview

New code lands in shared/infrastructure/{api,persistence,securestore,platform,realtime} and the umbrella wiring (`createLiveGraph`). Domain, application and presentation do not change except for defects found by contract tests (each with a regression test). Platform-specific code (Keychain, LocalAuthentication, NWPathMonitor) lives in iosMain actuals; Android and JVM actuals fail closed. The Swift side supplies only a `PairingBrowser`.

## Component architecture

- infrastructure/api: `PaperclipHttpClient`, `ErrorMapper`, one `*GatewayAdapter` per port, DTOs, mappers.
- infrastructure/persistence: schema, migrations, `Sql*` repositories, drivers per platform.
- infrastructure/securestore: `KeychainSecretStore` (iosMain), fail-closed actuals.
- infrastructure/platform: `LocalAuthenticationBiometrics`, `NwConnectivity`, system clock/time zone/id, lifecycle bridge.
- umbrella: live wiring.

## Layer responsibilities

Unchanged: domain owns rules; application owns one action; presentation owns what is enabled and shown; infrastructure implements ports and never leaks DTOs, Ktor, SQLDelight or platform types inward; apps render state and forward intents.

## Data flow

Use case -> decorated gateway port -> adapter -> Ktor client (bearer from SecretStore for the request only) -> server; response -> DTO -> mapper -> domain type; errors -> ErrorMapper -> DomainError.

## Sequence diagrams

[TBD: drawn in the architecture phase.]

## Integration points

Paperclip REST API (pinned 2026.1023.0); iOS Keychain, LocalAuthentication, Network.framework; ASWebAuthenticationSession through the Swift-supplied PairingBrowser.

## Architectural decisions

Recorded in `docs/architectural-decision-record.md` as they are made (next number after the highest existing).
