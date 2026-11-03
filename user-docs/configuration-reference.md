# Configuration reference

Every build and configuration knob that exists today. There is no end-user configuration file: the app's own settings live in the `AppSettings` and `Instance` types and are persisted by adapters that Phase 1b adds. Until then they live in memory in the fakes.

Last revised in the Phase 2i review-fix round (branch `feat/phase-2i-iphone-ui`); not re-checked line by line against the code.

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
| `scripts/paperclip-test-server.sh` | `up`, `down`, `logs`, `dump`, `health` | `PAPERCLIP_SERVER_VERSION` (overrides the catalog pin, used for the nightly `latest` job); `PAPERCLIP_TEST_PORT` (default `3199`) | Starts or stops `docker/paperclip-test/compose.yml`: Paperclip in `authenticated` mode, exposure `private`, bound to `127.0.0.1`, no LLM keys. `up` fails unless `/api/health` reports `authenticated`. `down` deletes its data. |
| `scripts/contract-smoke.sh` | optional base URL (default the test server) | `PAPERCLIP_TEST_PORT` (default `3199`) | Checks the pinned server's anonymous `/api/health` fields and the pairing challenge create and poll shapes. Run by `contract.yml`. |
| `scripts/test-build-kit.sh` | none | none | Checks the failure modes of `build-kit.sh` and `ios-test.sh` without Gradle or Xcode. |
| `scripts/check-adr-identifiers.py` | optional ADR numbers (default 18 22 23 24) | none | Fails when a backticked identifier in those ADRs' Decision paragraph is not in `shared/`. |

## Graph creation

| Call | Parameter | Effect |
|---|---|---|
| `PaperclipCore.createGraph(allowInsecureLocal: Boolean)` | `allowInsecureLocal` | `true` only in debug builds. It lets `http://localhost` and `http://10.0.2.2` pass address validation. Everything else must be `https`. Empty fakes-backed graph (no factories). |
| `PaperclipCore.createDemoGraph()` | none | Fakes seeded with the demo data set; insecure local addresses are not allowed. |

A live-versus-demo parameter does not exist yet; it arrives with the Phase 1b adapters.

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

Read and written only through the `SecretStore` port. Keys are `instance/{instanceId}/{name}` with name `boardToken`, `clientCertificate` or `clientCertificatePassword`. `Secret.toString()` prints `Secret(***)`. `SecretPolicy.requireUserPresence` selects biometric protection at rest. Editing a factory's address deletes its whole secret namespace (ADR-012). The real Keychain and Keystore implementations are Phase 1b; today `FakeSecretStore` is in memory.

## Security rules in force

| Rule | Where it is enforced |
|---|---|
| Instance addresses must be `https`; `http` only for `localhost` and `10.0.2.2` in debug builds | `BaseUri`, `ValidateInstanceAddress` |
| `local_trusted` and unknown server modes are refused | `HealthReport.refusal`, `AddInstance` |
| Destructive actions need device authentication before any server call | `DeviceAuthPolicy`, `DeviceAuthGuard`; Konsist `DeviceAuthRulesTest` |
| Mutations are never retried; reads retry three times with backoff | `retryingRead`; mutation use cases |
| `Secret.reveal()` is called only from infrastructure, fakes and `Secret` | Konsist `PortAndSecretRulesTest` |
| Fixtures use obviously fake tokens (`pcp_board_TESTONLY…`) | convention; secret scanning (`security.yml`, gitleaks) |
