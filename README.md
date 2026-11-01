# Paperclip Agent Factory Mobile

Native iPhone and Android apps for deciding, monitoring and intervening in one or more [Paperclip](https://docs.paperclip.ing) AI-agent instances from anywhere, through the Paperclip REST API. All non-UI code is one shared Kotlin Multiplatform core; the iPhone UI is SwiftUI and the Android UI will be Jetpack Compose.

**Status:** Phase 0 (foundations). The build, quality gates, CI and a one-screen iPhone app on the shared core exist. No Paperclip server is contacted yet. See [specs/261101-iphone-app/status.md](./specs/261101-iphone-app/status.md).

<img src="docs/images/ios-first-run.png" alt="The iPhone app's first-run screen: an address field and a disabled Continue button" width="240">

The planned first-run flow, from the [design mocks](./Paperclip-Remote-mobile-UI-design.html):

<img src="docs/images/design-first-run.png" alt="Design mock of the first-run setup screen" width="240">

## Documents

| File | What it holds |
|---|---|
| [INTENT.md](./INTENT.md) | Why the product exists and how to make trade-offs |
| [PRD](./paperclip-agent-factory-mobile-PRD.md) | Requirements, screens, release plan |
| [AGENTS.md](./AGENTS.md) | How we build: toolchain, TDD, Clean Architecture, security rules |
| [docs/product-summary.md](./docs/product-summary.md) | What the app does, in plain words |
| [docs/product-details.md](./docs/product-details.md) | Screens and behaviour, with images |
| [docs/technical-details.md](./docs/technical-details.md) | Modules, build, CI, how iOS consumes the Kotlin core |
| [docs/architectural-decision-record.md](./docs/architectural-decision-record.md) | Why we chose what we chose |
| [user-docs/](./user-docs/README.md) | Guides for people using the app |
| [specs/261101-iphone-app/plan.md](./specs/261101-iphone-app/plan.md) | The iPhone implementation plan |

## Build and run (macOS)

Needs a current Xcode (developed with 27.2), JDK 21 (Gradle finds or downloads it), and `brew install swiftlint xcodegen`. Android needs the Android SDK; put its path in a git-ignored `local.properties` (`sdk.dir=/Users/you/Library/Android/sdk`).

```bash
./gradlew check lint                 # format, static analysis, architecture rules, tests (JVM, Android host, iOS simulator), coverage
./scripts/build-kit.sh               # builds PaperclipKit.xcframework into iosApp/Frameworks
./scripts/ios-test.sh                # generates the Xcode project, runs the iOS unit and UI tests
open iosApp/iosApp.xcodeproj         # after ./scripts/ios-test.sh, run the "iosApp" scheme on a simulator
```

Demo mode (the whole app on fakes, no server) is being built in Phase 1a. `PaperclipCore.createGraph` already runs every slice 1a-1 state holder (first run, factories, manage, add/edit, settings, app shell) every slice 1a-2 state holder (Inbox, Decision, Approval, Budget incident, with the Acme Labs demo data) and every slice 1a-3 state holder (Work list, Issue with interactions, Agents, Agent detail, Run viewer, Factory home, reached as `graph.work`, `graph.agents`, `graph.runs` and `graph.factory`) on the shared fakes; the iOS app still renders only the first-run address step until the Phase 2i screens land.

### A Paperclip server for contract tests

```bash
./scripts/paperclip-test-server.sh up     # pinned Paperclip in Docker, authenticated mode, http://127.0.0.1:3199
./scripts/paperclip-test-server.sh down   # stop and delete its data
```

It uses host port 3199, so your own Paperclip on 3100 is untouched. The app refuses servers in `local_trusted` mode by design.

## Contributing

Read [INTENT.md](./INTENT.md) and [AGENTS.md](./AGENTS.md) first. Work is test-first (red, green, refactor), one logical change per pull request, Conventional Commits, and the docs change in the same pull request as the behaviour. A change to `shared/` and the UI that consumes it are separate pull requests, shared first.
