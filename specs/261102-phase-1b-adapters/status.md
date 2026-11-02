# Status: Phase 1b shared adapters

**Created:** 2026-11-02 · **Spec dir:** `specs/261102-phase-1b-adapters/`

Update this file after every task and phase. It is the single source of progress.

## Overall progress

| Phase | State |
|---|---|
| Phase 0 Specification | In Progress |
| Phase 1 Research and data model | Not Started |
| Phase 2 Architecture and plan | Not Started |
| Phase 3 Task breakdown | Not Started |
| Phase 4 Implementation | In Progress (1b-1, 1b-2 done) |
| Phase 5 Completion and archive | Not Started |

## Phase 0 checklist

- [x] Spec directory created; `spec.md` populated from the PRD
- [x] Research questions identified (`research.md`)
- [x] Phase files initialized
- [ ] `/dev-flow:review-spec` run and findings resolved

## Milestones

- [x] 1b-1 Ktor client, error mapping, health and auth/pairing gateways (AD-001 to AD-005, AD-010, AD-011)
- [x] 1b-2 SQLDelight persistence and repositories (AD-020 to AD-022)
- [ ] 1b-3 iOS Keychain, biometrics, connectivity and system services; Android/JVM fail-closed actuals (AD-030 to AD-033)
- [ ] 1b-4 Attention, decision, approval, budget, issue, interaction, agent, run and dashboard gateway adapters (AD-012 to AD-014)
- [ ] 1b-5 Live graph, contract test against the pinned server in CI, documentation (AD-040, AD-041, AD-050, AD-051)

## Blockers

(none)

## Recent activity

- 2026-11-02: spec created from the PRD.
- 2026-11-02: milestone 1b-1 done (AD-001 to AD-005, AD-010, AD-011): Ktor and serialization in the catalog; `ErrorMapper` with platform TLS/timeout classification (4ee0d10); `PaperclipHttpClient` (fca20cb); `HealthGatewayAdapter` and `StubPaperclipServer` (16b6113); `AuthGatewayAdapter`, secrets audit and Konsist rules (d5d5a97, 2414a94). Health, pairing, wrong-server and account contract suites pass on the real adapters through the stub. Full `check lint` green; CI results recorded below when read.
- 2026-11-02: milestone 1b-2 done (AD-020 to AD-022): SQLDelight 2.4.1 module `shared/infrastructure/persistence` with schema v1 and v2 (`1.sqm`), `SqlInstanceRepository`, `SqlAppSettingsStore`, `SqlAttentionCache`, `PaperclipPersistence`, JVM/iOS/Android factories, recreate-on-corruption, serial executor, secrets audit, iOS backup exclusion verified on the simulator, Konsist rules. Commits are listed in implementation-notes.md. ADR-042, ADR-043.
