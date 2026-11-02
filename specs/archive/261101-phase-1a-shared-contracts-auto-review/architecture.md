# Architecture: Phase 1a review fixes

**Date:** 2026-11-01 · **Status:** Draft

## Architecture overview

No structural change. Fixes keep dependencies inward and the fixed mutation order (permission, validation, device authentication, one gateway call, one re-read on timeout or conflict).

## Component architecture

Group 1: `application/auth` (`KeyEnrollment`, `PairInstance`, `SaveManualBoardKey`, `SignOut`), `application/instance` (`AddInstance`, `UpdateInstance`), presentation first-run and edit holders. Group 2: presentation holders, plus use cases that re-read before acting. Group 3: `shared/testing` contracts and fakes, Konsist rules, workflows. Group 4: docs, ADRs, spec and PRD text.

## Layer responsibilities

Unchanged: domain owns rules; application owns one action and its sequencing; presentation owns what is enabled, confirmed and shown.

## Data flow

Address edit during pairing: the use case captures the address at the start, validates the key against that host, re-reads the instance before writing, and aborts without writing if the address changed.

## Sequence diagrams

[TBD: pairing with a concurrent address edit; sign-out with cancellation during revoke.]

## Integration points

Ports unchanged except `AuthGateway` key validation (see data dictionary).

## Architectural decisions

New ADRs as decisions are made (probe bound to address; where the 401 marker lives; whether demo ships in release).
