# Demo script (about 4 minutes)

A narrated screen recording of the brief's features on a real device, with every payment
appearing in the **real Firebase console** (`cashi-demo`) at the same time as in the app.

The demo uses a **debug build with `firestore.useEmulator=false`**, so the app writes to the real
Firestore. How to switch is described in the README:
[Switching a debug build to real Firestore](../README.md#switching-a-debug-build-to-real-firestore).
The backend still runs locally.

## Before recording

**Build and services**

1. Use the real Firestore: add `firestore.useEmulator=false` to `local.properties`, then
   `./gradlew :androidApp:installDebug`.
2. Start the backend standalone (terminal 1):
   `./gradlew :server:installDist && server/build/install/server/bin/server`
3. Connect **only the device you'll demo with** (unplug the phone when demoing on an emulator, and
   vice versa): connecting a second device can reset the first one's port forwards, and the app
   then shows "No connection…" although the server is up. **Right before recording**, forward the
   ports with `./scripts/device-setup.sh` and check that `adb reverse --list` shows `tcp:8080` and
   `tcp:8085`.
4. Start Appium (terminal 2): `appium`. Compile the e2e tests once so the live run starts quickly:
   `./gradlew -p e2e compileTestKotlin`
5. The security rules are already deployed (`firebase deploy --only firestore:rules`).
6. Prepare the reports:
   - Cucumber: `./gradlew :shared:jvmTest`, then open `shared/build/reports/cucumber/cucumber.html`.
   - JMeter: open `perf/report/index.html` from the last run, or rerun `./perf/run-load-test.sh`.
7. Open the Firebase console on **Firestore → `payments`**. Optionally delete old test documents
   first, so the new ones are easy to spot.

**Phone**
- Do Not Disturb on, default font size, battery saver off, screen timeout long enough.
- App open on the Send screen.

**Recording: one QuickTime video with everything**

- Show the phone on the Mac with Android Studio's **device mirroring**: open the
  **Running Devices** window (View → Tool Windows → Running Devices) and start mirroring the
  connected phone. Alternative: `scrcpy` (optional, not required).
- Record the whole screen once with **QuickTime Player → File → New Screen Recording**, so the
  phone mirror, the Firebase console and the terminal are all in one video.
- Suggested window layout on one display:

  ```
  ┌───────────────┬──────────────────────────────────┐
  │               │  Firebase console                │
  │  Android      │  Firestore → payments            │
  │  Studio:      │  (top ~2/3 of the right side)    │
  │  Running      ├──────────────────────────────────┤
  │  Devices      │  Terminal                        │
  │  (phone       │  tabs: server · appium · gradle  │
  │  mirror)      │  (bottom ~1/3)                   │
  │  ~1/3 width   │                                  │
  └───────────────┴──────────────────────────────────┘
  ```
  Keep the server's log visible in the terminal during scenes 2–4 (each request is logged, e.g.
  `POST /payments -> 201`). Switch the right side to the browser tabs with the reports for scene 6.

**Demo data**

| Use | Value |
|---|---|
| Happy path | `alice@example.com`, `10.50`, USD |
| Console document (scene 2) | `bob@example.com`, `2550`, EUR |
| Invalid recipient | `not-an-email`, `10.50` |
| Invalid amount | `alice@example.com`, `1,500` |
| Retry | `carol@example.com`, `5.00`, USD |

**Dry run:** go through scenes 2–5 once before recording. In particular, check that the document
added in the console in scene 2 appears in History (see the note in that scene).

## Script

### 1. Intro (0:15)

**Show:** the Overview diagram in [ARCHITECTURE.md](ARCHITECTURE.md#overview), then the phone on
the Send screen.

**Say:** "A Kotlin Multiplatform payment app. All business logic — validation, money, the use cases
and the API client — is shared code; the Android app is Compose UI on top, and the Ktor backend
reuses the same validator. Payments are stored in Firestore and the history updates in real time."

### 2. Happy path and real-time History (1:00)

**Do:**
1. On Send, enter `alice@example.com`, `10.50`, currency **USD**, tap **Send**.
2. The dialog shows "Send $10.50 to alice@example.com?" → tap **Send**.
3. The snackbar shows **"Payment sent"**; the form clears. In the console, the new document appears
   in `payments` with `recipientEmail`, `amountMinor: 1050`, `currency: "USD"` and the server
   `timestamp`. In the terminal: `POST /payments -> 201`.
4. Tap **History**: the payment is listed with its timestamp and **"$10.50 USD"**.
5. Keep History open on the phone. In the console, **Firestore → `payments` → Add document**:
   - Document ID: **Auto-ID**
   - Fields, with **exactly these types**:

     | Field | Type | Value |
     |---|---|---|
     | `recipientEmail` | string | `bob@example.com` |
     | `amountMinor` | number | `2550` |
     | `currency` | string | `EUR` |
     | `timestamp` | timestamp | now (current date and time) |

   - **Save.** The row "bob@example.com — €25.50 EUR" appears at the **top** of History
     immediately, without a refresh.

**Say:** "The app validates in shared code, asks for confirmation, calls the backend, and only then
writes to Firestore with a server timestamp. History is a live snapshot listener: a document added
anywhere shows up instantly."

> Note: the console writes directly (security rules don't apply to it). The app reads
> `amountMinor` as an **integer**; enter `2550` with no decimal point. If the row doesn't appear in
> the dry run, the console stored the number as a floating-point value and the app skipped the
> document as malformed (by design, D28 in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md#persistence)).

### 3. Validation errors (0:30)

**Do:**
1. Tap **Send** (tab). Enter `not-an-email` and `10.50`, tap **Send**: **"Invalid recipient"** under
   the email field, no dialog.
2. Fix the email (`alice@example.com`), enter `1,500` as the amount, tap **Send**:
   **"Invalid amount"** under the amount field.

**Say:** "Errors appear on the field that's wrong. And `1,500` is rejected rather than read as 1.50:
the parser counts decimals in the text, so a thousands separator can't silently change the amount."

### 4. Network failure and retry → exactly one payment (0:45)

**Do:**
1. **Stop the server** (Ctrl+C in its terminal; wait until it has exited).
2. On Send, enter `carol@example.com`, `5.00`, USD → **Send** → **Send** in the dialog:
   **"No connection. Check your network and try again."** The form keeps its values.
3. **Restart the server** (same command) and wait for "Responding at http://127.0.0.1:8080".
4. Tap **Send** → **Send** again without changing anything: **"Payment sent"**.
5. In the console: **exactly one** new document for `carol@example.com`.

**Say:** "The app keeps the same idempotency key while the payment is unchanged, the server
deduplicates by that key, and Firestore documents are write-once. So a retry at any point produces
exactly one payment."

### 5. Appium end-to-end tests, live (0:50)

**Do:** in the terminal, `./gradlew -p e2e test --rerun` (`--rerun` makes Gradle run the tests even if nothing changed since the last run). The phone is driven on screen: typing a unique
`e2e-…@example.com`, selecting **EUR**, the confirmation dialog, "Payment sent", History. The second
test enters `not-an-email` and checks "Invalid recipient". The new `e2e-…` document appears in the
console; Gradle reports **2 tests passed**.

**Say:** "These are Appium UiAutomator2 tests on the real device. They find elements by stable
resource ids and wait explicitly, no sleeps."

### 6. Test reports (0:25)

**Show:**
1. The **Cucumber** report (`shared/build/reports/cucumber/cucumber.html`): 3 of 3 scenarios
   passed — payment creation, invalid recipient, transaction listing.
2. The **JMeter** dashboard (`perf/report/index.html`): 5 concurrent users, 100 requests, 0 errors,
   p95 ≈ 10 ms ([sample results](../perf/README.md#sample-results)).

**Say:** "The BDD scenarios run against the real shared use cases. The load test sends 5 concurrent
users with a new idempotency key per request, so every request is really processed."

### 7. Wrap-up (0:15)

**Show:** the README.

**Say:** "Setup, every test command, the architecture and all design decisions are in the README
and the `docs` folder: [ARCHITECTURE.md](ARCHITECTURE.md) and
[DESIGN_DECISIONS.md](DESIGN_DECISIONS.md). It also covers production hardening — authentication,
a shared idempotency store, server-owned writes — and what an iOS app would reuse."

## After recording

1. Back to the emulator: remove `firestore.useEmulator=false` from `local.properties`, then
   `./gradlew :androidApp:installDebug`.
2. Optionally delete the demo documents (`alice`, `bob`, `carol`, `e2e-…`) from the console.
3. Stop the server and Appium.
4. Add the video link to the README's [Demo](../README.md#demo) section.