# Architecture: Phase 2a Android UI

**Date:** 2026-11-04 · **Status:** Draft

## Architecture overview

New code lands in `androidApp/` (Compose UI, ViewModels over shared state holders, design system, string resources, Custom Tabs pairing browser) and in three shared modules as Android `actual`s of existing ports. Dependencies stay inward: Composables -> `HolderViewModel` -> umbrella graph and holders. No business rule, HTTP, persistence or secret handling in Compose. Layering diagram and full detail: PRD section "Architecture".

```
androidApp (Compose) -> shared/umbrella (graph) -> presentation -> application -> domain <- infrastructure/*
```

## Component architecture

- App shell: `PaperclipApplication`, `MainActivity`, `AppEnvironment`, `AppGraphProvider`, `RootScreen`, `TabShell`, `AppRoute`.
- State layer: `HolderViewModel<S, I>` per screen instance, keyed by route.
- Screens under `screens/` (one `*Screen.kt` per screen), design system under `designsystem/`, `pairing/`, `shell/`.
- Shared Android services: `KeystoreSecretStore`, `BiometricPromptBiometrics` + `AuthHost`, `ConnectivityManagerConnectivity`, `AndroidLivePlatform`, `AndroidLiveGraph`.
- The iOS-to-Android file map is in the PRD (section "App structure").

## Layer responsibilities

Unchanged: domain owns rules; application owns one action; presentation owns what is enabled and shown; infrastructure implements ports and never leaks DTOs, Ktor, SQLDelight or platform types inward; the app renders state and forwards intents. `BiometricPrompt` is reached only through the `Biometrics` port from a use case, never from a Composable.

## Data flow

User action -> Composable -> `HolderViewModel.onIntent` -> shared state holder -> use case -> port (fake or live) -> state update -> `StateFlow` -> `collectAsStateWithLifecycle()` -> re-render. Lifecycle: `ProcessLifecycleOwner` -> `graph.lifecycle.setForeground`; screens start/stop `graph.refresh` sessions at `STARTED`/`STOPPED`. Free text stays in `rememberSaveable`; only change intents reach the holder.

## Sequence diagrams

[TBD: drawn in the architecture review; sequences to draw: live graph open (Unavailable path), destructive action with `AuthHost` and BiometricPrompt, pairing with Custom Tab, process-death restore.]

## Integration points

Android Keystore, `BiometricPrompt` (`androidx.biometric`), `ConnectivityManager`, Custom Tabs (`androidx.browser`), `ProcessLifecycleOwner`, Navigation Compose, Roborazzi, Macrobenchmark, GitHub Actions emulator job.

## Architectural decisions

Recorded in `docs/architectural-decision-record.md` as they are made (next number after the highest existing, currently ADR-120). Expected: Keystore design and why not `security-crypto`; `AuthHost`; Roborazzi/Robolectric/JUnit runner choice; `withDeviceTest` adoption; Android live-graph entry point; accessibility results.
