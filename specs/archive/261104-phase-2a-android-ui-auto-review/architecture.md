# Architecture: Phase 2a Android review fixes

**Date:** 2026-11-04 · **Status:** Draft

## Architecture overview

No structural change. Dependencies stay inward. The mutation order (permission, validation, device authentication, one gateway call, one re-read) and no-auto-retry are not relaxed.

## Component architecture

securestore and platform androidMain (R-001, R-002, R-010, R-012, R-013); domain and application `SetAgentBudget` and `DeviceAuthPolicy` (R-004); presentation holders (R-007, R-019, R-024); androidApp screens, shell and env; Konsist and CI.

## Layer responsibilities

Business rules move from androidApp into shared presentation (R-019, INTENT principle 5).

## Data flow

Protected read: store asks backend to `open`; backend returns an unused cipher; launcher shows `BiometricPrompt` with the `CryptoObject`; the store runs `doFinal` with the returned cipher; the prompt wait is bounded and the store mutex is not held across it.

## Sequence diagrams

[TBD]

## Integration points

`PaperclipKit.xcframework` consumers (iosApp) for R-004, R-007, R-019, R-024.

## Architectural decisions

New ADRs: R-001 (replaces ADR-123's window claim), DEC-1 (amends ADR-013/FR-014), DEC-2.
