# Status: Phase 1a shared contracts

**Created:** 2026-11-01 · **Spec dir:** `specs/261101-phase-1a-shared-contracts/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | In Progress |
| Phase 1 Research and data model | Not Started |
| Phase 2 Architecture and plan | Not Started |
| Phase 3 Task breakdown | Not Started |
| Phase 4 Implementation (slices 1a-1 to 1a-4) | In Progress (1a-1 built, awaiting PR) |
| Phase 5 Completion and archive | Not Started |

## Phase 0 checklist

- [x] Spec directory created and `spec.md` populated from the PRD and the server types
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized
- [x] `/dev-flow:review-spec` run 2026-11-01: verdict Needs revision; edge cases, acceptance criteria 8 to 14, task count, decision inputs and open-question paths fixed. Remaining warnings: use case list and sequence diagrams (due before slice 1a-2).
- [x] PRD updated for the badge decision (FR-030, FR-032, FR-082, design conventions); [ ] other server differences in spec section 5 (source kinds, sixth interaction kind, `deploymentExposure`)

## Slice 1a-1 (instances, secrets, settings, auth, roles; screens 15, 8, 16, 9, 13)

- [x] Research Q6/Q9, health, challenge create/poll/approve answered in `research.md` (ADR-011, ADR-012)
- [x] Domain: value objects, `Secret`, `DomainError`, `Role`, `Action`, `Permissions`, `DeviceAuthPolicy`, `AppLockPolicy`, entities, ports
- [x] Testing: fakes for all ten ports, `TestData`, contract suites for `InstanceRepository`, `SecretStore`, `AppSettingsStore`
- [x] Application: probe, add, update, reorder, remove, observe, switch, pair, manual key, sign out (+ everywhere), key list and revoke, expiry status, credential check, settings, deep links
- [x] Presentation: `FirstRun`, `Factories`, `ManageFactories`, `EditInstance`, `Settings`, `App` state holders
- [x] Umbrella wiring on fakes; iOS app and tests build and pass (`GraphInteropTests` added)
- [x] Konsist: fake per port, `Secret.reveal()` callers, no `Result` or declared generics in the presentation surface
- [x] `./gradlew spotlessApply && ./gradlew check lint -q` green; iOS `build-kit`, SwiftLint, swift-format and `ios-test.sh` green
- [ ] Open the slice 1a-1 pull request (not opened by the implementing agent)

## Blockers

- Phase 0 pull requests #1 to #3 are not merged yet. Phase 1a branches stack on `phase-0/contract-server`.
- None for the badge: resolved 2026-11-01 (match the web app, `deskBadgeCount`).

## Recent activity

- 2026-11-01: slice 1a-1 implemented on `feat/phase-1a-shared-contracts` (domain, fakes, 30 use cases, six state holders, Koin wiring, Konsist rules, iOS interop test).
- 2026-11-01: spec created from the PRD, the iPhone plan and the pinned server's shared types.
