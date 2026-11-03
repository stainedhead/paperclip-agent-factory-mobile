# Data dictionary: Phase 1b shared adapters

**Purpose:** the types, schemas and interfaces this phase adds. Status: draft; filled in as the work lands.

## Schemas and DTOs

| Item | Notes |
|---|---|
| SQLDelight tables | `instance_config`, `app_settings`, `attention_cache` (no secrets) |
| DTOs | one per endpoint response/request in infrastructure/api, never exported |
| Fixtures | JSON bodies under shared/infrastructure/api/src/commonTest/resources (derived from server types; verified in CI) |

## Interfaces

`LiveConfig`/`createLiveGraph(allowInsecureLocal, pairingBrowser)` in umbrella; infrastructure classes implement the existing ports.

## Enumerations

No new domain enumerations expected; `DomainError` mappings only.
