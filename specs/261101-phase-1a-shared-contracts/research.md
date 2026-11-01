# Research: Phase 1a shared contracts

**Date:** 2026-11-01 · **Source:** [PRD](../../paperclip-agent-factory-mobile-PRD.md), pinned server Paperclip 2026.1023.0

## Research questions

1. **Badge and "Decide now" (RESOLVED 2026-11-01: match the web app).** The server returns `deskBadgeCount` (new today or overdue) and no `decideNowCount`. Should the app (a) show the server's `deskBadgeCount`, (b) compute "Decide now" client-side as `decideBy` today or `severity = critical` per FR-030 and badge on that, or (c) both? What does the web app show? **Answer:** the web app shows the server's `deskBadgeCount` (`ui/src/lib/attention.ts`, `attentionBadgeCount`) and its "Decide now" shelf is `decideBy` due today or past only, with no severity rule. Decision (owner): match the web app. The app shows `deskBadgeCount` and mirrors the server's `isDecideNow` and `isNewToday` (UTC day boundaries) in domain code so its groups agree with the number. `decideBy` values: `today`, `this_week`, `whenever`, a `YYYY-MM-DD` date, or null.
2. **Unknown attention kinds.** Eleven source kinds exist. Which ones carry `inlineResolvable` in practice, and what does the minimum viable "Open in web app" fallback need (`subject.href`)?
3. **Resolver audience.** How should `resolverAudience` (effective resolver policy, addressee) change what the app lets a board user do on an `issue_thread_interaction`? Is a viewer or a non-addressee ever allowed to respond?
4. **Decision execution.** After `POST /decisions/{id}/decide`, what is the shape of `executionStatus` and the per-effect results, and how long until a terminal state? Needed for the polling use case (FR-043).
5. **Budget incident resolution.** What does the 422 body look like (unaccounted runs, unpriced usage)? Which fields give the observed spend that the raise amount must exceed (FR-052)?
6. **Role discovery.** `GET /api/cli-auth/me` returns companies and an instance-admin flag; where does the per-company role (owner, operator, viewer) come from, and is `viewer` real at this server version?
7. **Cache keys.** Which identifiers are stable across factories (company id, item `dismissalKey`, `dedupKey`) for the cache and for undoing a dismissal?

## Industry standards

[TBD] Material 3 and Human Interface Guidelines only apply in Phase 2. Relevant here: OWASP MASVS L2 for storage and auth (already in the PRD).

## Existing implementations

The Paperclip web app (`ui-dist` in the server package) is the reference for behavior. Source of truth for types: `@paperclipai/shared` 2026.1023.0.

## API documentation

`GET /api/openapi.json` (covers about 217 of roughly 780 operations per the PRD). Server routes read in Phase 0 planning are listed in the [parent plan](../261101-iphone-app/plan.md) section 2.

## Best practices

- State holders as plain classes with `StateFlow`, no Android or iOS types (existing convention).
- Sealed hierarchies with an explicit unsupported branch for server kinds the app does not render.

## Open questions

See the PRD's open questions; the ones that touch this phase are single-user versus roles (PRD Q2) and which actions may run from a notification (PRD Q4, Phase 3).

## References

- `types/attention.d.ts`, `types/decision.d.ts`, `constants.d.ts` in `@paperclipai/shared` 2026.1023.0
- [spec.md](./spec.md) section 5
