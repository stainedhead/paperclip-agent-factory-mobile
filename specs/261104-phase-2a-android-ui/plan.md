# Plan: Phase 2a Android UI

**Date:** 2026-11-04 · **Status:** Planning

## Development approach

TDD (red, green, refactor) with one commit per task. Shared first: 2a-1 and 2a-2 change `shared/` and merge before the UI milestones that consume them; the UI stream owns `androidApp/**` and `docs/images/android-*`. Demo graph first, live graph second, behind one seam. Host tests with fakes carry most of the proof; emulator tests are limited to Keystore, biometric, persistence and a handful of flows. ktlint, detekt, Lint and Konsist run on every commit. `status.md` is updated after every task.

## Phase breakdown

1. 2a-0 Foundations (androidApp skeleton, catalog, device-test source set, test runner, CI; execution order T0.1 to T0.4, T0.6, T0.7, T0.8, T0.5).
2. 2a-1 Android device services (shared PR).
3. 2a-2 Android live graph entry point (shared PR).
4. 2a-3 Shell, design system, first run.
5. 2a-4 Factories family (8, 9, 13, 16, Paired devices, Diagnostics).
6. 2a-5 Inbox, Decision, Approval, Budget incident.
7. 2a-6 Factory home, Agents, Agent detail, Run viewer.
8. 2a-7 Work list and Issue.
9. 2a-8 Polish, accessibility, security hardening, performance (includes the `:androidApp:benchmark` module, end-to-end demo-flow test T8.8 and emulator live smoke T8.9).
10. 2a-9 Documentation, screenshots, release gate.

## Critical path

2a-0 (T0.1) and 2a-1/2a-2 -> 2a-3 -> any of 2a-4..2a-7 -> 2a-8 -> 2a-9. 2a-1 (Keystore, BiometricPrompt) is the largest new shared item and the riskiest; start it first. 2a-5 is the core operator value.

## Testing strategy

Shared rules stay in `commonTest`. New Android services: `androidHostTest` with fake backends plus `androidDeviceTest` on an emulator. `androidApp/src/test`: presentation mapping, key-to-string completeness, route table, `HolderViewModel` lifecycle, Compose and Roborazzi tests on the demo graph. `androidTest`: accessibility checks and a small set of flows. Konsist rules for `androidApp` with planted-violation self-tests. Contract tests unchanged. Test runner (T0.8): Compose and Roborazzi tests are JUnit 4 + Robolectric; pure JVM tests JUnit 5 through Vintage when available; shared modules unchanged; this amends the PRD's "Robolectric-free" and AGENTS.md's JUnit 5 row (updated in T9.1). Macrobenchmark lives in `:androidApp:benchmark` (T8.6), not per PR. Details: PRD "Testing Strategy".

## Rollout strategy

One pull request per milestone from a feature branch off `main`; shared milestones first; automerge after green checks. Distribution (Play) is out of scope; exit is a debug-signed and a locally upload-key-signed internal APK. Nothing merges without green `shared`, `android`, `ios`, `contract`, `security` workflows.

## Success metrics

All ten acceptance criteria in the PRD (reproduced in `spec.md` section 9) met with evidence; gates green; Kover gates not reduced; cold start median under 1.5 s recorded in `status.md`; parity ledger with no unlinked gap.
