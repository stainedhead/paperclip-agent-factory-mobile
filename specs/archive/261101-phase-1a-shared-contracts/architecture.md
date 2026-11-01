# Architecture: Phase 1a shared contracts

**Date:** 2026-11-01 · **Status:** Draft

## Architecture overview

Unchanged from [the parent plan](../../261101-iphone-app/plan.md) section 3: presentation, application and domain in shared Kotlin; infrastructure implements domain ports; the umbrella wires them; apps render state. This phase adds the contents of the three inner layers and the fakes.

## Component architecture

- **Domain:** entities, value objects, ports, `DomainError`, `Role`, `Permissions`, `DeviceAuthPolicy`.
- **Application:** use cases grouped by feature package: `instance`, `auth`, `attention`, `decision`, `approval`, `budget`, `issue`, `agent`, `run`, `dashboard`, `refresh`.
- **Presentation:** one state holder per screen, each with `state: StateFlow<XState>` and `onIntent(XIntent)`.
- **Testing:** `fakes/` (one per port), `fixtures/`, `DemoData`, `contract/` (abstract test suites).
- **Umbrella:** `PaperclipCore.createGraph(mode)` builds a live or demo graph; the demo graph is all fakes.

## Layer responsibilities

| Layer | Owns | Never |
|---|---|---|
| Domain | what things are, what is allowed (roles, auth policy) | I/O, JSON, time except via `Clock` |
| Application | one action, its preconditions and sequencing across ports | UI wording, platform types |
| Presentation | what is enabled, confirmed, shown, and error keys | platform widgets, strings |

## Data flow

Intent -> state holder -> use case -> port (fake now, adapter in 1b) -> result -> state update. Reads render cached data first (FR-095), then refresh.

## Sequence diagrams

Slice 1a-2 (attention, decisions, approvals, budget incidents). Pause agent and polling refresh are drawn with slice 1a-3.

### 1. Decide a decision

```mermaid
sequenceDiagram
    actor U as Person
    participant UI as Decision screen
    participant SH as DecisionStateHolder
    participant UC as DecideDecision
    participant PM as Permissions and DeviceAuthPolicy
    participant BIO as Biometrics
    participant GW as DecisionGateway
    U->>UI: pick an option, fill inputs, tap Confirm
    UI->>SH: Confirm intent
    SH->>SH: ignore if a mutation is in flight or offline
    SH->>UC: invoke(context, decision, optionId, inputValues)
    UC->>PM: allows(role, DecideOption)?
    alt viewer
        UC-->>SH: Denied (no gateway call)
    end
    UC->>UC: required inputs present, maxLength ok?
    alt invalid
        UC-->>SH: Invalid (no prompt, no gateway call)
    end
    opt option is destructive or has a cancel_issue_tree effect
        UC->>BIO: authenticate(ConfirmDestructiveAction)
        BIO-->>UC: not approved
        UC-->>SH: NotAuthorized (no gateway call)
    end
    UC->>GW: decide(optionId, inputValues, idempotencyKey) exactly once
    alt success
        GW-->>UC: decision with executionStatus and per-effect results
        UC-->>SH: Decided(decision)
        opt executionStatus is running
            SH->>UC: PollDecisionExecution (re-read until terminal, on the Clock)
        end
    else timeout or 409 conflict
        UC->>GW: get(id) re-read, never decide again
        UC-->>SH: Reconciled(current decision)
    end
    SH-->>UI: final state, per-effect executed / skipped / failed
```

### 2. Resolve an approval with device authentication

```mermaid
sequenceDiagram
    actor U as Person
    participant SH as ApprovalStateHolder
    participant UC as RejectApproval
    participant BIO as Biometrics
    participant GW as ApprovalGateway
    U->>SH: Reject with a note
    SH->>UC: invoke(context, approvalId, note)
    UC->>UC: role allows RejectApproval? note not blank?
    UC->>BIO: authenticate(ConfirmDestructiveAction)
    alt cancelled, failed, locked out, unavailable
        BIO-->>UC: not approved
        UC-->>SH: NotAuthorized (zero gateway calls)
    else approved
        BIO-->>UC: Approved
        UC->>GW: reject(approvalId, note) once
        alt success
            GW-->>UC: approval (status rejected)
            UC-->>SH: Resolved(approval)
        else 422 or 409 (resolved elsewhere) or timeout
            UC->>GW: get(approvalId)
            GW-->>UC: current approval
            UC-->>SH: Reconciled(current approval, final state, no actions)
        end
    end
```

Approve and Comment skip the Biometrics step (not in `DeviceAuthPolicy`); Request revision needs a note and `pending` status.

### 3. Resolve a budget incident

```mermaid
sequenceDiagram
    actor U as Person
    participant SH as BudgetIncidentStateHolder
    participant UC as ResolveBudgetIncident
    participant BIO as Biometrics
    participant GW as BudgetGateway
    U->>SH: Raise and resume with a new amount
    SH->>UC: invoke(context, incident, RaiseAndResume(amount))
    UC->>UC: role is Owner? (operator and viewer: Denied)
    UC->>UC: amount greater than observed spend? (else Invalid)
    UC->>BIO: authenticate(ConfirmDestructiveAction)
    BIO-->>UC: Approved
    UC->>GW: resolve(incident, raise_budget_and_resume, amount) once
    alt 422 with the server reason
        GW-->>UC: Rejected(reason)
        UC-->>SH: Failed(Rejected(reason)) shown as the reason
    else success
        GW-->>UC: incident (resolved)
        UC-->>SH: Resolved
    end
    Note over UC,GW: Keep paused needs Operator or Owner and no device authentication
```

### 4. Inbox load, cache first then refresh

```mermaid
sequenceDiagram
    participant SH as InboxStateHolder
    participant LI as LoadInbox
    participant C as AttentionCache
    participant RF as RefreshInbox
    participant GW as AttentionGateway
    SH->>LI: invoke(scope)
    LI->>C: read(scope)
    opt cache has a feed
        C-->>LI: feed
        LI-->>SH: Cached(feed) shown with Updated generatedAt
    end
    LI->>RF: refresh first page (sort=decide, limit=50)
    RF->>GW: feed(scope, query) with read retries
    alt success
        GW-->>RF: feed with deskBadgeCount, nextCursor
        RF->>C: write(scope, feed)
        LI-->>SH: Fresh(feed)
    else failure
        LI-->>SH: Failed(error) and the cached feed stays on screen
    end
    SH->>LI: LoadMore(cursor) later, not cached
```

## Integration points

Ports listed in the data dictionary. Phase 1b implements them; contract suites make "the fake and the real adapter agree" a test.

## Architectural decisions

Recorded in `docs/architectural-decision-record.md` as they are made. Decided now: (1) sealed `Unsupported` branches for unknown server kinds; (2) device authentication is enforced inside the use case through the `Biometrics` port, never in the UI; (3) state holders take an injected `CoroutineScope` and a `Clock`, run use cases in that scope, and cancel it from a `close()` that the platform calls when the screen goes away; (4) `Secret.reveal()` is callable only from the API auth plugin, enforced by a Konsist rule. Sequence diagrams for slice 1a-2 are above; pause agent and polling refresh follow with slice 1a-3. Slice 1a-2 decisions: ADR-015 to ADR-020.
