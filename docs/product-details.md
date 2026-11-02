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
