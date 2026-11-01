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

Phase 1a (shared contracts) is built on the branch `feat/phase-1a-shared-contracts`, awaiting pull requests. In plain terms:

- **Built and tested:** the shared Kotlin core for 15 screens: setting up and managing factories, the Inbox, decisions, approvals, budget incidents, issues and their interaction forms, agents, runs and the factory overview. It enforces roles, asks for Face ID or a PIN before destructive actions, never retries a change, and keeps working from its last data when offline. It runs on built-in demo data with no server.
- **Built UI:** one iPhone screen, the first-run address check.
- **Not built:** the iPhone and Android screens beyond that (Phase 2), the connection to a real Paperclip server and secure storage on the device (Phase 1b), notifications (Phase 3), and the Android app.

So the app cannot yet be installed and used to manage a factory. Developers can build the core, run its tests and drive every screen's logic on the demo data; see [user-docs/getting-started.md](../user-docs/getting-started.md) and [product-details.md](./product-details.md). The plan is in [specs/261101-iphone-app/plan.md](../specs/261101-iphone-app/plan.md).
