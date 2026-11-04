# Product summary

Paperclip runs a "factory" of AI agents. Agents propose; people decide. When the person who decides is away from their desk, the factory waits.

**Paperclip Agent Factory Mobile** puts the board operator's decisions, oversight and emergency controls in their pocket, for every Paperclip instance they run, without weakening the factory's security.

## What you can do

1. **Decide** what agents are waiting on: approvals, decisions, questions and budget stops, usually in two taps from a notification.
2. **Know** whether the factory is healthy: what is running, failing, blocked or overspending.
3. **Intervene** safely: pause an agent, cancel a run, redirect work with a comment, each behind Face ID or a fingerprint when it is destructive.

You can connect several Paperclip instances ("factories") and switch between them and between companies. Setting up and managing those connections is part of the app. Configuring agents and administering the instance stays in the Paperclip web app.

## Who it is for

The owner or operator of one or more Paperclip instances, at home or in the cloud, away from the web app.

## Platforms

iPhone (iOS 17 and later) first, then Android (9 and later). Both use one shared Kotlin core, so they behave the same.

## Where it stands

- **Built and tested:** the shared Kotlin core and the whole iPhone app: setting up and managing factories, the Inbox, decisions, approvals, budget incidents, Factory home, agents and runs, issues and their interaction forms, settings and app lock. It enforces roles, asks for Face ID before destructive actions, never retries a change, keeps working from its last data when offline, and supports Dynamic Type, dark mode, VoiceOver labels, reduced motion and 44 pt targets.
- **Runs on demo data:** every screen works end to end on a built-in demo factory (no server).
- **Not built:** notifications (Phase 3) and the Android app. The connection to a real Paperclip server and key storage in the Keychain are built (Phase 1b) but have not yet been exercised end to end from the app against a live server.

So the app cannot yet manage a real factory; it can be built, run and tried on the demo factory. See [user-docs/getting-started.md](../user-docs/getting-started.md) and [product-details.md](./product-details.md).

<img src="images/inbox.png" alt="The Inbox in demo mode" width="220">
