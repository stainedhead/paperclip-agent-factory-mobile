# Status: iPhone app on a shared Kotlin core

**Spec dir:** `specs/261101-iphone-app/` · **Updated:** 2026-11-01

| Step | State |
|---|---|
| 0 Research the PRD | Done (server routes verified in the installed `paperclipai` 2026.1023.0 package) |
| 1 Specification (`spec.md`) | Not started |
| 2 Research and data model | Not started |
| 3 Architecture and plan | `plan.md` drafted, awaiting owner review |
| 4 Task breakdown (`tasks.md`) | Not started |
| 5 Implementation (TDD) | Phase 0 in review: PRs #1 (shared skeleton and gates), #2 (iPhone shell), #3 (Docker test server, contract and security CI) |
| 6 Completion and archive | — |

## Blockers and decisions needed

1. ~~Repository~~ Decided: `stainedhead/paperclip-agent-factory-mobile` created (public, `main`, no protection); local folder initialised, nothing committed yet.
2. Decided: no second local instance; contract tests use Docker at the pinned version (assumed from "no"; confirm).
3. Decided: bundle id `com.stainedhead.paperclipmobile` for now; not publishing yet, so no Apple account or distribution decision is needed.
4. Tools missing on this machine: `swiftlint` (install via Homebrew). Xcode 27.2 and JDK 21 are present.

## Facts to remember

- The local Paperclip is now **running** (port 3100, source build `2026.1023.0+1086`, ahead of the npm package I checked) but is still `local_trusted`, which the app refuses by design. Do not change it. Its `/api/health` already returns `deploymentMode` and `deploymentExposure`, so it is a good fixture for the refusal path. Pin the server version tested by contract tests, not this dev build.
