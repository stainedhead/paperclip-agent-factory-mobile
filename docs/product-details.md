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

### Work, Issue, Agents, Run and Factory home behaviour (shared core, slice 1a-3)

The shared core holds the rules for screens 4, 5, 6, 7, 10 and 11. Behaviour a screen can rely on:

- **Work list (FR-060, FR-062).** Saved filters All open, Blocked, Critical and high, In review and Assigned to me, plus search that finds an issue by its identifier in any spelling (`PAP-1182`, `pap1182`). The list loads in pages. Critical and high is filtered on the phone because the server cannot filter by priority. An operator or owner can create an issue; a viewer cannot.
- **Issue (FR-061 to FR-063).** Header with status, priority, assignee, project, parent and subtasks; tabs Thread (comments and interactions in time order), Plan (read only), Runs and Files. Comments can ask to reopen a closed issue or interrupt the running agent. Status and assignee can be changed and a subtask added. Interactions are forms: suggested tasks (all selected to start), questions, a confirmation, a checkbox confirmation with its range, and per-item verdicts with a reason for reject. A form says what is missing before anything is sent. A card addressed to someone else, already resolved, or of a kind the app does not know shows why it is read only; unknown kinds say "open in the web app".
- **Agents (FR-070, FR-071).** Needs attention (error, budget-paused, waiting for approval), Running, Paused and Idle, with the lifecycle state shown when it is not ready. Pause asks you to confirm and then to authenticate; Resume is not offered for a budget pause (the budget incident lifts it); Run now needs no prompt. Setting an agent's monthly budget is owner only.
- **Agent detail (FR-072).** Role, who it reports to, its current issue (from its run in progress), recent runs and month spend against budget.
- **Run viewer (FR-075).** Events and a log tail that follows the end until you scroll up. Cancel asks you to confirm and to authenticate; a viewer cannot cancel; a run that finished meanwhile shows its final state.
- **Factory home (FR-078).** Tiles for agents, tasks, month spend against budget, pending approvals and budget incidents; the runs in progress; rolling spend for 5 hours, 24 hours and 7 days.
- **Everywhere.** Offline, screens keep what they showed and every action is disabled with the reason; a second tap while an action runs is ignored; a timed-out action is sent once and the screen reads the item again.

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
