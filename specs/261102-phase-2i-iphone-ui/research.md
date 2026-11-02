# Research: Phase 2i iPhone UI

**Date:** 2026-11-02 · **Source PRD:** [phase-2i-iphone-ui-PRD.md](./phase-2i-iphone-ui-PRD.md)

## Research questions

1. Which Swift patterns best mirror SKIE `SkieSwiftStateFlow` into an `@Observable` model without stale reads or main-actor problems under Swift 6 strict concurrency (the existing `FirstRunModel` is the baseline)? Check what the Phase 1a interop review found (boxed nullable numbers, enum bridging).
2. How do the state holders' keys (`ErrorKey`, `ActionsDisabledReason`, effect-summary keys with parameters, `AddressIssue`) enumerate in the exported header, and what is the cleanest String Catalog scheme that tests can verify is complete?
3. How should a Swift `PairingBrowser` built on `ASWebAuthenticationSession` present and report cancellation, and how can UI tests substitute a scripted browser through the graph?
4. How does the demo graph expose scripted biometrics results and call counters to UI tests (launch arguments or a test hook), and is a shared change needed?
5. What accessibility audit APIs are available in the installed Xcode (`performAccessibilityAudit`) and what issues does it flag on the existing first-run screen as a baseline?
6. How can UI tests capture screenshots deterministically (fixed device, locale, demo clock) and export them as attachments to docs/images in CI and locally?

## Industry standards

Apple Human Interface Guidelines (large titles, sheets, swipe actions, Dynamic Type), WCAG 2.2 AA contrast as stated in the PRD NFR.

## Existing implementations

The Paperclip web app (`~/Code/Test/paperclip/ui/src`) and server source (`server/src`, `packages/shared/src`) are the behavioral reference. Phase 1a code and ADR-010 to ADR-037 are the in-repo precedent.

## API documentation

Pinned server 2026.1023.0 (`@paperclipai/shared` types and route source). Answers are recorded here with file and line.

## Best practices

Test first; fixtures from the server's own types; one commit per slice task; every decision that surprises goes into an ADR.

## Open questions

1. Should the demo graph's clock advance (to show relative times updating) or stay frozen (deterministic tests)? (Default: frozen for tests; a debug launch argument for a live-ticking demo.)
2. App icon and launch screen: placeholder in 2i, final art later. (Default: placeholder.)
3. Where do user-facing strings for effect summaries (`ParamKey` style keys from the state holders) live? (Default: String Catalog with format arguments.)

## References

[phase-2i-iphone-ui-PRD.md](./phase-2i-iphone-ui-PRD.md)
