# Tasks

- T1 `Secret.copy()` (domain), test first.
- T2 `SessionCachingSecretStore` + `SessionTokenMemory` + `SessionTokens`, test first (copies, per factory, single flight, refusals, cancellation, clearing, generations, contract, warm cache still asks).
- T3 `SignedOutMarker` forgets the factory's token on 401 and `KeyInvalidated`.
- T4 Umbrella: device store under `RawPort`, decorator bound for every graph, `AppLifecycleBridge` clears on background.
- T5 Konsist `SessionCacheRulesTest` (memory only, constructed by the composition root only).
- T6 Investigate unlock = protected read; decision recorded in ADR-145.
- T7 Docs: ADR-145, ADR-144/139/141/142 status text, technical details (known limitation 12, counts), user-docs/app-lock.md, device checklist item 4.
- T8 Gates: spotless, detekt, allTests, konsist (rerun), kover, lint, androidApp unit tests, Roborazzi verify, assembleRelease, emulator device tests, iOS build-kit, swiftlint, swift-format, ios-test.
