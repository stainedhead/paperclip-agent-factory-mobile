# Data dictionary: Phase 2a Android UI

**Purpose:** the types, schemas and interfaces this phase adds or relies on. Status: draft; filled in as the work lands.

## Existing types relied on (verified, see research.md)

| Type | Values / shape |
|---|---|
| `SecretWriteResult` | `Stored`, `Failed` |
| `SecretReadResult` | `Found(secret)`, `Missing`, `Invalidated`, `Denied` |
| `SecretPolicy` | `requireUserPresence: Boolean` |
| `SecretKey` | `instanceId`, `name: SecretName`; `path` = `instance/{id}/{wire}` |
| `BiometricResult` | `Approved`, `Cancelled`, `Failed`, `LockedOut`, `Unavailable` |
| `AuthReason` | `UnlockApp`, `ConfirmDestructiveAction` |

## Kotlin types added (shared)

| Type | Module / source set | Notes |
|---|---|---|
| `KeystoreSecretStore(context)` | securestore / androidMain | implements `SecretStore` including `namespaces()` |
| `KeystoreBackend` (internal) | securestore / androidMain | seam: key generation, encrypt/decrypt, record file I/O; fake in androidHostTest |
| `BiometricPromptBiometrics(host)` | platform / androidMain | implements `Biometrics` |
| `AuthHost` | platform / androidMain | `suspend fun current(): FragmentActivity?`; registered by `MainActivity` while resumed |
| `ConnectivityManagerConnectivity(context)` | platform / androidMain | implements `Connectivity`; `close()` unregisters |
| `AndroidLivePlatform` (internal) | umbrella / androidMain | implements `LivePlatform` |
| `AndroidLiveGraph.openLiveGraph(context, allowInsecureLocal, pairingBrowser, settings)` | umbrella / androidMain | returns `AndroidGraphOpen` |
| `AndroidGraphOpen` | umbrella / androidMain | sealed: `Opened(graph)`, `Unavailable(cause)` |
| `AndroidLiveSettings` | umbrella / androidMain | prompt texts, key alias (mirrors `IosLiveSettings`) |
| `:androidApp:benchmark` | `com.android.test` module | `StartupBenchmark`, frame-timing fling, baseline profile generator (T8.6) |

## Kotlin types added (androidApp)

| Type | Notes |
|---|---|
| `PaperclipApplication`, `MainActivity` (`FragmentActivity`) | one graph per process; one Activity |
| `AppEnvironment`, `AppGraphProvider` (Demo, FirstRunDemo, Live), `LaunchConfiguration` | graph + startup notice; demo via Intent extra in debug |
| `HolderViewModel<S, I>` | owns one state holder; closes it in `onCleared()` |
| `AppRoute` (`@Serializable`) | five top-level destinations plus pushed routes and sheets |
| `*Presentation.kt`, `*Labels.kt` | pure formatting; key enum to `@StringRes` |
| `CustomTabsPairingBrowser` | opens the approval URL in a Custom Tab; `finished()` on resume or cancel |
| Diagnostics report | typed redacted values only: app version, compatibility result, last graph-open outcome class, last Keystore/biometric error class, last pairing outcome |

## Resources and configuration

| Item | Notes |
|---|---|
| `res/values/strings.xml` | one string per `ErrorKey`, `ActionsDisabledReason`, `AddressIssue`, `StartupNotice`, `DeviceAuthIssue`, label enums |
| `AndroidManifest.xml` | permissions INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC only; `allowBackup=false`; `dataExtractionRules` |
| `debug/res/xml/network_security_config.xml` | cleartext to localhost and 10.0.2.2 only |
| `stability_config.conf` | marks shared presentation packages stable |
| `proguard-rules.pro` | R8 keep rules for the release build |

## Persisted data

Secrets: Keystore-wrapped records (IV + tag + ciphertext) in `noBackupFilesDir`, one per `SecretKey`. Factory list and cache: existing SQLDelight `paperclip.db` in `noBackupFilesDir`. No new SQLDelight tables.

## API request/response types

None added; the REST gateways are unchanged.
