# Specification: Phase 2i iPhone UI

**Created:** 2026-11-02 · **Status:** Draft · **Spec dir:** `specs/261102-phase-2i-iphone-ui/`
**Source PRD:** [phase-2i-iphone-ui-PRD.md](./phase-2i-iphone-ui-PRD.md) · **Parent plan:** [../261101-iphone-app/plan.md](../261101-iphone-app/plan.md)

## 1. Executive summary

Phase 2i builds the iPhone UI: every PRD screen as SwiftUI that renders the shared state holders and forwards intents, plus the platform shell (tabs, navigation, sheets, app lock, Face ID gating, offline and error presentation, accessibility, localisation). It runs fully in demo mode on the shared fakes and switches to the real factory through one environment seam when Phase 1b lands. UI tests capture real simulator screenshots that replace the design mocks in the documentation.

## 2. Problem statement

The shared Kotlin core has a state holder, demo data and fakes for all 16 screens, but the iPhone app contains one SwiftUI screen (the first-run address check). A board operator cannot see an inbox, decide a decision, approve a hire, pause an agent or read a run. The design mocks exist as HTML images and the PRD describes every behavior, but none of it is visible on a phone.

Phase 2i builds the iPhone UI: every screen in the PRD inventory as SwiftUI that renders shared state and forwards intents, with the platform shell around it (tabs, navigation, sheets, app lock, Face ID prompts, offline and error presentation, accessibility). It runs fully in demo mode on the shared fakes with no server, and switches to the real factory when Phase 1b's live graph lands, without changing any view.

## 3. Goals and non-goals

- **G1** Every screen of the PRD inventory (1 to 16, except screen 14 notification settings, which is Phase 3) as a native SwiftUI view that holds no business rule: it renders `StateFlow` state through an `@Observable` adapter and sends intents.
- **G2** The platform shell: five-tab navigation (Inbox, Factory, Work, Agents, Factories), `NavigationStack` detail pushes, confirmation sheets, the first-run flow, the app lock and Face ID gating for destructive actions through the `Biometrics` port, app-switcher blur, pull to refresh and the refresh-session lifecycle (foreground/background).
- **G3** Visual fidelity to the design mocks (colors, type, spacing, card patterns, destructive styling, lock glyphs, "Updated" labels) using a small design system, with dark mode and Dynamic Type up to 200%.
- **G4** Accessibility and localisation as in the PRD NFR: VoiceOver labels on every control, 44 pt targets, no information by colour alone, every string externalised in a String Catalog; state holders already carry keys, the app maps keys to localized text.
- **G5** Demo mode first: the app launches into a working demo factory ("Try the demo factory" and a debug launch argument) driven by `PaperclipCore.createDemoGraph()`; live mode uses `PaperclipCore.createLiveGraph(...)` (Phase 1b) through one environment switch, behind which the views are identical.
- **G6** Proof: Swift Testing for every adapter model, XCUITest flows per screen family on the demo graph, and screenshot capture from those tests into `docs/images/` and `user-docs/images/` replacing the design mocks as the documentation images.

**Non-goals**

- iPad, Mac Catalyst, landscape-first layouts, widgets, Live Activities, watchOS.
- Push notifications and the notification settings screen (Phase 3).
- Changing shared business rules: gaps found go back to shared Kotlin as their own test-first pull request.
- The Android UI.
- Real-server behavior (Phase 1b); the live switch is wired but verified only to the extent Phase 1b provides.

## 4. User requirements: functional requirements

The full requirement text is in the PRD. Summary:

| ID | Requirement |
|---|---|
| UI-001 | App environment: demo or live graph chosen at launch (`-demo` launch argument and a first-run "Try the demo factory"), graph owned by the `App` scene; holders created once per screen and closed on teardown through an explicit life |
| UI-002 | Generic `StateHolderModel` pattern: an `@Observable @MainActor` adapter per holder that mirrors state from the SKIE async sequence, applies the current value after each intent (no stale reads), cancels observation cleanly, and is  |
| UI-003 | Tab shell and navigation: five tabs with the Inbox badge from `deskBadgeCount`, per-tab `NavigationStack`, deep-link routes for push later, sheets for confirmations and the budget incident sheet. |
| UI-004 | Face ID: a Swift `Biometrics` implementation (or the Kotlin LocalAuthentication one from Phase 1b) mapped to the port; destructive confirmation sheets show the lock glyph and invoke the use case only after approval; app lock after |
| UI-005 | Design system: tokens (color, type, spacing, radius) taken from the mocks; reusable views (`AttentionCard`, `StatusPill`, `SeverityLabel`, `UpdatedLabel`, `DisabledReasonView`, `ErrorBanner`, `EmptyState`, `LoadingState`) with pre |
| UI-006 | Error, offline and empty presentation from keys: one localized message per `ErrorKey`, offline banner with disabled actions and their reason keys, signed-out and needs-re-pair states with the re-pair action. |
| UI-007 | Localisation: a String Catalog with every key; a test that every `ErrorKey`, `ActionsDisabledReason`, `AddressIssue` and screen label key resolves. |
| UI-010 | First run (15), Factories (8), Add/edit factory (9), Manage factories (16), Settings and profile (13): pairing flow with `ASWebAuthenticationSession` through a Swift `PairingBrowser`. |
| UI-020 | Inbox (1): groups Decide now / This week / Whenever, filter chips, inline approve/reject and budget actions, snooze, dismiss with undo, pull to refresh, "Updated" label, open-in-web-app fallback for unsupported kinds. |
| UI-030 | Decision (2), Approval (3), Budget incident sheet (12): option cards with effect summaries, destructive styling, required inputs, confirm/dismiss, execution results, resolved-elsewhere states. |
| UI-040 | Factory home (5), Agents (6), Agent detail (11), Run viewer (7): tiles and spend windows, grouped agents with role-aware actions, run events and auto-scrolling log tail, cancel with confirmation and Face ID. |
| UI-050 | Work list (10) and Issue (4): saved filters, search, create issue, thread/plan/runs/files tabs, interaction forms with live validation, comment box, status and assignee actions. |
| UI-060 | Polish: Dynamic Type 200%, VoiceOver audit, dark mode, reduced motion, haptics, iPhone SE and Pro Max layouts, launch screen and app icon placeholder. |

## 5. Non-functional requirements

- **Security:** views never touch secrets, HTTP or persistence; no secret in view models, logs or screenshots; screenshots are captured from the demo graph only and never from the pairing screen; app-switcher snapshot blurred; no ATS exceptions; no analytics.
- **Performance:** cold start to the demo Inbox under 1.5 s on an iPhone-class simulator build (measured); list scrolling without dropped frames on 200 items (measured with a UI test or Instruments note); state mirroring does no work on the main thread beyond assignment.
- **Reliability:** no retain cycles or leaked holders (a test creates and destroys each screen model and asserts the holder is closed); cancellation of observation never closes a holder owned by the app.
- **Accessibility and localisation:** per the PRD NFR; automated checks in XCUITest where the platform allows (`performAccessibilityAudit`).
- **Platform:** iOS 17 and later, Swift 6 strict concurrency with zero warnings, SwiftLint `--strict` and swift-format `--strict` clean.
- **Quality:** Swift Testing and XCUITest suites green in `ios.yml`; shared Kotlin gates unchanged.

## 6. System architecture

All new code lands in iosApp/ (SwiftUI views, @Observable adapters over the SKIE flows, design system, String Catalog, Swift PairingBrowser and Biometrics adapters, tests). Shared Kotlin changes are limited to defects or gaps found by the UI, each as its own test-first commit. Dependencies stay inward: views -> models -> PaperclipKit (umbrella only). No business rule, HTTP, persistence or secret handling in Swift.

## 7. Scope of changes

Create: iosApp/Sources/App/** (shell, design system, adapters, 15 screens), iosApp/Sources/App/Resources/Localizable.xcstrings, Swift tests per adapter, XCUITest flows per screen family, screenshot capture into docs/images and user-docs/images, lint config adjustments. Modify: project.yml, docs/product-details.md, README.md, user-docs/*, AGENTS.md only if a rule changes.

## 8. Breaking changes

None for users. The PaperclipApp entry changes from the one-screen shell to the tab shell; the Swift test targets are extended.

## 9. Success criteria and acceptance criteria

1. Every screen in UI-010 to UI-050 is reachable in the demo app and renders the demo data of its design mock (values asserted in XCUITest by accessibility identifier).
2. Main flows work end to end on the demo graph: add a factory (first run), switch factory and company, decide the decision (non-destructive and destructive with Face ID cancelled and approved), approve and reject a hire (reject with note), resolve the budget incident, answer the agent's two questions, pause an agent and cancel a run with Face ID, create an issue, snooze and dismiss with undo.
3. Role and offline variants are visible: a viewer sees no actions; offline disables actions with their reasons and shows cached data with "Updated".
4. Face ID gating: with the biometrics stub cancelled or failed, no mutation occurs (asserted through the demo graph's call counters, reachable from a test hook).
5. Accessibility: `performAccessibilityAudit` passes on each screen family; Dynamic Type at the largest accessibility size shows no truncated primary actions on the Inbox, Decision and Approval screens (screenshot review); every control has a label.
6. Localisation: the String Catalog resolves every key (test); no hard-coded user-facing English in view code outside the catalog (SwiftLint custom rule or script).
7. Lifecycle: creating and leaving every screen closes its holder (test), switching tabs keeps state, backgrounding stops refresh sessions and foregrounding resumes them and re-engages the app lock after the idle period.
8. Live switch: the app builds against `createLiveGraph` when Phase 1b is merged and a launch without `-demo` starts at the empty first-run flow; until then this criterion is verified by a compile-time seam test.
9. Documentation: `docs/product-details.md`, `README.md` and `user-docs/` use real simulator screenshots captured by the UI tests, labelled as built; the design mocks stay as design references.
10. Gates: `./gradlew check lint`, `./scripts/build-kit.sh debug`, SwiftLint, swift-format, `./scripts/ios-test.sh` and all four workflows green; Kover gates not reduced.

Quality gates for every milestone: `./gradlew check lint` green, Kover gates (90%) unchanged or higher, SwiftLint and swift-format `--strict` clean, `./scripts/ios-test.sh` green, workflows `shared`, `ios`, `contract`, `security` green on the pull request.

## 10. Risks and mitigation

| # | Item | Impact | Mitigation |
|---|---|---|---|
| 1 | Phase 1b not yet merged | live mode unverified | demo graph is the primary target; one environment seam for live; compile-time seam test |
| 2 | SKIE boxed nullable numbers and sealed types in Swift | awkward view code | thin Swift extension layer per state type; record gaps; propose shared fixes as separate PRs |
| 3 | UI tests are slow and flaky on CI simulators | red CI | focus helper, explicit waits, accessibility identifiers, retry only the focus step; keep UI test count moderate and group flows |
| 4 | XCFramework rebuild time | slow loop | simulator-only debug build script already exists; Swift views previewed with the demo graph |
| 5 | Face ID cannot be exercised on CI | gating untested end to end | biometrics stub through the demo graph with scripted results; document the manual device check |
| 6 | Parallel work with Phase 1b | conflicts in `shared/umbrella` and `iosApp` | 2i owns `iosApp/**` (and docs/images); 1b owns `shared/infrastructure/**` and live wiring; shared API changes go through small separate commits |
| 7 | Large scope (15 screens) | long run | milestones per screen family, each its own commit group, UI tests per family |

## 11. Timeline and milestones

Milestones in dependency order; relative sizes: 2i-0 M, 2i-1 L, 2i-2 L, 2i-3 L, 2i-4 L, 2i-5 L, 2i-6 M. Phase 1b runs in parallel; the live switch is integrated last.

## 12. References

- PRD: [phase-2i-iphone-ui-PRD.md](./phase-2i-iphone-ui-PRD.md)
- [Main PRD](../../paperclip-agent-factory-mobile-PRD.md), [AGENTS.md](../../AGENTS.md), [INTENT.md](../../INTENT.md)
- [Phase 1a spec](../archive/261101-phase-1a-shared-contracts/spec.md), [Phase 1a fix spec](../archive/261101-phase-1a-shared-contracts-auto-review/spec.md)
