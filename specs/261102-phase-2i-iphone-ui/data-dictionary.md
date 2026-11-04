# Data dictionary: Phase 2i iPhone UI

**Purpose:** the types, schemas and interfaces this phase adds. Status: draft; filled in as the work lands.

## Swift types

| Type | Notes |
|---|---|
| `AppEnvironment` | demo or live graph, owned by the App scene |
| `*Model` (@Observable @MainActor) | one per state holder; mirrors state, forwards intents, closes the holder from an explicit owner path |
| Design tokens | color, type, spacing, radius from the mocks |
| `Localizable.xcstrings` | every key the state holders emit |

## Interfaces

Swift `PairingBrowser` (ASWebAuthenticationSession) conforming to the Kotlin interface; optional Swift `Biometrics` if the Kotlin LocalAuthentication adapter is not available.
