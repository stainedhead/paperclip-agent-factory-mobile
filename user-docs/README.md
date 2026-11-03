# User and developer guides

The iPhone app is built on a demo factory; connecting to a real server arrives with Phase 1b. Developer guides come first, then end-user guides.

| Guide | What it covers | State |
|---|---|---|
| [Getting started](./getting-started.md) | Prerequisites, clone, build, run the gates, build the XCFramework, run the iOS tests, run the demo graph from Kotlin and Swift | Current |
| [Configuration reference](./configuration-reference.md) | Gradle properties, version-catalog pins (including `paperclip-server`), scripts and their environment variables, graph flags, settings and factory fields with their validation rules, security rules | Current |
| [Usage examples](./usage-examples.md) | Verified snippets: create a graph, drive a state holder (Kotlin and Swift), add a factory on fakes, decide with device authentication cancelled or approved, start and stop the refresh policy, write a fake and a contract suite | Current |
| [Testing guide](./testing-guide.md) | TDD, fakes, contract suites, Konsist rules and Kover gates together; add a use case or a state holder end to end | Current |
| [First run and adding a factory](./first-factory.md) | Address, name, check, demo factory | Current |
| [Pairing](./pairing.md) | Approving this phone | Current (demo only) |
| [Tailscale and allowed hostnames](./tailscale-and-hostnames.md) | Reaching a private factory | Current |
| [Adding and managing factories](./managing-factories.md) | Factories tab, Manage, Edit | Current |
| [App lock and security](./app-lock.md) | Lock, Face ID, destructive actions | Current |
| [Key expiry and re-pairing](./key-expiry-and-repairing.md) | Expired keys | Current |
| [Troubleshooting](./troubleshooting.md) | Common problems | Current |

Reference documents: [product summary](../docs/product-summary.md), [product details](../docs/product-details.md), [technical details](../docs/technical-details.md), [architectural decision record](../docs/architectural-decision-record.md).
