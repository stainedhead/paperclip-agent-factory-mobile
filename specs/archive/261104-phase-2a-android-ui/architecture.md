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

## Module table additions

| Module | Type | Purpose |
|---|---|---|
| `androidApp` | `com.android.application` | Compose UI |
| `androidApp/benchmark` (`:androidApp:benchmark`) | `com.android.test` | Macrobenchmark startup and frame-timing tests, baseline profile generator (T8.6); depends on the release-like `benchmark` build type of `androidApp` |
| `shared/infrastructure/securestore`, `platform`, `persistence`, `umbrella` | existing KMP libraries | gain `androidDeviceTest` via `withDeviceTest {}` (T0.7) |

## Test runner layout

`androidApp/src/test`: JUnit 4 + Robolectric for Compose and Roborazzi tests; JUnit 5 for pure tests via the Vintage engine, or JUnit 4 throughout if unavailable (T0.8). `androidApp/src/androidTest`: JUnit 4, instrumented. Shared modules: kotlin.test / JUnit 5 unchanged.

## Layer responsibilities

Unchanged: domain owns rules; application owns one action; presentation owns what is enabled and shown; infrastructure implements ports and never leaks DTOs, Ktor, SQLDelight or platform types inward; the app renders state and forwards intents. `BiometricPrompt` is reached only through the `Biometrics` port from a use case, never from a Composable.

## Data flow

User action -> Composable -> `HolderViewModel.onIntent` -> shared state holder -> use case -> port (fake or live) -> state update -> `StateFlow` -> `collectAsStateWithLifecycle()` -> re-render. Lifecycle: `ProcessLifecycleOwner` -> `graph.lifecycle.setForeground`; screens start/stop `graph.refresh` sessions at `STARTED`/`STOPPED`. Free text stays in `rememberSaveable`; only change intents reach the holder.

## Sequence diagrams

Textual sequences (diagrams may be added in the architecture phase):

1. Live graph open: `PaperclipApplication` -> `AppGraphProvider.Live` -> `AndroidLiveGraph.openLiveGraph(context, ...)` -> `Opened(graph)` or `Unavailable(cause)`; `Unavailable` -> `StartupNotice.LiveGraphUnavailable` + Try again; nothing left open.
2. Destructive action: Composable -> `HolderViewModel.onIntent(confirm)` -> state holder -> use case -> `Biometrics.authenticate(ConfirmDestructiveAction)` -> `BiometricPromptBiometrics` -> `AuthHost.current()` -> `BiometricPrompt`; only `Approved` continues to the gateway mutation.
3. Pairing: First-run/Edit holder -> `CallbackPairingBrowser` -> `CustomTabsPairingBrowser` opens the Custom Tab; activity resume or cancel -> `finished()`; the shared poller ends on approved, cancelled or expired.
4. Process death: system kills the process -> `SavedStateHandle` restores route stack and selection -> graph re-opens -> holders re-read; no secret, prompt or in-flight mutation is restored.

## Integration points

Android Keystore, `BiometricPrompt` (`androidx.biometric`), `ConnectivityManager`, Custom Tabs (`androidx.browser`), `ProcessLifecycleOwner`, Navigation Compose, Roborazzi, Macrobenchmark, GitHub Actions emulator job.

## Architectural decisions

Recorded in `docs/architectural-decision-record.md` as they are made (next number after the highest existing, currently ADR-120). Expected: Keystore design and why not `security-crypto`; `AuthHost`; test runner choice (JUnit 4 + Robolectric for Compose/Roborazzi, JUnit 5 elsewhere; T0.8); `withDeviceTest` adoption; Android live-graph entry point; accessibility results.
