# User and developer guides

The iPhone and Android apps connect to real Paperclip servers (Phase 1b adapters) and also have a debug-only demo factory. Developer guides come first, then end-user guides; the end-user guides have an "On Android" section where Android differs.

| Guide | What it covers | State |
|---|---|---|
| [Getting started](./getting-started.md) | Prerequisites, clone, build, run the gates, build the XCFramework, run the iOS tests, run the demo graph from Kotlin and Swift; build and run the Android app, emulator, device tests, benchmark, release smoke | Current |
| [Configuration reference](./configuration-reference.md) | Gradle properties, version-catalog pins (including `paperclip-server`), scripts and their environment variables, graph flags, Android launch extras, build types and signing variables, settings and factory fields with their validation rules, security rules | Current |
| [Usage examples](./usage-examples.md) | Snippets that mirror the tests: create a graph, drive a state holder (Kotlin and Swift), add a factory on fakes, decide with device authentication cancelled or approved, start and stop the refresh policy, write a fake and a contract suite | Current |
| [Testing guide](./testing-guide.md) | TDD, fakes, contract suites, Konsist rules and Kover gates together; add a use case or a state holder end to end | Current |
| [First run and adding a factory](./first-factory.md) | Address, name, check, demo factory; Android notes | Current |
| [Pairing](./pairing.md) | Approving this phone (Safari sheet on iPhone, Custom Tab on Android) | Current; not yet run against a live server |
| [Tailscale and allowed hostnames](./tailscale-and-hostnames.md) | Reaching a private factory, Tailscale on iPhone and Android | Current |
| [Adding and managing factories](./managing-factories.md) | Factories tab, Manage, Edit | Current |
| [App lock and security](./app-lock.md) | Lock, Face ID, fingerprint and screen lock, destructive actions, Recents | Current |
| [Key expiry and re-pairing](./key-expiry-and-repairing.md) | Expired keys, and Android key invalidation | Current |
| [Troubleshooting](./troubleshooting.md) | Common problems, with an Android table | Current |

Reference documents: [product summary](../docs/product-summary.md), [product details](../docs/product-details.md), [technical details](../docs/technical-details.md), [architectural decision record](../docs/architectural-decision-record.md).
