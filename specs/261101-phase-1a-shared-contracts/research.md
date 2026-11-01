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

8. **Where do decision inputs attach?** `DecisionInput` exists in the shared types but `DecisionOption` has no `inputs` field. Check the `GET /api/decisions/{id}` response and the decide request body (`server/src/routes/decisions.ts`). Owner: whoever starts slice 1a-2, before modelling `Decision`.
9. **Role source.** Same as Q6; the answer fixes the `Role` mapping and the permissions table (acceptance criterion 8).

Resolution path for all open questions: answer from the server source or a live authenticated instance (`./scripts/paperclip-test-server.sh up`), record the answer here with the file or response that proves it, then update the data dictionary. Questions 3 to 9 are due before the slice that needs them starts (2 to 5 before 1a-2; 6 and 9 before 1a-1; 7 before 1a-3).

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

## Answers recorded in slice 1a-1 (server source, Paperclip checkout at `~/Code/Test/paperclip`)

Paths below are relative to that checkout.

**Q6 / Q9 Role source (ANSWERED).** `GET /api/cli-auth/me` (`server/src/routes/access.ts:2885-2898`) returns `{user, userId, isInstanceAdmin, companyIds, memberships, source, keyId}`. `memberships` is a list of `{companyId, membershipRole, status}` built by `resolveBoardAccess` (`server/src/services/board-auth.ts:72-110`), active rows only (`board-auth.ts:93-99`). It carries no company names: names come from `GET /api/companies?scope=accessible` (`server/src/routes/companies.ts:380-400`).
- Server membership roles are `owner`, `admin`, `operator`, `viewer`, `member` (`packages/shared/src/constants.ts:965-979`). `viewer` is real and enforced: a non-safe method by a non-instance-admin viewer fails with `403 "Viewer access is read-only"` (`server/src/routes/authz.ts:111-118`); instance admins skip the check (`authz.ts:111`).
- The server does not distinguish owner from operator on budget routes (`server/src/routes/costs.ts:352-377` only calls `assertBoard` and `assertCompanyAccess`). "Owner only" for budget raise is therefore an app policy from the PRD, not a server rule; the server stays the authority (a 403 is shown).
- Mapping (ADR-011): instance admin, `owner`, `admin` -> Owner; `operator` -> Operator; `viewer`, `member`, anything unknown -> Viewer (fail closed). Per-company role: `isInstanceAdmin` wins over the membership row.

**Challenge create** (`access.ts:2786-2808`, request schema `packages/shared/src/validators/access.ts:73-78`): `POST /api/cli-auth/challenges` body `{command (1-240), clientName?, requestedAccess (default board), requestedCompanyId?}` returns 201 `{id, token, boardApiToken, approvalPath, approvalUrl (nullable), pollPath, expiresAt, suggestedPollIntervalMs: 1000}`. The board API token (`boardApiToken`) is returned at CREATE, not on approval; the challenge `token` and `approvalPath/approvalUrl` embed a secret. Challenge TTL is 10 minutes, board key TTL 30 days (`board-auth.ts:17-18`).
**Poll** (`access.ts:2810-2838`): `GET /api/cli-auth/challenges/{id}?token=<challenge token>` returns the challenge (`id, status, command, clientName, requestedAccess, requestedCompanyId, requestedCompanyName, approvedAt, cancelledAt, expiresAt`) plus `requiresSignIn`, `canApprove`, `currentUserId`. It does not return the token or the key id. Status precedence `cancelled > expired > approved > pending` (`board-auth.ts:49-54`).
**Approve / cancel** (`access.ts:2840-2882`) happen in the browser; the app only creates, polls and may cancel (`POST .../{id}/cancel {token}`).
**Key metadata:** `me.keyId` is the current key id when `source == "board_key"` (`access.ts:2897`); its `expiresAt` (nullable, `board-auth.ts:254`) comes from `GET /api/board-api-keys` (`access.ts:2901-2910`, fields `id,name,createdAt,lastUsedAt,revokedAt,expiresAt`).
**Revoke:** `DELETE /api/board-api-keys/{keyId}` -> `{ok, keyId}`; `POST /api/cli-auth/revoke-current` -> `{revoked, keyId}` (`access.ts:2954-2980`).

**`GET /api/health`** (`server/src/routes/health.ts`): an unauthenticated caller of an `authenticated` server gets the redacted body `{status, deploymentMode, deploymentExposure, localAiLoginSupported, commit, bootstrapStatus, bootstrapInviteActive, ...}` with NO version (`health.ts:45-51, 394-418`). `local_trusted` servers return full details to anyone. The `!db` branch omits `deploymentExposure` (`health.ts:270-290`); the 503 branch omits `deploymentMode` (`health.ts:318-330`). Hence `serverVersion` and `exposure` are nullable before pairing, and the probe fails closed: only `deploymentMode == authenticated` with status `ok` and `bootstrapStatus == ready` is accepted.
