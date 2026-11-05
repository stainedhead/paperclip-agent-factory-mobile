# Plan: Phase 2a Android review fixes

**Date:** 2026-11-04 · **Status:** Planning

## Development approach

TDD red-first, one commit per finding, brief diff review per fix (second reviewer for R-001, R-002, R-004), agent teammates in per-group git worktrees, one Gradle build at a time (`flock /tmp/pafm-build.lock`). Full text in the PRD "Guidance".

## Phase breakdown

1. **Group 1 shared core (sequential, owns `shared/`):** R-001, R-002 (after R-001's test commit), R-004, R-007 shared half, R-024 shared half, R-019 shared parts, R-010, R-011, R-012, R-013. iOS gates after each commit that changes exported surface or shared behaviour.
2. **Group 2 androidApp text and input** (after Group 1): R-003, R-023, R-024 app half, R-025, R-026, R-027, R-028, R-029, R-033, R-034, R-036, R-037.
3. **Group 3 androidApp navigation, lifecycle, Inbox, Run** (authoring parallel with Group 2): R-005, R-006, R-007 app half, R-008, R-018, R-021, R-022, R-032.
4. **Group 4 tests, build, CI:** R-014, R-015, R-016, R-017, R-020, R-030, R-031 (baselines re-recorded once, last, by the lead), R-035.
5. **Group 5 docs and status:** R-039, R-040, R-038 (design only), ADR-123 correction, ADR entries for DEC-1 and DEC-2.
6. **Owner-hardware track (parallel):** R-009 evidence.

Merge order: Group 1 (full gates plus iOS gates), 2, 3, 4, 5; Android CI command and `./gradlew check` after each merge.

## Critical path

Group 1 -> Groups 2 and 3 -> Group 4 (baselines) -> Group 5. R-009 needs R-001 and R-004 applied.

## Testing strategy

Per requirement the failing test named in the PRD first. Gates: spotlessCheck, detekt, lint, allTests, konsistTest, koverVerifyCore, koverVerifyPresentation, check, and the Android CI command (`:androidApp:lint :androidApp:testDebugUnitTest :androidApp:verifyRoborazziDebug :androidApp:assembleRelease`). iOS gates for shared exported changes: `./scripts/build-kit.sh debug && swiftlint --strict && xcrun swift-format lint --recursive --strict iosApp/Sources iosApp/Tests iosApp/Packages/PaperclipKit/Package.swift && ./scripts/ios-test.sh` under the build lock (`flock /tmp/pafm-build.lock`).

## Rollout strategy

Fixes land on the Android branch before its pull request is marked ready.

## Success metrics

0 open P1, R-009 evidence recorded, gates green, coverage at or above core 98.03% and presentation 98.10%.
