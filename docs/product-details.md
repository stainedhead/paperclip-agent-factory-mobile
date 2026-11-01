# Product details

Screens and behaviour. Requirements are numbered in the [PRD](../paperclip-agent-factory-mobile-PRD.md); the design images below come from the [design mocks](../Paperclip-Remote-mobile-UI-design.html) and show demo data only. Real screenshots replace them as screens are built.

## Built so far

### First-run address check (screen 15, step 1)

FR-003 and FR-009. The address of the Paperclip server is checked as you type, by the shared Kotlin core:

- it must start with `https://`; `http://` is accepted only for `localhost` and `10.0.2.2` in debug builds;
- scheme and host are lower-cased and a trailing `/` is removed; a path prefix such as `/paperclip` and an explicit port are kept;
- user names and passwords, query strings and fragments are refused, with a message saying what to remove.

| Today (iPhone, running) | Planned (design mock) |
|---|---|
| <img src="images/ios-first-run.png" alt="The first-run screen as built today" width="240"> | <img src="images/design-first-run.png" alt="The first-run screen as designed" width="240"> |

### Inbox, Decision, Approval and Budget incident behaviour (shared core, slice 1a-2)

The shared Kotlin core now holds the rules for screens 1, 2, 3 and 12; the iPhone and Android screens that draw them come in Phase 2. Behaviour a screen can rely on:

- **Inbox (FR-030 to FR-035).** Cards are grouped Decide now (decide-by due today or overdue, UTC days), This week and Whenever; severity only orders cards inside a group. The badge is the server's `deskBadgeCount`. Filter chips come from the server's counts per source kind. Inline Approve and Reject (Reject asks for a note) appear on approvals the server marks resolvable; Keep paused and Raise and resume appear on hard-stop budget alerts, and a raise opens the Budget incident sheet. Anything else opens its detail screen or the web app. Snooze offers an hour, tonight (until 20:00), tomorrow 08:00 and next Monday 08:00 in the phone's time zone; Dismiss and Snooze can be undone for five seconds. The list shows the cached feed first with "Updated <time>", and offline every action is disabled with a reason.
- **Decision (FR-040 to FR-044).** Every option shows what it will do before you choose. Destructive options, and any option that cancels an issue tree, are marked and ask for Face ID or your device PIN. Required inputs are checked before anything is sent. After deciding, each effect shows executed, skipped or failed. A decision that was decided, expired or cancelled elsewhere shows that final state and no options.
- **Approval (FR-050, FR-051).** The payload is shown per type. Approve and Reject work from pending and from revision requested; Request revision only from pending and needs a note; Reject needs a note and device authentication.
- **Budget incident (FR-052).** Keep paused is open to operators and owners. Raise and resume is owner only, needs an amount above the observed spend, device authentication, and shows the server's reason if it refuses.
- **Viewers** see everything and can change nothing. A tap while an action is already running is ignored, and a timed-out action is never sent twice: the screen reads the item again and shows what really happened.

## Designed, not built yet

| Screen | Image |
|---|---|
| Inbox: what needs a decision now (FR-030 to FR-035) | <img src="images/design-inbox.png" alt="Inbox design mock" width="200"> |
| Decision: every option's effects shown before you choose (FR-040 to FR-044) | <img src="images/design-decision.png" alt="Decision design mock" width="200"> |
| Factories: your Paperclip instances and their key status (FR-001 to FR-008) | <img src="images/design-factories.png" alt="Factories design mock" width="200"> |
| Manage factories: reorder and remove, with a confirmation that needs Face ID (FR-006) | <img src="images/design-manage-factories.png" alt="Manage factories design mock" width="200"> |
| Edit factory: name, address, default company, your roles, certificate pin (FR-001, FR-016, FR-017) | <img src="images/design-edit-factory.png" alt="Edit factory design mock" width="200"> |
| Settings and profile: device name, app lock, per-factory entries (FR-014, FR-017) | <img src="images/design-settings.png" alt="Settings design mock" width="200"> |

The rest of the screens (Approval, Issue, Factory home, Agents, Run viewer, Work list, notifications) are in the PRD's screen inventory.
