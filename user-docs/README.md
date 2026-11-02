# User and developer guides

The app is under construction, so these guides are for a developer adopting the shared Kotlin core. The only end-user screen so far is the first-run address check on iPhone. These pages grow with the product.

| Guide | What it covers | State |
|---|---|---|
| [Getting started](./getting-started.md) | Prerequisites, clone, build, run the gates, build the XCFramework, run the iOS tests, run the demo graph from Kotlin and Swift | Current |
| [Configuration reference](./configuration-reference.md) | Gradle properties, version-catalog pins (including `paperclip-server`), scripts and their environment variables, graph flags, settings and factory fields with their validation rules, security rules | Current |
| [Usage examples](./usage-examples.md) | Verified snippets: create a graph, drive a state holder (Kotlin and Swift), add a factory on fakes, decide with device authentication cancelled or approved, start and stop the refresh policy, write a fake and a contract suite | Current |
| [Testing guide](./testing-guide.md) | TDD, fakes, contract suites, Konsist rules and Kover gates together; add a use case or a state holder end to end | Current |
| [Adding your first factory](./first-factory.md) | The address step, the only built screen | Current; pairing is not built |
| Pairing, Tailscale and allowed hostnames | End-user setup | Planned with Phase 1b |
| App lock and security settings | End-user settings | Planned |

Reference documents: [product summary](../docs/product-summary.md), [product details](../docs/product-details.md), [technical details](../docs/technical-details.md), [architectural decision record](../docs/architectural-decision-record.md).
