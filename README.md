# Cashi Payment App

A Kotlin Multiplatform (KMP) payment app. Android is the implemented platform; all business logic
(models, validation, use cases, API client, persistence contracts) lives in the `shared` module so
another platform such as iOS can reuse it. A Ktor backend in this repo exposes `POST /payments` and
reuses the same shared models and validator.

**Contents:** [Quick start](#quick-start) · [Where to look first](#where-to-look-first) ·
[Tech stack](#tech-stack-and-versions) · [Repository layout](#repository-layout) ·
[Prerequisites](#prerequisites) · [Setup](#setup) · [Run](#run) · [Running tests](#running-tests) ·
[Troubleshooting](#troubleshooting) · [API](#api) · [Architecture](#architecture) ·
[Cross-platform potential](#cross-platform-potential) · [Design decisions](#design-decisions) ·
[Production hardening](#production-hardening) · [Demo](#demo) ·
[How this project was built](#how-this-project-was-built) ·
[Project tour](docs/PROJECT_TOUR.md)

**Features**
- **Send payment:** recipient email, amount and currency (USD, EUR), validated in shared code. A
  confirmation dialog shows the formatted amount; on confirm the app calls the backend and, if it
  succeeds, saves the transaction to Firestore. The result appears as "Payment sent" or as an error
  such as "Invalid recipient".
- **Transaction history:** a scrollable list from Firestore (recipient, amount with currency code,
  timestamp), updated in real time by a snapshot listener.
- **Backend:** `POST /payments` validates, deduplicates by `Idempotency-Key` and returns the
  processed payment or a JSON error.
- **Tests:** shared unit tests, Cucumber BDD scenarios, server tests, ViewModel tests, on-device
  tests against the Firestore emulator, a JMeter load test and Appium end-to-end tests.

## Quick start

Runs everything locally against the **Firestore emulator**, with a **placeholder Firebase config**
(`demo-cashi`). No Firebase account, login or real project is needed. Prerequisites:
[below](#prerequisites).

```bash
# 1. Placeholder Firebase config (the real file is gitignored)
cp androidApp/google-services.json.example androidApp/google-services.json

# 2. Firestore emulator with the matching demo project            (terminal 1)
firebase emulators:start --only firestore --project demo-cashi

# 3. Backend on 127.0.0.1:8080                                     (terminal 2)
./gradlew :server:installDist && server/build/install/server/bin/server

# 4. Device over USB: forward 8080 + 8085, then install the app    (terminal 3)
./scripts/device-setup.sh
./gradlew :androidApp:installDebug
```

Send a payment on the device. It appears in History and in the Emulator UI at
http://127.0.0.1:4000 (project `demo-cashi`). Then [run the tests](#running-tests).

To use a real Firebase project instead, see [Setup](#setup).

## Where to look first

New to the code? The [project tour](docs/PROJECT_TOUR.md) maps every module and file, lists every
test and follows one payment from the Send screen to Firestore and back. These are the files to read
first:

| File | Why |
|---|---|
| [`PaymentValidator.kt`](shared/src/commonMain/kotlin/cashi/payment/oways/shared/domain/validation/PaymentValidator.kt) | The one validator used by both the app and the server (email → currency → amount, first failure wins). |
| [`AmountParser.kt`](shared/src/commonMain/kotlin/cashi/payment/oways/shared/domain/validation/AmountParser.kt) | Form amount parsing that counts fraction digits in the text, so `"1,500"` can't silently become 1.50. |
| [`MinorUnits.kt`](shared/src/commonMain/kotlin/cashi/payment/oways/shared/domain/money/MinorUnits.kt) | Exact `Double` → `Long` cents conversion at the API boundary, with the business limit. |
| [`SendPaymentUseCase.kt`](shared/src/commonMain/kotlin/cashi/payment/oways/shared/domain/usecase/SendPaymentUseCase.kt) | The shared send flow: validate → backend → history; a history failure never reads as "payment failed". |
| [`FirestorePaymentStore.kt`](shared/src/androidMain/kotlin/cashi/payment/oways/shared/data/firestore/FirestorePaymentStore.kt) | Firestore behind the `PaymentStore` interface: idempotent save under write-once rules, real-time `callbackFlow`. |
| [`SendPaymentViewModel.kt`](androidApp/src/main/kotlin/cashi/payment/oways/ui/send/SendPaymentViewModel.kt) | Thin ViewModel: one UiState, confirmation, and idempotency-key reuse while the payment content is unchanged. |
| [`InMemoryIdempotencyStore.kt`](server/src/main/kotlin/cashi/payment/oways/server/idempotency/InMemoryIdempotencyStore.kt) | Server-side deduplication: atomic check-and-create, replay vs. mismatch, TTL and LRU bound. |

## Tech stack and versions

| | Version |
|---|---|
| Kotlin (Multiplatform, JVM, Compose compiler, serialization) | 2.4.20 |
| Android Gradle Plugin / Gradle | 9.1.1 / 9.5.1 |
| JDK | 21 (bytecode targets JVM 17) |
| Android | minSdk 24, compile/target SDK 37 |
| Jetpack Compose BOM / Material 3 / Navigation Compose | 2026.09.00 / BOM-managed / 2.10.2 |
| Ktor (client: OkHttp engine; server: Netty) | 3.6.0 |
| kotlinx.serialization / kotlinx.coroutines | 1.12.0 / 1.11.0 |
| Koin | 4.2.2 |
| Firebase BoM (Firestore) / google-services plugin | 35.1.0 / 4.5.0 |
| Tests: JUnit 6 (shared `jvmTest`, server, e2e) | 6.1.3 |
| Tests: JUnit 4 (androidApp unit and device tests; shared Android host and device tests) | 4.13.2 |
| Tests: Cucumber / Turbine / Ktor MockEngine | 8.0.4 / 1.2.1 / 3.6.0 |
| Tools: Firebase CLI | 15.34.1 |
| Tools: JMeter | 5.6.3 |
| Tools: Appium / UiAutomator2 driver / java-client | 3.8.0 / 8.7.0 / 11.0.0 |

All library versions are managed in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Repository layout

| Path | Contents |
|---|---|
| `shared/` | KMP library (targets: `android`, `jvm`). Domain, use cases, Ktor client, `PaymentStore` interface, Koin `sharedModule()` + `expect fun platformModule()`. Firestore only in `androidMain`. |
| `androidApp/` | Jetpack Compose UI, ViewModels, Koin setup (`CashiApplication`), `BuildConfig` connection settings. |
| `server/` | Ktor backend (JVM), depends on `shared` through its `jvm()` target. Port `8080`. |
| `perf/` | JMeter load test for `POST /payments` (plain folder; see [perf/README.md](perf/README.md)). |
| `e2e/` | Appium UI tests: a standalone Gradle build, not part of the app build (`./gradlew -p e2e test --rerun`; see [e2e/README.md](e2e/README.md)). |
| `docs/` | [Project tour](docs/PROJECT_TOUR.md), [Architecture](docs/ARCHITECTURE.md), [Design decisions](docs/DESIGN_DECISIONS.md), [Demo script](docs/DEMO.md), [API contract](docs/API.md), [UI test ids](docs/TEST_TAGS.md). |
| `scripts/` | `device-setup.sh` / `.bat` (`adb reverse` for a real device). |
| `.run/` | Shared Android Studio / IntelliJ run configurations (**Server**, **Server tests**). |
| `firebase.json`, `.firebaserc`, `firestore.rules` | Firebase CLI config, emulator ports and the Firestore security rules. |

## Prerequisites

- JDK 21
- Android SDK with `platform-tools` (`adb`) on your `PATH`, and `ANDROID_HOME` set
- Node.js and the Firebase CLI: `npm install -g firebase-tools` (tested with 15.34.1)
- An Android device with USB debugging enabled (or an Android emulator; see
  [Connection options](#connection-options))
- Only for the load test: JMeter 5.6.3 ([perf/README.md](perf/README.md))
- Only for the e2e tests: Appium 3.8.0 + UiAutomator2 8.7.0 ([e2e/README.md](e2e/README.md))

## Setup

### Option A: emulator only (reviewers, default)

What the [Quick start](#quick-start) does:

1. `cp androidApp/google-services.json.example androidApp/google-services.json`. The example
   uses Firebase's **`demo-` project convention** (`project_id: demo-cashi`) with placeholder keys,
   which the emulator accepts without any real project.
2. Start the emulator with **the same project id**:
   `firebase emulators:start --only firestore --project demo-cashi`
   (Firestore on `127.0.0.1:8085`, Emulator UI on http://127.0.0.1:4000). The emulator loads
   `firestore.rules` from `firebase.json`, so the security rules apply locally too.

**The emulator's project id must match the app's** (`project_id` in `google-services.json`).
Otherwise the app writes to and reads from a different project than the one shown in the Emulator
UI, and History stays empty.

### Option B: your own Firebase project

This is how the project owner runs it against the real project `cashi-demo`.

1. Download `google-services.json` for the Android app `cashi.payment.oways` from your Firebase
   project (Project settings → Your apps) into `androidApp/`. It's gitignored.
2. Set your project id in `.firebaserc` (it's `cashi-demo` in this repo).
3. Deploy the security rules: `firebase login`, then `firebase deploy --only firestore:rules`.
4. Start the emulator for everyday development: `firebase emulators:start --only firestore` (it
   uses the project from `.firebaserc`, which matches your `google-services.json`).
5. To use the **real** Firestore from a debug build, set `firestore.useEmulator=false` (see
   [Switching to real Firestore](#switching-a-debug-build-to-real-firestore)).

### `local.properties` keys (optional, all builds)

| Key | Default | Description |
|---|---|---|
| `api.baseUrl` | `http://127.0.0.1:8080` | Backend URL (`BuildConfig.API_BASE_URL`). |
| `firestore.useEmulator` | `true` | Debug builds use the local Firestore emulator. Set `false` to use the real project from `google-services.json`. **Release builds always use the real project**, whatever this says. |
| `firestore.emulatorHost` | `127.0.0.1:8085` | Emulator address as `host` or `host:port` (port defaults to `8085`). |

Invalid values fail the build with a clear message (`api.baseUrl` must start with `http://` or
`https://`; `firestore.useEmulator` must be `true` or `false`; `firestore.emulatorHost` must be
`host` or `host:port`). Rebuild after changing them.

## Run

### Backend (server)

The `server` module is a Ktor (Netty) app. It depends on `shared` through its `jvm()` target and
reuses the same `PaymentValidator`, money conversion and API models (`PaymentRequest`,
`PaymentResponse`, `ErrorResponse`) as the app, so client and server rules can't drift apart.

**Start it** (listens on `http://127.0.0.1:8080`):

| How | Command |
|---|---|
| Terminal (development) | `./gradlew :server:run`, stop with Ctrl+C (Gradle then reports exit value 143, which is normal) |
| Standalone (no Gradle in between; logs appear immediately; use this for JMeter) | `./gradlew :server:installDist`, then `server/build/install/server/bin/server` (Windows: `server\build\install\server\bin\server.bat`) |
| Android Studio / IntelliJ | Run configuration **Server** (shared in `.run/`) |

**Configuration** (environment variables):

| Variable | Default | Description |
|---|---|---|
| `SERVER_HOST` | `127.0.0.1` | Bind address. Localhost only by default, so the server isn't exposed on the LAN. |
| `SERVER_PORT` | `8080` | Port, e.g. `SERVER_PORT=9090 ./gradlew :server:run`. |

**Endpoints:** `GET /health` and `POST /payments`, with an optional `Idempotency-Key` header. Every
error returns `{"code","message"}`. The full contract (fields, rules, error codes, idempotency
semantics, curl examples) is in [docs/API.md](docs/API.md). Quick check:

```bash
curl -s http://127.0.0.1:8080/health
curl -s -X POST http://127.0.0.1:8080/payments -H "Content-Type: application/json" \
  -d '{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}'
```

### App on a real device

The backend and the Firestore emulator listen on `127.0.0.1`. `adb reverse` maps the device's own
`127.0.0.1:8080` and `127.0.0.1:8085` to this machine, so no LAN IP or firewall setup is needed.

1. Start the Firestore emulator ([Setup](#setup)) and the [backend](#backend-server).
2. Connect the device over USB, then run `./scripts/device-setup.sh`
   (Windows: `scripts\device-setup.bat`). With several devices connected, set `ANDROID_SERIAL` first.
3. Install the app: `./gradlew :androidApp:installDebug`.

Re-run the script whenever the device reconnects or the adb server restarts.

### Connection options

The backend URL and the Firestore emulator address are `BuildConfig` fields with defaults for a
real device via `adb reverse`. Override them in `local.properties` and rebuild:

| Setup | `local.properties` | Notes |
|---|---|---|
| Real device + `adb reverse` (default) | *(nothing)* | Run `scripts/device-setup.sh` after connecting the device (forwards `8080` and `8085`). |
| Android emulator | `api.baseUrl=http://10.0.2.2:8080`<br>`firestore.emulatorHost=10.0.2.2:8085` | `10.0.2.2` is the emulator's alias for the host machine. |
| Real device over Wi-Fi (LAN IP) | `api.baseUrl=http://192.168.1.20:8080`<br>`firestore.emulatorHost=192.168.1.20:8085` | Start the server with `SERVER_HOST=0.0.0.0`, set `"host": "0.0.0.0"` for the Firestore emulator in `firebase.json`, and allow the IP for cleartext (below). |

- **Trailing slash:** a URL with a path must end with `/` (e.g. `http://host/api/`). Without it the
  client resolves `payments` against the host root (`http://host/payments`).
- **Cleartext (plain HTTP)** is allowed only in **debug** builds and only for `127.0.0.1`,
  `localhost` and `10.0.2.2` (`androidApp/src/debug/res/xml/network_security_config.xml`). Release
  builds allow HTTPS only. To use a LAN IP, add it to the debug file:

  ```xml
  <domain includeSubdomains="false">192.168.1.20</domain>
  ```
- With the emulator, the app uses an in-memory Firestore cache, so restarting the emulator (which
  wipes its data) never leaves stale payments on the device. With the real project, the default
  persistent cache keeps the history available offline.

### Switching a debug build to real Firestore

For example, to show payments in the real Firebase console during a demo (needs
[Option B](#option-b-your-own-firebase-project)):

1. Add `firestore.useEmulator=false` to `local.properties`.
2. Rebuild and install: `./gradlew :androidApp:installDebug`.
3. To go back to the emulator, remove the line (or set it to `true`) and reinstall.

With `false`, payments are written to the real database of the project in `google-services.json`
(`cashi-demo` for the owner), subject to the deployed `firestore.rules`. The backend still runs
locally.

## Running tests

### No device needed

| Scope | Command | Expected |
|---|---|---|
| Shared logic: validation, money, API client (MockEngine), use cases, store contract on a fake | `./gradlew :shared:allTests` (= `:shared:jvmTest` + `:shared:testAndroidHostTest`) | All pass |
| Cucumber BDD scenarios (payment creation, transaction listing; part of `jvmTest`) | `./gradlew :shared:jvmTest` | 3 scenarios pass; report at `shared/build/reports/cucumber/cucumber.html` |
| Server (Ktor `testApplication`: routes, errors, idempotency) | `./gradlew :server:test`, or run configuration **Server tests** | All pass |
| App ViewModels and formatting (JVM unit tests) | `./gradlew :androidApp:testDebugUnitTest` | All pass |

The Cucumber features live in `shared/src/jvmTest/resources/features`. Their steps
(`shared/src/jvmTest/.../bdd/PaymentSteps.kt`) drive the real shared use cases and validator; only
the backend and Firestore are replaced by `FakePaymentApi` and `InMemoryPaymentStore`.

### On a device or with services (on demand, not part of `allTests` or `check`)

All need a connected device with `./scripts/device-setup.sh` done, except the load test.

| Scope | Also needs | Command | Expected |
|---|---|---|---|
| App navigation (Compose UI test) | — | `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cashi.payment.oways.navigation.NavigationTest` | 4 tests pass |
| App → backend smoke test (`ApiConnectivityTest`) | Backend | `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cashi.payment.oways.ApiConnectivityTest` | 1 test passes |
| App → Firestore emulator smoke test (`FirestoreEmulatorConnectivityTest`) | Firestore emulator | `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cashi.payment.oways.FirestoreEmulatorConnectivityTest` | 1 test passes (skipped if `firestore.useEmulator=false`) |
| All app device tests at once | Backend + Firestore emulator | `./gradlew :androidApp:connectedDebugAndroidTest` | All pass |
| Firestore store contract + the whole shared suite on the device | Firestore emulator | `./gradlew :shared:connectedAndroidDeviceTest` | All pass |
| End-to-end UI tests (Appium) | Backend, Appium, and the Firestore emulator (or an app built with `firestore.useEmulator=false`) | `./gradlew -p e2e test --rerun` ([e2e/README.md](e2e/README.md)) | 2 tests pass |
| Load test: 5 concurrent users on `POST /payments` (JMeter) | Backend only (no device) | `./perf/run-load-test.sh` ([perf/README.md](perf/README.md)) | "All requests passed."; sample results in perf/README.md |

Notes:
- **Firestore store contract:** the same `PaymentStoreContractTest` that runs against the in-memory
  fake runs here against `FirestorePaymentStore`. The emulator enforces `firestore.rules`, so the
  rules (validated create, no update) are tested too. Each test clears the emulator's data first,
  so don't point it at data you want to keep. The test APK has its own `INTERNET` permission and
  allows cleartext only to `127.0.0.1`. It uses the project id from `androidApp/google-services.json`
  (so it matches the emulator; `demo-cashi` if the file is absent). Override it with
  `-Pandroid.testInstrumentationRunnerArguments.firestoreProjectId=<id>`.
- **Smoke tests:** `ApiConnectivityTest` sends one real payment through OkHttp,
  `BuildConfig.API_BASE_URL`, the network security config and `adb reverse`.
  `FirestoreEmulatorConnectivityTest` builds the store exactly as the app does, saves a payment and
  waits for it in the live history. It never writes to a real project.
- **UI test ids:** every element the e2e tests need has a stable Android resource-id (Compose test
  tags exposed with `testTagsAsResourceId`). The full list and how to inspect them on a device are
  in [docs/TEST_TAGS.md](docs/TEST_TAGS.md).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Server: `java.net.BindException: Address already in use` | Another server is on the port. Find it with `lsof -nP -iTCP:8080 -sTCP:LISTEN` and stop it, or set `SERVER_PORT`. |
| App: "No connection. Check your network and try again." | Backend not running, or `adb reverse` missing: rerun `./scripts/device-setup.sh`. |
| "No connection…" although the server is up, right after connecting another device | Connecting a second device can reset the first one's `adb reverse` forwards. Disconnect the device you don't use (or set `ANDROID_SERIAL`), rerun `./scripts/device-setup.sh`, and check that `adb reverse --list` shows `tcp:8080` and `tcp:8085`. |
| `device-setup.sh`: "N devices are connected" | Several devices or emulators are connected; run it with one of the printed `ANDROID_SERIAL=… ./scripts/device-setup.sh` lines, or disconnect the others. |
| History stays empty / payments don't appear in the Emulator UI | Emulator not running, `adb reverse tcp:8085` missing, or the emulator's project id doesn't match `google-services.json` (reviewers: `--project demo-cashi`). |
| Gradle: "api.baseUrl must start with http:// or https://" (or a similar `firestore.*` message) | Fix the value in `local.properties` ([keys](#localproperties-keys-optional-all-builds)). |
| Device tests / e2e can't reach the device | Check `adb devices`; with several devices set `ANDROID_SERIAL`. |
| e2e: "App cashi.payment.oways is not installed" or `SessionNotCreatedException` after `connectedDebugAndroidTest` | The instrumented tests uninstall the app when they finish. Run `./gradlew :androidApp:installDebug` before `./gradlew -p e2e test --rerun`. |
| e2e or load-test issues | See the troubleshooting sections in [e2e/README.md](e2e/README.md) and [perf/README.md](perf/README.md). |

## API

The backend contract (request and response fields, validation rules, error codes, idempotency) is
in [docs/API.md](docs/API.md).

## Architecture

Full explanation with diagrams: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

- **`shared` (KMP: `android` + `jvm`)** holds everything that isn't UI. The **domain** (models,
  `PaymentValidator`, `AmountParser`, minor-unit money, `Outcome`, errors, use cases) has no
  platform dependencies. It defines the ports `PaymentApi` and `PaymentStore`, which the **data**
  layer implements (`KtorPaymentApi`; `FirestorePaymentStore` in `androidMain` only).
- **`androidApp`** is presentation only: stateless Compose content, thin ViewModels with one UiState
  per screen, and `CashiApplication`, the only place `BuildConfig` enters the Koin graph.
- **`server`** reuses the same validator, money conversion and API models through `shared`'s `jvm()`
  target, so client and server rules can't drift apart.
- **expect/actual** sits at the DI boundary: `expect fun platformModule()` provides the OkHttp engine
  and the Firestore store on Android, and is empty on the JVM. `PaymentStore` stays an interface,
  so every layer can be tested with fakes, and one contract test runs against both the fake and
  real Firestore.
- **Idempotency holds end to end:** the app reuses its key while the payment content is unchanged,
  the server replays or rejects repeated keys, and Firestore documents are write-once with the
  payment id as document id.

## Cross-platform potential

About three quarters of `shared`'s production code is platform-independent and would run on iOS
unchanged: the domain and validation, both use cases, the Ktor client with its error mapping, the
API contract, the `PaymentStore` contract tests and Koin's `sharedModule()`. An iOS app would add
the iOS targets, the Ktor Darwin engine, an iOS `actual fun platformModule()`, a `PaymentStore`
(GitLive firebase-kotlin-sdk, or Swift with the Firebase iOS SDK behind the interface), a split of
`shared` into `shared-core` (also used by the server) and `shared-presentation` (multiplatform
ViewModels), SKIE for Swift-friendly Flows, and SwiftUI screens. Details and a suggested order:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#cross-platform-potential-ios).

## Design decisions

All 49 decisions, with reasons, alternatives and trade-offs, are in
[docs/DESIGN_DECISIONS.md](docs/DESIGN_DECISIONS.md). The most important:

- **Money in minor units** (D8): `Double` only at the API boundary (per the brief), exact `Long`
  cents inside; more than 2 decimals is rejected; business limit 1,000,000.00 (D9).
- **Amount parsing counts digits in the text** (D10): `"1,500"` is rejected instead of silently
  becoming 1.50.
- **One shared validator** (D1, D11): the same `PaymentValidator` runs in the app and on the server,
  in the order email → currency → amount; `ValidatedPayment` proves input was checked (D12).
- **One error format** (D15): every error is `{"code","message"}`; clients branch on stable codes.
- **Idempotency end to end** (D20–D22, D25): the app reuses its key while the payment content is
  unchanged, the server replays or rejects repeated keys, and Firestore documents are write-once.
- **Firestore's server timestamp is the source of truth** for History (D26); an offline save
  reports `Unavailable` after 10 s instead of fire-and-forget (D27).
- **expect/actual at the DI boundary** (D4): `PaymentStore` stays an interface, so every layer is
  testable with fakes and one contract test runs against both the fake and real Firestore (D41).
- **minSdk 24 without `java.time`** (D34) and an **emulator-only reviewer path** (D40).

## Production hardening

- **Authentication:** the demo's Firestore rules allow open reads and validated creates. In
  production, enable Firebase Anonymous Auth (or real sign-in) and require `request.auth != null`
  in `firestore.rules`.
- **API key restriction:** restrict the Firebase Android API key in Google Cloud Console
  (APIs & Services → Credentials) to Android apps with package `cashi.payment.oways` and the app's
  signing certificate SHA-1.
- **Idempotency:** the demo accepts `POST /payments` without an `Idempotency-Key` (the app and the
  JMeter plan always send one). Production should require the header and keep keys in a shared
  store (Redis, or a database table with a unique constraint) instead of server memory.
- **Server-owned writes:** the app writes to Firestore after the API succeeds, as the brief asks.
  In production the backend should own this write, so a client can't record payments the backend
  never processed.
- **Release shrinking (R8):** enable `isMinifyEnabled` and `isShrinkResources` for release builds
  and verify them with a release smoke test against an HTTPS backend. kotlinx.serialization, Ktor,
  Koin and Firebase need no library-specific configuration (they ship their own R8 rules or, like
  Koin, don't rely on reflection), so mainly app-specific keep rules would be needed (D48).

## Demo

A 4-minute walkthrough on a real device, with each payment appearing in the real Firebase console at
the same time as in the app: happy path and real-time History, validation errors, a network-failure
retry that creates exactly one payment, the Appium tests running live, and the Cucumber and JMeter
reports. The script, checklist and recording setup are in [docs/DEMO.md](docs/DEMO.md).

**Video:** _link to be added after recording._

## How this project was built

I built this project with an AI coding assistant in a human-in-the-loop workflow. I wrote the
specification and made the architecture and design decisions; the assistant implemented them one
small step at a time. Each step was planned, reviewed and approved by me before any code was
written, and I checked the result before the next step started. Tests were written in every step,
and every feature was verified on a real Android device, with the end-to-end tests and the demo
also run on an Android emulator.
