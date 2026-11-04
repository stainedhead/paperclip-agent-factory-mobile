# Getting started

For a developer adopting the shared Kotlin core of Paperclip Agent Factory Mobile. There is no end-user app to install yet. The iPhone app has every screen (Inbox, Decision, Approval, Budget incident, Factory home, Agents, Agent detail, Run viewer, Work, Issue, Factories, Edit and Manage factories, Settings, first run), on the real Paperclip adapters (a launch without `-demo`; it starts at first run behind the app lock) or, in a Debug build, on the built-in demo data (launch argument `-demo`). What you can do today is build the core, run its quality gates and tests, and run the app either way.

Developed with Kotlin 2.4.21, Gradle 9.8.2 and Xcode 27.2 on macOS. This page was last revised in the Phase 2i review-fix round (branch `feat/phase-2i-iphone-ui`); the commands were run then, the older prose was not re-checked line by line.

## Prerequisites

| Need | Why | Notes |
|---|---|---|
| macOS | Kotlin/Native iOS targets and Xcode only build on macOS | The shared JVM and Android-host tests also run on Linux (`shared.yml`). |
| JDK 21 | Gradle toolchain | Do not set `JAVA_HOME`. The Gradle daemon JVM is pinned in `gradle/gradle-daemon-jvm.properties` and the foojay resolver downloads it if missing. |
| Android SDK | Every shared module has an Android library target, so Gradle needs an SDK to configure it | Put the path in a git-ignored `local.properties` at the repository root: `sdk.dir=/Users/you/Library/Android/sdk`. compileSdk 37, minSdk 28. |
| Xcode (developed with 27.2) | iOS framework, simulator tests | Install an iPhone simulator runtime. |
| `brew install swiftlint xcodegen` | Swift lint and Xcode project generation | `swift-format` ships with Xcode (`xcrun swift-format`). |
| Docker (optional) | Only for the throwaway Paperclip test server | Not needed for any test that exists today. |

Gradle runs one build at a time on a machine; do not start two in parallel.

## Clone and build

```bash
git clone <repository-url> paperclip-agent-factory-mobile
cd paperclip-agent-factory-mobile
git checkout feat/phase-1a-shared-contracts
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # adjust the path
./gradlew :shared:umbrella:jvmTest                            # quick check: JVM tests of the umbrella and everything it depends on
```

## Run the gates

All commands run from the repository root.

```bash
./gradlew spotlessApply        # fix formatting (ktlint) before you commit
./gradlew check lint           # the full gate: spotless, detekt, Konsist, tests on JVM + Android host + iOS simulator, Kover, Android Lint
```

Individual gates:

| Command | Runs |
|---|---|
| `./gradlew spotlessCheck` | ktlint formatting |
| `./gradlew detekt` | static analysis, zero issues allowed |
| `./gradlew :shared:umbrella:konsistTest` | the architecture tests (count in docs/technical-details.md) |
| `./gradlew allTests` | `commonTest` on JVM, Android host and iOS simulator |
| `./gradlew :shared:<module>:jvmTest` | fast loop for one module (`domain`, `application`, `presentation`, `testing`, `umbrella`) |
| `./gradlew koverVerifyCore koverVerifyPresentation` | the 90% coverage gates (domain + application; presentation). `koverVerify` alone checks nothing meaningful. |
| `./gradlew lint` | Android Lint |

Coverage numbers: `rm -rf build/reports/kover && ./gradlew koverXmlReportCore koverXmlReportPresentation -q`, then read `build/reports/kover/reportCore.xml` and `reportPresentation.xml`. Numbers for this branch are in `docs/technical-details.md` (Tests and coverage).

## Build the Android app and run the device tests

```bash
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease   # builds the app (release is unsigned)
./gradlew :androidApp:installDebug                               # installs on the running emulator or device
```

`check` never needs a device. The instrumented tests (Keystore, connectivity, persistence, live graph) run only on an emulator or a phone, one module at a time:

```bash
"$ANDROID_HOME/emulator/emulator" -avd Pixel_9_Pro -no-window -no-audio -no-snapshot &   # or start it from Android Studio
"$ANDROID_HOME/platform-tools/adb" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
./gradlew :shared:infrastructure:securestore:connectedAndroidDeviceTest
./gradlew :shared:infrastructure:platform:connectedAndroidDeviceTest
./gradlew :shared:infrastructure:persistence:connectedAndroidDeviceTest
./gradlew :shared:umbrella:connectedAndroidDeviceTest
```

Device test method names are camelCase: DEX 039 (our minSdk 28) rejects spaces in method names, so the backticked sentence names used in `commonTest` do not compile into an instrumented test. A module opts in to device tests by having a `src/androidDeviceTest` directory.

## Build the XCFramework and run the iOS tests

```bash
./scripts/build-kit.sh debug          # or: release. Builds PaperclipKit.xcframework into iosApp/Frameworks (git-ignored)
swiftlint --strict iosApp
xcrun swift-format lint --recursive --strict iosApp/Sources iosApp/Tests iosApp/Packages/PaperclipKit/Package.swift
./scripts/ios-test.sh                 # xcodegen generate, then xcodebuild test on the first available iPhone simulator
```

`build-kit.sh` keeps the previous framework when the Gradle build fails or its output is missing, and writes `iosApp/Frameworks/PaperclipKit.configuration` (`debug` or `release`); `ios-test.sh` stops with a clear message if the framework is missing. `./scripts/test-build-kit.sh` checks those failure modes without Gradle or Xcode. `IOS_SIMULATOR_ID=<udid> ./scripts/ios-test.sh` picks a specific simulator (`xcrun simctl list devices available`). To run the app, open `iosApp/iosApp.xcodeproj` (generated by `ios-test.sh`) and run the `iosApp` scheme; you get the first-run address screen described in [first-factory.md](./first-factory.md).

## Run the demo graph

`PaperclipCore.createDemoGraph()` builds the whole app core on fakes seeded with the design mocks' data: Home factory, Acme Labs, PAP-1182, Engineer 1, nine Inbox cards. No server is contacted. Both examples below are checked by tests in the repository.

**From a Kotlin test.** Any test in `shared/umbrella/src/commonTest` can do this; [usage-examples.md](./usage-examples.md) has complete, compiled snippets (read the Inbox, filter it, dismiss a card). The repository's own version is `DemoGraphTest.kt`:

```bash
./gradlew :shared:umbrella:jvmTest --tests '*DemoGraph*'
```

**From Swift.** After `./scripts/build-kit.sh`, this is a verbatim excerpt of `iosApp/Tests/iosAppTests/DemoGraphInteropTests.swift` (case `inboxShowsTheMockCountsAndDecideNowCards`), which passes under `./scripts/ios-test.sh`. `eventually` is the small polling helper at the top of that file; `home` is `"demo-home"` and `acme` is `"company-acme"`.

```swift
@MainActor
struct DemoAttentionInteropTests {
    private let graph = PaperclipCore.shared.createDemoGraph()

    @Test func inboxShowsTheMockCountsAndDecideNowCards() async {
        let inbox = graph.attention.createInboxStateHolder(instanceId: home, companyId: acme)
        #expect(await eventually { inbox.state.value.phase == .content })

        let state = inbox.state.value
        #expect(state.badgeCount == 3)
        #expect(state.totalCount == 9)
        #expect(state.sections.first?.group == .decideNow)
        #expect(state.sections.first?.items.map(\.title).first == "Ship the billing migration to production?")
        #expect(state.refreshIntervalSeconds == 15)
        inbox.onIntent(intent: InboxIntentFilterSelected(kind: SourceKindUi.decision))
        #expect(await eventually { inbox.state.value.selectedFilter == .decision })
        inbox.close()
    }
}
```

The iPhone app opens the real graph through `LiveIosGraph.openLiveGraph`; `-demo` (Debug builds) runs the app on the demo graph instead. To see a screen's logic on demo data from Swift, use the demo graph in a test as above.

## Where next

| You want to | Read |
|---|---|
| Copy working code | [usage-examples.md](./usage-examples.md) |
| Know every knob and rule | [configuration-reference.md](./configuration-reference.md) |
| Add a use case, a fake or a screen | [testing-guide.md](./testing-guide.md) |
| Understand the structure | [../docs/technical-details.md](../docs/technical-details.md) |
| See what each screen does | [../docs/product-details.md](../docs/product-details.md) |
| Contribute | [../AGENTS.md](../AGENTS.md), [../INTENT.md](../INTENT.md) |
