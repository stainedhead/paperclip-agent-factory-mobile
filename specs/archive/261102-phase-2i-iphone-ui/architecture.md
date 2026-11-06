# Architecture: Phase 2i iPhone UI

**Date:** 2026-11-02 · **Status:** Draft

## Architecture overview

All new code lands in iosApp/ (SwiftUI views, @Observable adapters over the SKIE flows, design system, String Catalog, Swift PairingBrowser and Biometrics adapters, tests). Shared Kotlin changes are limited to defects or gaps found by the UI, each as its own test-first commit. Dependencies stay inward: views -> models -> PaperclipKit (umbrella only). No business rule, HTTP, persistence or secret handling in Swift.

## Component architecture

- App shell (App, tab view, navigation routing, environment).
- Model layer (one adapter per holder).
- Views by screen family under Screens/.
- Design system under DesignSystem/.
- Resources (String Catalog, assets).

## Layer responsibilities

Unchanged: domain owns rules; application owns one action; presentation owns what is enabled and shown; infrastructure implements ports and never leaks DTOs, Ktor, SQLDelight or platform types inward; apps render state and forward intents.

## Data flow

User action -> view -> model.intent -> shared state holder -> use case -> port (fake or live) -> state update -> SKIE flow -> model.state -> view re-renders.

## Sequence diagrams

[TBD: drawn in the architecture phase.]

## Integration points

PaperclipKit.xcframework (SKIE), ASWebAuthenticationSession, LocalAuthentication (via port), SwiftUI/Observation, XCTest/Swift Testing.

## Architectural decisions

Recorded in `docs/architectural-decision-record.md` as they are made (next number after the highest existing).
