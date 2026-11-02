# Technical details

How the code is organised and built. The rules behind it are in [AGENTS.md](../AGENTS.md); the reasons for the main choices are in the [decision record](./architectural-decision-record.md).

## Layers and modules

Dependencies point inward only (enforced by Konsist, see below):

```
iosApp (SwiftUI)  ──imports only──►  shared/umbrella  (Koin graph, PaperclipKit.xcframework)
                                          │
                       presentation ─► application ─► domain ◄─ infrastructure/*
```

| Module | Holds today | Status |
|---|---|---|
| `shared/domain` | `BaseUri` (address rules), `BaseUriError`, `BaseUriResult` | in use |
| `shared/application` | `ValidateInstanceAddress` use case | in use |
| `shared/presentation` | `FirstRunStateHolder`, `FirstRunState`, `FirstRunIntent`, `AddressIssue` | in use |
| `shared/umbrella` | `PaperclipCore.createGraph(...)`, `PaperclipGraph`, the XCFramework and SKIE setup, the Konsist rules | in use |
| `shared/testing` | fakes and demo data | empty until Phase 1a |
| `shared/infrastructure/{api,realtime,persistence,securestore,platform}` | adapters | empty until Phase 1b |

Every module is a Kotlin Multiplatform library with these targets: `jvm` (fast tests, the future push relay), `android` (AGP's KMP library plugin) and `iosArm64` / `iosSimulatorArm64`. Module build files are a few lines each; targets, the JDK 21 toolchain, "warnings are errors" and the common test dependencies live in the convention plugin `build-logic/src/main/kotlin/pafm.kmp-library.gradle.kts`.

Package root: `com.stainedhead.paperclipmobile`. Every dependency version is in `gradle/libs.versions.toml`, including `paperclip-server`, the Paperclip release that contract tests and fixtures are pinned to.

## How iOS consumes the shared core

1. `./scripts/build-kit.sh` runs Gradle to build `PaperclipKit.xcframework` (SKIE turns Kotlin `Flow` into Swift `AsyncSequence` and sealed types into Swift enums) and copies it to `iosApp/Frameworks/` (git-ignored).
2. `iosApp/Packages/PaperclipKit` is a local Swift package whose binary target points at that XCFramework.
3. `iosApp/project.yml` is the source of truth for the Xcode project; `xcodegen generate` creates `iosApp.xcodeproj` (git-ignored). The app is Swift 6 with complete strict concurrency, iOS 17.
4. SwiftUI views render state from a `*Model` that mirrors a Kotlin state holder and forwards intents. They hold no rules.

Kotlin/Native renames functions that start with `new`, `alloc`, `copy`, `mutableCopy` or `init` (for example `newFoo` becomes `doNewFoo` in Swift). A Konsist rule keeps the Swift-facing modules free of those names.

## Quality gates

`./gradlew check lint` runs all of these; `shared.yml` runs it on every pull request.

| Gate | Tool | What it checks |
|---|---|---|
| Format | Spotless with ktlint | `.kt` and `.kts` files, config in `.editorconfig` |
| Static analysis | detekt | all shared sources, config in `config/detekt/detekt.yml`, zero issues |
| Architecture | Konsist (`:shared:umbrella:konsistTest`) | import rules per layer, one `operator fun invoke` per use case, state holders expose `state` and `onIntent`, no Objective-C reserved names |
| Coverage | Kover | `koverVerifyCore`: 90% line coverage over domain + application (one aggregate); `koverVerifyPresentation`: 90% for presentation |
| Tests | kotlin.test, Kotest, Turbine | `allTests`: JVM, Android host (`androidHostTest`) and iOS simulator |
| Android Lint | AGP | `lint` |
| Swift | SwiftLint `--strict`, swift-format `--strict`, Swift Testing, XCUITest | `ios.yml` and `./scripts/ios-test.sh` |

Each gate was shown to fail on a deliberate violation before it was trusted. Konsist rules also assert they can see the code they guard, so an empty scan cannot pass.

## CI

| Workflow | Runs on | Does |
|---|---|---|
| `shared.yml` | ubuntu | `./gradlew check lint` |
| `ios.yml` | macOS | Kotlin/Native tests, XCFramework, SwiftLint, swift-format, Xcode unit and UI tests |
| `contract.yml` | ubuntu | starts the pinned Paperclip in Docker and checks it reports `authenticated`; nightly also checks `latest` |
| `security.yml` | ubuntu | gitleaks on the full history |

Dependabot (`.github/dependabot.yml`) proposes weekly grouped updates for Gradle dependencies and GitHub Actions. Dependabot alerts and dependency review need the repository's Dependency graph switched on in GitHub settings, which is not done yet.

## The Paperclip test server

`docker/paperclip-test/compose.yml` runs `ghcr.io/paperclipai/paperclip:<paperclip-server>` with `PAPERCLIP_DEPLOYMENT_MODE=authenticated`, exposure `private`, no LLM keys, on host port 3199. `scripts/paperclip-test-server.sh` reads the version from the catalog, starts it, waits for health and checks the mode. The app refuses `local_trusted` servers, so a developer's usual local Paperclip cannot be used for contract tests.

## Known server facts (Paperclip 2026.1023.0)

- Board keys are rejected on the live-events WebSocket (`/api/companies/{id}/events/ws` accepts agent keys and browser sessions), so the app polls.
- `/api/health` returns `deploymentMode` and `deploymentExposure` to anyone; the server `version` is only in the response to signed-in callers.
- A fresh authenticated server reports `bootstrapStatus: bootstrap_pending` (no users yet). Phase 1b contract tests must complete the bootstrap invite to create the first board user before they can pair.
