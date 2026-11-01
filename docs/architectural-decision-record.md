# Architectural decision record

One entry per significant decision, newest last. Format: context, decision, consequences.

## ADR-001: iPhone first in the UI, shared core first in the build

**Context.** The PRD suggests building Android first when there is one UI stream. The request was an iPhone app that reuses the core across UIs.
**Decision.** Build the shared core (domain, application, presentation, fakes) before any screen, then the iPhone UI against it. Android follows and reuses everything below the UI.
**Consequences.** Slower feedback on the core than an Android-first order; covered by JVM tests and demo mode. No SwiftUI screen waits on a server.

## ADR-002: Ship the core to iOS as an XCFramework in a local Swift package

**Context.** The core must reach Xcode either through a Gradle build phase or as a prebuilt framework.
**Decision.** `scripts/build-kit.sh` builds `PaperclipKit.xcframework`; `iosApp/Packages/PaperclipKit` wraps it as a binary target.
**Consequences.** Xcode resolves it natively and CI is deterministic. The framework must be built before the Xcode project resolves.

## ADR-003: Modular shared Kotlin with inward dependencies, enforced by Konsist

**Context.** Android and the push relay should reuse the core without UI code.
**Decision.** Separate Gradle modules per layer and per infrastructure concern; Konsist tests fail the build on a wrong import.
**Consequences.** More modules to maintain; the layering cannot erode unnoticed.

## ADR-004: Poll by default; the WebSocket is an optional adapter

**Context.** Paperclip 2026.1023.0 does not accept board API keys on its live-events WebSocket.
**Decision.** A shared use case refreshes the visible screen on a timer; a WebSocket adapter is built later and enabled only if the server accepts board keys.
**Consequences.** Slightly slower updates; an upstream fix changes one adapter.

## ADR-005: Targets are jvm, android (AGP KMP library plugin) and iOS

**Context.** AGENTS.md named `androidUnitTest`; AGP 9's KMP library plugin names host tests `androidHostTest` and device tests `androidDeviceTest`. A `jvm` target is also needed for fast tests and the relay.
**Decision.** Use the new plugin, add `jvm`, and update AGENTS.md.
**Consequences.** Kover measures the JVM runs of the shared tests; Android host tests still run in `allTests`.

## ADR-006: XcodeGen owns the Xcode project

**Context.** A committed `.xcodeproj` produces noisy merges and hides settings.
**Decision.** `iosApp/project.yml` is committed; `iosApp.xcodeproj` is generated and git-ignored.
**Consequences.** Contributors install XcodeGen; scripts generate the project before building.

## ADR-007: Coverage gates are two Kover variants

**Context.** The PRD wants one aggregate 90% gate over domain + application and a separate one for presentation. Kover rules cannot filter by package.
**Decision.** Two custom variants (`core`, `presentation`) with package filters, each with its own verify rule.
**Consequences.** `koverVerify` alone checks nothing meaningful; use `koverVerifyCore koverVerifyPresentation` (both run in `check`).

## ADR-008: Contract tests run against a pinned Docker image in authenticated mode

**Context.** The developer's local Paperclip is in `local_trusted` mode, which the app refuses by design, and changing it would affect their web app.
**Decision.** A throwaway container at the catalog's `paperclip-server` version on host port 3199.
**Consequences.** Docker is required for contract tests; the version bump is its own pull request.

## ADR-009: Swift-facing names avoid Objective-C reserved prefixes

**Context.** Kotlin/Native renamed `newFirstRunStateHolder` to `doNewFirstRunStateHolder` in Swift.
**Decision.** A Konsist rule rejects public functions starting with `new`, `alloc`, `copy`, `mutableCopy` or `init` in presentation and umbrella.
**Consequences.** Factory methods are named `create...`.

## ADR-010: The inbox badge is the server's `deskBadgeCount`, not a number the app computes

**Context.** The PRD assumed a `decideNowCount` that the server does not provide. The feed returns `deskBadgeCount` (distinct items new today, by UTC day, or with a `decideBy` due or overdue), and that is what the web app shows. The web app's "Decide now" shelf is `decideBy` due today or past only.
**Decision.** Owner decision on 2026-11-01: match the web app. The tab badge and app icon badge show `deskBadgeCount`. The domain mirrors the server's `isDecideNow` and `isNewToday` so the app's groups agree with the number. Severity orders items and never changes their group.
**Consequences.** The number on the phone equals the number on the desk. The Inbox header reads "<n> new or due today", and the Decide now group can hold fewer cards than the badge. If the server changes its rule, one domain function and its tests change.

## ADR-011: Company roles map fail-closed from the server's membership roles

**Context.** The server has five human membership roles (`owner`, `admin`, `operator`, `viewer`, `member`) plus an instance-admin flag, and enforces only "viewers are read-only". The PRD has three personas and makes budget raise owner-only, which the server does not enforce (research Q6/Q9).
**Decision.** Instance admin, `owner` and `admin` map to Owner; `operator` to Operator; `viewer`, `member` and any unknown value to Viewer. `Permissions` is a single Role x Action table in the domain; local factory actions (sign out, rename, remove, settings) are open to every role because they never call a company mutation.
**Consequences.** An unknown future role can only lose abilities in the app, never gain them. The server stays the authority; a 403 after a role change returns `Forbidden`.

## ADR-012: Pairing holds the token in memory only until approval, and changing a factory's address drops its credentials

**Context.** The server returns the board API token when the challenge is created, before the user approves in the browser. The challenge token and the approval URL embed a secret. A board key must never be sent to a host other than the one that issued it.
**Decision.** `PairInstance` keeps the pending token and challenge token as `Secret` values (which redact `toString()` and can be wiped), writes the token to the `SecretStore` only on `approved`, and wipes every in-memory copy on every exit. Editing a factory's address deletes its secrets and marks it Signed out. `SignOutInstance` always deletes local secrets even when the server revoke is unconfirmed, and reports that fact.
**Consequences.** Cancelled or expired challenges leave nothing behind. Re-pairing is required after an address change.

## ADR-013: Device authentication is a domain policy applied inside the use case

**Context.** FR-014 lists the actions that need device authentication and says it cannot be turned off.
**Decision.** `DeviceAuthPolicy.requires(action)` is a pure domain function with no settings input. Use cases call `DeviceAuthGuard` (application) before any gateway or repository call; if the `Biometrics` port does not approve, nothing else runs. `RemoveFactory` joins the list in the PRD's design rules.
**Consequences.** The UI cannot skip or weaken the check. Setting a monthly agent budget is not on the PRD's list and does not require authentication; only the budget raise does.

## ADR-014: Contract suites are plain classes in `shared/testing`, run by thin test subclasses

**Context.** `shared/testing` ships inside `PaperclipKit.xcframework` through the umbrella, so it must not depend on kotlin-test.
**Decision.** Port contract suites are abstract classes with `suspend fun runAll()` that use `check`; each implementation (the fake now, the real adapter in Phase 1b) has a one-line `@Test` in its own test source set.
**Consequences.** The same assertions guard the fake and the adapter without a test framework in a shipped module.
