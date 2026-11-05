# Data dictionary: Phase 2a Android review fixes

**Date:** 2026-11-04

Purpose: the types the fixes add or change.

## Entities and value objects

[TBD] No new entities.

## Interfaces / ports

- `KeystoreBackend.open` and `seal` return `NeedsAuthentication(cipher)` carrying an initialised unused `Cipher`; `authenticate` becomes `suspend (reason, Cipher) -> Cipher?` (R-001).
- `Connectivity` port unchanged (`StateFlow<Boolean>`), DEC-2 (R-012).

## Enumerations

- New `Action.RaiseAgentBudget`, added to `DeviceAuthPolicy` (R-004, DEC-1).

## UI state

- `InboxState.isWorking` (R-007). Shared rule types moved from androidApp (R-019): `RunStatusUi.isLive`, visible agent actions, factory status decisions, tone enums, `DecideByUi.hintText`.

## API request and response types

None.
