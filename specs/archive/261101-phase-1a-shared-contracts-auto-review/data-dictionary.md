# Data dictionary: Phase 1a review fixes

**Purpose:** the types added or changed by the fixes. Status: draft; filled in as each fix lands.

## Entities and value objects (new or changed)

| Type | Change |
|---|---|
| `ProbeResult.Accepted` | carries the probed `BaseUri` (F-B-1, F-A-4) |
| `Instance` | updates become field-level, not whole-snapshot write-backs (F-A-8) |

## Interfaces

| Port | Change |
|---|---|
| `AuthGateway` | key validation against an explicit host (`meWithKey(baseUri, token)`) (F-A-1) |
| fakes in `shared/testing` | record the host of every authenticated call (F-C-8) |

## Enumerations and outcome types

| Type | Change |
|---|---|
| `InvokeOutcome` | new `Unknown` for a timeout whose result cannot be proven (F-A-6) |
| `DomainError` | possible split of certificate failure from `Unreachable` (F-A-11) |

## API request and response types

[TBD: none expected.]
