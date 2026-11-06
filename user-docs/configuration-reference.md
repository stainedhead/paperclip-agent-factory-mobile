# Configuration reference

Every build and configuration knob that exists today. There is no end-user configuration file: the app's own settings live in the `AppSettings` and `Instance` types and are persisted by adapters that Phase 1b adds. Until then they live in memory in the fakes.

The Android sections were checked against the code in Phase 2a (2026-11-04); the rest was last revised in the Phase 2i review-fix round and not re-checked line by line.

## Build

### Gradle properties (`gradle.properties`)

| Property | Value | Purpose |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8` | Gradle daemon heap |
| `kotlin.daemon.jvmargs` | `-Xmx3g -XX:MaxMetaspaceSize=1g` | Kotlin compiler daemon heap |
| `org.gradle.parallel` | `true` | Parallel project execution |
| `org.gradle.caching` | `true` | Build cache |
| `kotlin.code.style` | `official` | |
| `kotlin.mpp.enableCInteropCommonization` | `true` | Kotlin Multiplatform setting |
| `android.useAndroidX` | `true` | |

There are no custom `-P` properties. The root build reads none.

### Local, git-ignored files

| File | Content |
|---|---|
| `local.properties` | `sdk.dir=<path to the Android SDK>`. Required: all shared modules have an Android target. |
| `iosApp/Frameworks/PaperclipKit.xcframework` | Output of `scripts/build-kit.sh` |
| `iosApp/iosApp.xcodeproj` | Generated from `iosApp/project.yml` by XcodeGen |

### Toolchain and version catalog (`gradle/libs.versions.toml`)

Never hard-code a version in a build script; change the catalog. The only literal version outside it is the foojay resolver plugin in `settings.gradle.kts`.

| Key | Pin | Used for |
|---|---|---|
| `kotlin` | 2.4.21 | Kotlin Multiplatform |
| `agp` | 9.5.0 | Android Gradle plugin, KMP library plugin (`androidHostTest`, not `androidUnitTest`) |
| `skie` | 0.10.16 | Swift interop (Flow to AsyncSequence, sealed to enum) |
| `coroutines` | 1.11.0 | kotlinx-coroutines (core in domain, test in `commonTest`) |
| `datetime` | 0.7.1 | kotlinx-datetime |
| `koin` | 4.2.2 | Dependency injection in the umbrella |
| `kotest` | 6.2.6 | Assertions in tests |
| `turbine` | 1.2.1 | Flow testing |
| `konsist` | 0.18.1 | Architecture rules |
| `junit` | 6.1.3 | Runs Konsist |
| `spotless` / `ktlint` | 8.10.4 / 1.8.0 | Formatting |
| `detekt` | 1.23.8 | Static analysis (`config/detekt/detekt.yml`) |
| `kover` | 0.9.11 | Coverage |
| **`paperclip-server`** | **2026.1023.0** | The Paperclip release that contract tests and fixtures are pinned to. `scripts/paperclip-test-server.sh` reads it. Bumping it is its own pull request with refreshed fixtures. |

Other fixed settings: Gradle 9.8.2 (wrapper), JDK 21 toolchain, compileSdk 37, minSdk 28, "warnings are errors" for Kotlin (`pafm.kmp-library.gradle.kts`), Swift 6.0 with `SWIFT_STRICT_CONCURRENCY: complete`, iOS deployment target 17.0, iPhone only (`iosApp/project.yml`). Formatting: `.editorconfig` (4 spaces, 120 columns, ktlint official style); `.swiftlint.yml`; `.swift-format`.

### Coverage gates

Defined in the root `build.gradle.kts`: variants `core` (packages `domain` + `application`) and `presentation`, each with a verify rule of at least 90% line coverage. Raising the threshold is a code change in that file; lowering it is not allowed.

### Scripts

| Script | Arguments | Environment | Does |
|---|---|---|---|
| `scripts/build-kit.sh` | `debug` (default) or `release` | none | Runs `:shared:umbrella:assemblePaperclipKit<Debug\|Release>XCFramework`, checks the output exists, and swaps it into `iosApp/Frameworks/` only when the copy is complete (the old framework survives a failure); writes `PaperclipKit.configuration` next to it. Exits 1 with a message when Gradle fails or the output is missing. |
| `scripts/ios-test.sh` | none | `IOS_SIMULATOR_ID` (a simulator UDID; default: first available iPhone on the newest iOS runtime) | `xcodegen generate`, then `xcodebuild test` for the `iosApp` scheme with `CODE_SIGNING_ALLOWED=NO`. Needs the XCFramework first (exits 1 with "run scripts/build-kit.sh first" otherwise). |
| `scripts/paperclip-test-server.sh` | `up`, `down`, `logs`, `dump`, `health`, `invite` | `PAPERCLIP_SERVER_VERSION` (overrides the catalog pin, used for the nightly `latest` job); `PAPERCLIP_TEST_PORT` (default `3199`) | Starts or stops `docker/paperclip-test/compose.yml`: Paperclip in `authenticated` mode, exposure `private`, bound to `127.0.0.1`, no LLM keys. `up` fails unless `/api/health` reports `authenticated`. `down` deletes its data. `invite` creates a bootstrap CEO invite in the running container and prints only its token (the contract test accepts it to create the first admin). |
| `scripts/contract-smoke.sh` | optional base URL (default the test server) | `PAPERCLIP_TEST_PORT` (default `3199`) | Checks the pinned server's anonymous `/api/health` fields and the pairing challenge create and poll shapes. Run by `contract.yml`. |
| `scripts/test-build-kit.sh` | none | none | Checks the failure modes of `build-kit.sh` and `ios-test.sh` without Gradle or Xcode. |
| `scripts/check-adr-identifiers.py` | optional ADR numbers (default 18 22 23 24) | none | Fails when a backticked identifier in those ADRs' Decision paragraph is not in `shared/`. |

## Android app

### Launch extras (debug and `releaseDemo` builds)

Passed with `adb shell am start -n com.stainedhead.paperclipmobile/.android.MainActivity --ez <name> true` (or `--es`, `--ei`). A shipping `release` build ignores all of them (`LaunchConfiguration.from(..., allowsDemoControls)`), so a store build cannot be steered onto fakes.

| Extra | Type | Effect |
|---|---|---|
| `demo` | boolean | The demo graph: the design mocks' data on shared fakes, no server. |
| `demo_first_run` | boolean | Fakes that start with no factory and approve every pairing: the first-run flow. "Try the demo factory" switches to the demo graph. |
| `demo_biometrics` | string | Comma-separated answers of the device-owner prompt on the demo graph, in order, from `cancelled`, `failed`, `approved`; after the list is used up the prompt approves again. For example `cancelled,approved`. |
| `demo_inbox_cards` | int | With `demo`: how many generated cards the Inbox holds besides the mock's nine (the frame-timing benchmark uses 200). |

No extra reaches a real factory: without `demo` the app opens the live graph.

### Build types (`androidApp/build.gradle.kts`)

| Build type | Application id | Minified (R8) | Signed with | Demo entry | Use |
|---|---|---|---|---|---|
| `debug` | `com.stainedhead.paperclipmobile` | no | debug key | yes (`BuildConfig.DEBUG`) | Development; cleartext allowed to `localhost` and `10.0.2.2` only (debug network security config). |
| `release` | `com.stainedhead.paperclipmobile` | yes, with resource shrinking | the `upload` key when `PAFM_UPLOAD_STORE_FILE` is set, else the debug key (not a distribution artifact) | no | What would ship. No cleartext, no network security config, `Log` calls stripped. |
| `releaseDemo` | `...paperclipmobile.demo` | yes | debug key | yes | The shipping code plus the demo entry, to smoke-test the R8 output without a server. Never distributed. |
| `benchmark` | `...paperclipmobile.benchmark` | yes | debug key | yes | Macrobenchmark target; profileable. Never distributed. |
| `nonMinified` | `...paperclipmobile.benchmark` | no | debug key | yes | Records the baseline profile with real class names. |

`versionName` is the catalog's `android-app` (0.1.0). `versionCode` is `git rev-list --count HEAD`: use a full clone (`fetch-depth: 0` in CI), or every build is 1. `minSdk` 28, `compileSdk` and `targetSdk` 37. Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `USE_BIOMETRIC` and nothing else.

### Environment variables

| Variable | Used by | Meaning |
|---|---|---|
| `PAFM_UPLOAD_STORE_FILE`, `PAFM_UPLOAD_STORE_PASSWORD`, `PAFM_UPLOAD_KEY_ALIAS`, `PAFM_UPLOAD_KEY_PASSWORD` | `:androidApp:assembleRelease` | The upload keystore. All four are read from the environment at build time and never committed (`*.jks` and `*.keystore` are git-ignored). Without `PAFM_UPLOAD_STORE_FILE` the release build is signed with the debug key (a local build warns; a build with `CI` set fails unless `PAFM_ALLOW_DEBUG_SIGNED_RELEASE=1`). Setting the store file without the other three fails the build everywhere. |
| `PAFM_ALLOW_DEBUG_SIGNED_RELEASE` | `:androidApp:assembleRelease`, CI | `1` says a debug-signed release is meant (a smoke build). `android.yml` sets it. Without it, a CI build with no upload key fails in `verifyReleaseSigning`. |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | the Android scripts | SDK location for `adb` and `aapt2` (default `$HOME/Library/Android/sdk`). |
| `ANDROID_SERIAL` | the Android scripts | Pick a device when several are connected. |
| `PAFM_BENCHMARK_ALLOW_EMULATOR` | `scripts/run-benchmarks.sh` | `1` (default): record on an emulator, as a regression guard. `0`: refuse emulators (the Pixel 6 target needs a real Pixel 6). |
| `PAPERCLIP_TEST_PORT` | `scripts/android-live-pairing.sh`, `paperclip-test-server.sh` | Host port of the pinned test server (default 3199). |

### Android catalog entries (`gradle/libs.versions.toml`)

| Key | Pin | Used for |
|---|---|---|
| `android-app` | 0.1.0 | `versionName` |
| `compose-bom` | 2026.09.00 | Compose artifacts (the BOM pins them) |
| `androidx-activity` / `-lifecycle` / `-navigation` | 1.13.0 / 2.11.0 / 2.10.2 | Compose activity, ViewModel and refresh lifecycle, type-safe navigation |
| `androidx-biometric` | 1.1.0 | `BiometricPrompt` (stable; 1.4 is alpha) |
| `androidx-browser` | 1.10.0 | Custom Tabs for pairing and the web-app door |
| `androidx-splashscreen`, `-core`, `-profileinstaller` | 1.2.0, 1.19.1, 1.4.1 | Splash, core KTX, baseline profile installation |
| `androidx-benchmark`, `-uiautomator` | 1.5.0, 2.4.1 | The `:androidApp:benchmark` module |
| `androidx-test-runner` / `-core` / `-ext-junit` / `-orchestrator` / `-espresso` | 1.7.0 / 1.7.0 / 1.3.0 / 1.6.1 / 3.7.0 | Connected tests (Espresso is pinned because the 3.5 that Compose resolves breaks on API 37) |
| `roborazzi`, `robolectric`, `junit4` | 1.77.0, 4.17, 4.13.2 | Screenshot tests and JUnit 4 + Robolectric (`robolectric.properties` pins `sdk=36`) |

`CatalogRulesTest` (Konsist) fails on any literal dependency or plugin version in a `*.gradle.kts`.

### Android scripts

| Script | Environment | Does |
|---|---|---|
| `scripts/android-release-smoke.sh live\|demo` | `ANDROID_SERIAL`, `ANDROID_HOME` | Builds, installs and launches the R8 release APK (`demo` uses `releaseDemo`); fails on a crash, a wrong permission set or a missing screen. |
| `scripts/android-live-smoke.sh` | `ANDROID_SERIAL`, `ANDROID_HOME` | Live launch opens the real graph, no board key in files or log, backup refused, no cleartext in the release manifest. |
| `scripts/android-live-pairing.sh` | `ANDROID_SERIAL`, `PAPERCLIP_TEST_PORT` | Pairs the debug app with the pinned server in Docker (never run). |
| `scripts/run-benchmarks.sh` | `ANDROID_SERIAL`, `PAFM_BENCHMARK_ALLOW_EMULATOR` | The Macrobenchmarks; prints medians. |
| `scripts/generate-baseline-profile.sh` | `ANDROID_SERIAL` | Re-records `androidApp/src/main/baseline-prof.txt`. |

## Graph creation

| Call | Parameter | Effect |
|---|---|---|
| `PaperclipCore.createGraph(allowInsecureLocal: Boolean)` | `allowInsecureLocal` | `true` only in debug builds. It lets `http://localhost` and `http://10.0.2.2` pass address validation. Everything else must be `https`. Empty fakes-backed graph (no factories). |
| `PaperclipCore.createDemoGraph()` | none | Fakes seeded with the demo data set; insecure local addresses are not allowed. |

`PaperclipCore.createLiveGraph(allowInsecureLocal, pairingBrowser)` builds the real graph on iOS; on Android the app calls `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings)` (`allowInsecureLocal` is `BuildConfig.DEBUG`). See [technical-details.md](../docs/technical-details.md#the-live-graph-phase-1b-5a).

## App settings (`AppSettings`)

Device-wide preferences. Changed through `UpdateAppSettings` / `SettingsStateHolder`.

| Field | Type | Default | Rule |
|---|---|---|---|
| `deviceName` | String | `"My phone"` (`AppSettings.DEFAULT_DEVICE_NAME`) | Trimmed; 1 to 60 characters (`MAX_DEVICE_NAME_LENGTH`); otherwise `InvalidDeviceName`. |
| `appLockEnabled` | Boolean | `true` | When on, the app asks for device authentication on a cold launch and after the idle period. |
| `idlePeriod` | `IdlePeriod` | `FiveMinutes` | `Immediately`, `OneMinute`, `FiveMinutes`, `FifteenMinutes`, `ThirtyMinutes`, `OneHour`. |
| `selectedInstanceId` | `InstanceId?` | `null` | The selected factory. |

There is deliberately no setting for destructive-action authentication. `SettingsState.destructiveActionAuthAlwaysOn` is always `true`.

## Factory configuration (`Instance`)

Non-secret configuration of one Paperclip instance. It holds no secret.

| Field | Rule |
|---|---|
| `id` | `InstanceId`, generated by `IdGenerator`. |
| `displayName` | Trimmed; 1 to 40 characters (`Instance.MIN_NAME_LENGTH`, `MAX_NAME_LENGTH`); unique among factories ignoring case (`InstanceNameIssue`: `Empty`, `TooLong`, `Taken`). |
| `baseUri` | A `BaseUri`; see below. |
| `deploymentMode`, `exposure` | From the server probe (`local_trusted` / `authenticated` / unknown; `private` / `public` / unknown). |
| `authMethod` | `PairedBoardKey` or `ManualBoardKey`. |
| `boardKeyId`, `keyExpiresAt` | Metadata about the stored key, not the key. |
| `defaultCompanyId`, `lastCompanyId` | The company to open is the last used, else the default. |
| `pins` | Zero or more `SpkiPin`: the base64 SHA-256 of a server public key, exactly 43 base64 characters plus `=`. Anything else does not parse. |
| `status` | `SignedIn`, `SignedOut` or `NeedsRePairing`. A new factory starts `SignedOut` until paired. |
| `sortOrder` | Set by add (next free) and by reorder. |

### Address rules (`BaseUri.parse`)

| Rule | Error (`BaseUriError`) |
|---|---|
| Input is trimmed; empty is rejected | `Empty` |
| `://` must be present | `MissingScheme` |
| Scheme must be `http` or `https` | `UnsupportedScheme` |
| `http` is allowed only when `allowInsecureLocal` is true **and** the host is `localhost` or `10.0.2.2` | `InsecureHttp` |
| A host is required: dot-separated labels of 1 to 63 ASCII letters, digits and hyphens (no leading or trailing hyphen, no empty label, no trailing dot, at most 253 characters); punycode (`xn--`) is accepted, Unicode is not; a host ending in digits must be a canonical IPv4 address (four parts 0 to 255, no leading zeros); a bracketed IPv6 literal must be well formed | `MissingHost`, `InvalidHost` |
| A port, if present, is 1 to 65535 | `InvalidPort` |
| No user name or password (`@`) | `UserInfoNotAllowed` |
| No query string or fragment | `QueryOrFragmentNotAllowed` |
| The path (after the host) has no whitespace, control character or backslash, no empty segment (`//`), no `.` or `..` segment (also as `%2e`), and every `%` is a valid two-digit escape that does not hide a control character, `/` or `\` | `InvalidPath` |

Normalisation: scheme and host lower-cased, trailing `/` removed; an explicit port and a path prefix are kept. The REST API root is `{baseUri}/api`.

### Server refusal (`HealthReport.refusal`)

A factory is added only if `GET /api/health` shows `deploymentMode == authenticated`, the bootstrap is not pending, and the status is `ok`. Otherwise `ProbeRefusal`: `LocalTrusted` (always refused), `UnknownDeploymentMode` (fails closed), `BootstrapPending`, `NotReady`.

## Secrets

Read and written only through the `SecretStore` port. Keys are `instance/{instanceId}/{name}` with name `boardToken`, `clientCertificate` or `clientCertificatePassword`. `Secret.toString()` prints `Secret(***)`. `SecretPolicy.requireUserPresence` selects biometric protection at rest. Editing a factory's address deletes its whole secret namespace (ADR-012). The real implementations are `KeychainSecretStore` (iOS) and `KeystoreSecretStore` (Android); the fakes use `FakeSecretStore` in memory.

## Security rules in force

| Rule | Where it is enforced |
|---|---|
| Instance addresses must be `https`; `http` only for `localhost` and `10.0.2.2` in debug builds | `BaseUri`, `ValidateInstanceAddress` |
| `local_trusted` and unknown server modes are refused | `HealthReport.refusal`, `AddInstance` |
| Destructive actions need device authentication before any server call | `DeviceAuthPolicy`, `DeviceAuthGuard`; Konsist `DeviceAuthRulesTest` |
| Mutations are never retried; reads retry three times with backoff | `retryingRead`; mutation use cases |
| `Secret.reveal()` is called only from infrastructure, fakes and `Secret` | Konsist `PortAndSecretRulesTest` |
| Fixtures use obviously fake tokens (`pcp_board_TESTONLY…`) | convention; secret scanning (`security.yml`, gitleaks) |
