# Demo walkthrough

A step-by-step walkthrough of every feature in the brief, which you can follow yourself in about
10 minutes on a real device (or an Android emulator). It uses the [Quick start](../README.md#quick-start)
setup: the local backend and the **Firestore emulator**, so every payment appears in the
**Emulator UI** at the same time as in the app.

> **Real Firestore instead:** the same steps work against your own Firebase project
> ([Option B](../README.md#option-b-your-own-firebase-project)) with the security rules deployed
> (`firebase deploy --only firestore:rules`) and a debug build switched to it, as described in
> [Switching a debug build to real Firestore](../README.md#switching-a-debug-build-to-real-firestore).
> Use the Firebase console wherever this walkthrough says Emulator UI.

## Before you start

**Services and app** (as in the [Quick start](../README.md#quick-start))

1. Placeholder Firebase config:
   `cp androidApp/google-services.json.example androidApp/google-services.json`
2. Firestore emulator (terminal 1):
   `firebase emulators:start --only firestore --project demo-cashi`
3. Backend (terminal 2):
   `./gradlew :server:installDist && server/build/install/server/bin/server`
4. Connect **only one device** (unplug the phone when using an emulator, and vice versa):
   connecting a second device can reset the first one's port forwards, and the app then shows
   "No connection…" although the server is up. Forward the ports with `./scripts/device-setup.sh`,
   check that `adb reverse --list` shows `tcp:8080` and `tcp:8085`, then install the app:
   `./gradlew :androidApp:installDebug`
5. Open the Emulator UI at http://127.0.0.1:4000 → **Firestore** → `payments` (the collection
   appears after the first payment).

**For scenes 5 and 6 (optional tools, see [Prerequisites](../README.md#prerequisites))**

- Appium (terminal 3): `appium`. Compile the e2e tests once so the run starts quickly:
  `./gradlew -p e2e compileTestKotlin`
- Cucumber report: `./gradlew :shared:jvmTest`, which writes
  `shared/build/reports/cucumber/cucumber.html`.
- JMeter report: `./perf/run-load-test.sh` (with the backend running), which writes
  `perf/report/index.html`. The report folder is gitignored, so a fresh clone doesn't have one.

**Demo data**

| Use | Value |
|---|---|
| Happy path | `alice@example.com`, `10.50`, USD |
| Emulator UI document (scene 2) | `bob@example.com`, `2550`, EUR |
| Invalid recipient | `not-an-email`, `10.50` |
| Invalid amount | `alice@example.com`, `1,500` |
| Retry | `carol@example.com`, `5.00`, USD |

## Walkthrough

### 1. The architecture in one picture

**Look at:** the Overview diagram in [ARCHITECTURE.md](ARCHITECTURE.md#overview), then open the app
on the Send screen.

**What this shows:** a Kotlin Multiplatform payment app. All business logic (validation, money,
the use cases and the API client) is shared code; the Android app is Compose UI on top, and the
Ktor backend reuses the same validator. Payments are stored in Firestore and the history updates in
real time.

### 2. Happy path and real-time History

**Do:**
1. On Send, enter `alice@example.com`, `10.50`, currency **USD**, tap **Send**.
2. The dialog shows "Send $10.50 to alice@example.com?" → tap **Send**.
3. The snackbar shows **"Payment sent"**; the form clears. In the Emulator UI, the new document
   appears in `payments` with `recipientEmail`, `amountMinor: 1050`, `currency: "USD"` and the
   server `timestamp`. The server log shows `POST /payments -> 201`.
4. Tap **History**: the payment is listed with its timestamp and **"$10.50 USD"**.
5. Keep History open on the device. In the Emulator UI, **`payments` → Add document**:
   - Document ID: leave it empty (auto-ID)
   - Fields, with **exactly these types**:

     | Field | Type | Value |
     |---|---|---|
     | `recipientEmail` | string | `bob@example.com` |
     | `amountMinor` | number | `2550` |
     | `currency` | string | `EUR` |
     | `timestamp` | timestamp | now (current date and time) |

   - **Save.** The row "bob@example.com — €25.50 EUR" appears at the **top** of History
     immediately, without a refresh.

**What this shows:** the app validates in shared code, asks for confirmation, calls the backend,
and only then writes to Firestore with a server timestamp. History is a live snapshot listener: a
document added anywhere shows up instantly.

> Note: the Emulator UI writes directly (security rules don't apply to it). The app reads
> `amountMinor` as an **integer**; enter `2550` with no decimal point. If the row doesn't appear,
> the number was stored as a floating-point value and the app skipped the document as malformed
> (by design, D28 in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md#persistence)).

### 3. Validation errors

**Do:**
1. Tap **Send** (tab). Enter `not-an-email` and `10.50`, tap **Send**: **"Invalid recipient"**
   under the email field, no dialog.
2. Fix the email (`alice@example.com`), enter `1,500` as the amount, tap **Send**:
   **"Invalid amount"** under the amount field.

**What this shows:** errors appear on the field that's wrong. And `1,500` is rejected rather than
read as 1.50: the parser counts decimals in the text, so a thousands separator can't silently
change the amount.

### 4. Network failure and retry → exactly one payment

**Do:**
1. **Stop the server** (Ctrl+C in its terminal; wait until it has exited).
2. On Send, enter `carol@example.com`, `5.00`, USD → **Send** → **Send** in the dialog:
   **"No connection. Check your network and try again."** The form keeps its values.
3. **Restart the server** (same command) and wait for "Responding at http://127.0.0.1:8080".
4. Tap **Send** → **Send** again without changing anything: **"Payment sent"**.
5. In the Emulator UI: **exactly one** new document for `carol@example.com`.

**What this shows:** the app keeps the same idempotency key while the payment is unchanged, the
server deduplicates by that key, and Firestore documents are write-once. So a retry at any point
produces exactly one payment.

### 5. Appium end-to-end tests

**Do:** run `./gradlew -p e2e test --rerun` (`--rerun` makes Gradle run the tests even if nothing
changed since the last run). The device is driven on screen: typing a unique `e2e-…@example.com`,
selecting **EUR**, the confirmation dialog, "Payment sent", History. The second test enters
`not-an-email` and checks "Invalid recipient". The new `e2e-…` document appears in the Emulator UI;
Gradle reports **2 tests passed**. Details: [e2e/README.md](../e2e/README.md).

**What this shows:** Appium UiAutomator2 tests on the device. They find elements by stable resource
ids and wait explicitly, with no sleeps.

### 6. Test reports

**Open:**
1. The **Cucumber** report (`shared/build/reports/cucumber/cucumber.html`): 3 of 3 scenarios
   passed (payment creation, invalid recipient, transaction listing).
2. The **JMeter** dashboard (`perf/report/index.html`): 5 concurrent users, 100 requests, 0 errors,
   p95 of about 10 ms on the owner's machine ([sample results](../perf/README.md#sample-results)).

**What this shows:** the BDD scenarios run against the real shared use cases. The load test sends
5 concurrent users with a new idempotency key per request, so every request is really processed.

### 7. Where to read more

Setup, every test command, the architecture and all design decisions are in the
[README](../README.md) and the `docs` folder: [ARCHITECTURE.md](ARCHITECTURE.md) and
[DESIGN_DECISIONS.md](DESIGN_DECISIONS.md). They also cover production hardening (authentication,
a shared idempotency store, server-owned writes) and what an iOS app would reuse.

## Checklist

| Feature | Expected result |
|---|---|
| Send payment (scene 2) | "Payment sent"; a new document in `payments` with `amountMinor: 1050` |
| Real-time History (scene 2) | The payment is listed; a document added in the Emulator UI appears at the top immediately |
| Invalid recipient (scene 3) | "Invalid recipient" under the email field, no dialog |
| Invalid amount (scene 3) | `1,500` → "Invalid amount" under the amount field |
| Network failure and retry (scene 4) | "No connection…", then "Payment sent"; exactly one `carol@example.com` document |
| Appium (scene 5) | 2 tests passed; a new `e2e-…` document |
| Cucumber (scene 6) | 3 of 3 scenarios passed |
| JMeter (scene 6) | 5 users, 100 requests, 0 errors |

## Afterwards

Stop the server, the Firestore emulator (its data is discarded) and Appium.