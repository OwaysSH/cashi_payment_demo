# Design decisions

Why the project is built the way it is. Each entry gives the **decision**, **why**, the **alternative
considered**, and where useful the **trade-off** and **where in code**. Entries are numbered (D1…)
so they can be referenced. How things work is in [ARCHITECTURE.md](ARCHITECTURE.md) and
[API.md](API.md); this document is about the reasons.

**Contents:** [Architecture](#architecture) · [Money and validation](#money-and-validation) ·
[API](#api) · [Idempotency](#idempotency) · [Persistence](#persistence) · [UI](#ui) ·
[Development and testing](#development-and-testing) · [Visual design](#visual-design) ·
[Known limitations](#known-limitations)

## Architecture

**D1. `shared` targets `android` and `jvm`; the server depends on it.**
Why: the server reuses the same `PaymentValidator`, money conversion and API models, so client and
server validation can never disagree. Alternative: a separate server-side copy of the rules.
Trade-off: `ktor-client` and Koin reach the server's classpath unused (see
[limitations](#known-limitations)).

**D2. UI in `androidApp` with Jetpack Compose, not Compose Multiplatform in `shared`.**
Why: the brief asks for Jetpack Compose, and keeping UI out of `shared` keeps the server's `jvm()`
target free of Compose. Alternative: CMP UI in `shared` (shareable with iOS, but heavier and it would
leak into the server). iOS would get SwiftUI ([ARCHITECTURE.md](ARCHITECTURE.md#cross-platform-potential-ios)).

**D3. ViewModels in `androidApp`, kept thin.**
Why: standard Jetpack ViewModels with `koinViewModel()`; they only orchestrate shared code and map
results to one UiState. Alternative: KMP ViewModels in `shared` now. Deferred to a future
`shared-presentation` module, so the server's dependency (`shared-core`) stays lean.

**D4. `PaymentStore` is an interface; expect/actual sits at the DI boundary.**
Decision: `expect fun platformModule(): Module` provides the OkHttp engine and the Firestore store on
Android and is empty on the JVM. Why: an interface can be faked in every test, and the server's JVM
target needs no Firestore actual. Alternative: `expect class PaymentStore` (as first planned),
harder to fake and forcing a JVM actual. Where: `shared/…/di/PlatformModule*.kt`.

**D5. Firebase only in `shared/androidMain`.**
Why: `commonMain` stays platform-independent and the server stays Firebase-free.
`androidApp` doesn't see Firebase types either: the factory returns `PaymentStore`
(`createFirestorePaymentStore`).

**D6. Koin, with `BuildConfig` entering only in `CashiApplication`.**
Why: shared code never reads build config; the app passes `PaymentConfig` (API base URL, emulator
address). Stateful objects (HTTP client, store) are singletons, use cases are factories. The
Firestore store must be a single because Firestore can be configured only once.

**D7. Concrete use cases; interfaces only at the ports.**
Why: `SendPaymentUseCase` and `ObservePaymentsUseCase` have no I/O of their own; tests use the real
use case with fake ports (`FakePaymentApi`, `InMemoryPaymentStore`), which keeps real logic under
test. Alternative: interfaces for every class (more indirection, nothing gained).

## Money and validation

**D8. `Double` at the API boundary, exact `Long` minor units inside.**
Why: the brief fixes `amount: Double` on the wire; money must never be calculated in floating point.
Amounts are converted once (`toMinorUnits`) with a tolerance check, and amounts with more than
2 decimals are rejected. Alternative: naive `(amount * 100).toLong()` (`0.29` → 28 cents).
Where: `shared/…/domain/money/MinorUnits.kt`.

**D9. Business limit of 1,000,000.00 per payment** (`MAX_AMOUNT_MINOR = 100_000_000`).
Why: a sensible single-payment limit for a P2P app that also keeps scaled values far inside the
range where the floating-point tolerance check is reliable. Larger amounts are an invalid amount.

**D10. `AmountParser` counts fraction digits in the text, before converting.**
Why: with `,` accepted as a decimal separator, `"1,500"` (meant as one thousand five hundred) would
otherwise become `1.5` and silently send 1.50. Three fraction digits are rejected as an invalid
amount; grouping separators, signs and exponents too. Alternative: counting digits after `toDouble()`
(impossible: `1.500` and `1.5` are the same `Double`).

**D11. Validation order: email → currency → amount; the first failure wins.**
Why: the amount check needs the currency's decimals, so the currency must be known first. A single
error matches the brief's one-message UX and the API's single error code. Alternatives: amount
before currency (impossible without a currency), or collecting all errors (more complex UI and API).

**D12. `ValidatedPayment` as proof of validation.**
Decision: a `@ConsistentCopyVisibility` data class with an internal constructor. Why: other modules
can't create or `copy()` one around the validator (verified with a compile probe from `:server`), so
any function taking a `ValidatedPayment` knows its input was checked ("parse, don't validate").

**D13. Practical email rules, trimmed input.**
Why: `local@domain.tld` with length limits (254, local part 64) catches typos without an unreadable
RFC 5322 regex; surrounding whitespace from typing is trimmed and the trimmed value is sent and
stored. The server stays the source of truth; Firestore rules use a looser check.

**D14. Currency codes are strict and case-sensitive** (`USD`, `EUR`).
Why: the dropdown always sends correct codes, and a strict API makes ambiguous client input visible
(`usd` → unsupported currency) instead of hiding it. `Currency.code` is explicit, so renaming an enum
entry can't change persisted values.

## API

**D15. One error format: `{"code","message"}`, with the code as a string.**
Why: clients branch on stable codes (`INVALID_EMAIL`, …), `message` is for developers. Keeping `code`
a string in the DTO lets older clients decode codes added later (they map unknown codes to a
generic error). Every error has this body, including 404 `NOT_FOUND` and 405 `METHOD_NOT_ALLOWED`.

**D16. 400 for validation errors, not 422.**
Why: the most common convention; the `code` already says exactly what's wrong. 422 is used only for
reusing an idempotency key with a different payment.

**D17. Strict JSON number for `amount`.**
Decision: `StrictJsonDoubleSerializer` rejects `"10.5"` (quoted), `null` and booleans with 400
`MALFORMED_REQUEST`. Why: kotlinx.serialization accepts quoted numbers by default, but the contract
says `amount: Double`. Alternative: tolerate quoted numbers (accepts a type the contract doesn't
specify).

**D18. `ignoreUnknownKeys` on both sides, via the shared `ApiJson`.**
Why: either side can add fields without breaking the other; missing required fields still fail.
Alternative: strict decoding on the server (catches client bugs, but makes the API harder to evolve).

## Idempotency

**D19. Optional `Idempotency-Key` header in the demo.**
Why: plain requests that match the brief's contract keep working (e.g. a reviewer's curl); the app
and the JMeter plan always send a key. Production should require it ([README](../README.md#production-hardening)).

**D20. Same key + same payment → replay; same key + different payment → 422.**
Why: a retried request returns the original 201 (same `id`, `createdAt`, plus
`Idempotent-Replayed: true`) and creates nothing; reusing a key for another payment is a client bug
and is rejected. "Same payment" compares the **normalized** `ValidatedPayment`, so `" a@b.co "` vs
`"a@b.co"` or `10.5` vs `10.50` match. Validation failures aren't stored, so a corrected retry with
the same key is processed.

**D21. In-memory store with one lock, 24 h TTL, 10,000 entries (LRU).**
Why: atomic check-and-create makes concurrent duplicates safe; the critical section only builds a
payment in memory. Alternative: Redis or a database table with a unique key (required in production
for multiple instances and restarts). Where: `server/…/idempotency/InMemoryIdempotencyStore.kt`.

**D22. Client key rule: reuse the key while the validated content is unchanged.**
Why: after a timeout the user may edit a field and restore the same value; a "new key on any edit"
rule would then create a duplicate payment. A new key is generated only when the content differs or
after a success. Where: `SendPaymentViewModel`.

**D23. No automatic retries in the client.**
Why: errors reach the UI immediately and the user retries with the same key (deduplicated by the
server); no hidden waits behind a spinner. Alternative: Ktor `HttpRequestRetry` once on network/5xx.
Timeouts: connect 10 s, request 15 s, socket 15 s.

## Persistence

**D24. The app writes to Firestore after the API succeeds.**
Why: the brief specifies it. In production the backend should own the write, so a client can't
record payments the backend never processed ([README](../README.md#production-hardening)).

**D25. Document id = payment id; write-once rules; read back on retry.**
Why: `firestore.rules` allow validated creates and no updates, so a retried save of an existing
document fails with `PERMISSION_DENIED`. The store then reads it back: same content → success
(idempotent), different → `Conflict`, missing → a real rules rejection (never hidden).

**D26. Firestore's server timestamp is the source of truth for History.**
Why: the `timestamp` field must equal `request.time` (enforced by the rules), so clients can't forge
it. History shows it, not the API's `createdAt`.

**D27. Offline save: wait for the server's acknowledgement, at most 10 s, then `Unavailable`.**
Why: Firestore queues writes offline and `set().await()` only completes after the server confirms.
Waiting with a timeout (`DEFAULT_OPERATION_TIMEOUT = 10.seconds`) tells the user the truth ("Payment
sent, but it couldn't be added to history yet"); the write stays queued and syncs later, which is
harmless because the document id is the payment id. **Rejected alternative: fire-and-forget**
(return success as soon as the write is in the local cache). It would be simpler, but a later
server-side rejection (e.g. by the rules) would never reach the user, and the UI would claim the
payment is in the history when it may never be.

**D28. Malformed history documents are skipped and logged by id only.**
Why: one bad document shouldn't break the whole History screen; the log omits the email (personal
data). Alternative: ending the flow with an error (one bad record would hide every valid payment).

**D29. In-memory Firestore cache when using the emulator.**
Why: restarting the emulator wipes its data, and a persistent cache would keep showing payments that
no longer exist. The real project keeps Firestore's persistent cache (history available offline).

**D30. History listener with `WhileSubscribed(5 s)`.**
Why: the Firestore listener runs only while the screen is visible, survives rotation, and is removed
in `awaitClose`. `ObservePaymentsUseCase` drops equal consecutive lists (`distinctUntilChanged`).

## UI

**D31. Inline field errors; snackbar only for results.**
Why: a validation error says which field is wrong right where it is; the snackbar is for "Payment
sent" and for network/server errors the user can't fix in a field.

**D32. Confirmation dialog before every send.**
Why: standard for money transfers; it shows the formatted amount and recipient
("Send $10.50 to alice@example.com?") as a last check against typos.

**D33. History shows the ISO currency code next to the formatted amount** (`$10.50 USD`).
Why: the brief requires the currency explicitly; a symbol alone (`$`) is ambiguous.

**D34. minSdk 24 without `java.time`.**
Why: timestamps use `java.text.DateFormat` (API 1) and amounts `NumberFormat` from `BigDecimal` minor
units, so neither minSdk 26 nor core library desugaring is needed. `kotlin.time.Instant` works on
API 24.

**D35. One UiState per screen, stateless composables.**
Why: `SendPaymentContent` and `HistoryContent` take state and callbacks only, so they're previewable
and testable; the thin `SendScreen` / `HistoryScreen` wrappers connect them to Koin ViewModels.

**D36. Navigation Compose 2.10.2 with type-safe routes, not Navigation 3.**
Why: mature and stable, two destinations, and `koinViewModel()` scopes ViewModels to each back-stack
entry automatically. Alternative: Navigation 3 (newer, the app owns the back stack as a list), which
needs `lifecycle-viewmodel-navigation3` for per-screen ViewModels and more setup for little gain with
two screens.

**D37. A Compose UI navigation test in addition to Appium.**
Why: Appium is optional in the brief and needs Appium, a device and the backend; `NavigationTest`
runs on a device with nothing else and catches navigation regressions quickly (start screen, tab
switching, no duplicate screens).

## Development and testing

**D38. Real device via `adb reverse`, with `BuildConfig` overrides.**
Why: the device reaches `127.0.0.1:8080` / `:8085` on the host without LAN IPs or firewall setup, and
the server stays on localhost. `local.properties` keys switch to `10.0.2.2` (Android emulator) or a
LAN IP. Invalid values fail the build.

**D39. Cleartext only in debug builds, only for local hosts.**
Why: release builds can never send payment data over plain HTTP. The debug config allows
`127.0.0.1`, `localhost` and `10.0.2.2`; a LAN IP must be added explicitly. The shared device-test
APK has its own config, limited to `127.0.0.1`.

**D40. Reviewer path with Firebase's `demo-` convention.**
Why: reviewers can't access `cashi-demo`. The example `google-services.json` uses `demo-cashi` and
the emulator runs with `--project demo-cashi`, so no account or login is needed. The device tests
take the project id from `google-services.json`, so they always match the emulator.

**D41. One store contract, run against the fake and real Firestore.**
Why: `PaymentStoreContractTest` defines the behaviour; running it against `InMemoryPaymentStore` (JVM)
and `FirestorePaymentStore` (device + emulator, which also enforces the rules) proves the fake used
everywhere else behaves like the real store.

**D42. Cucumber on the real use cases.**
Why: the scenarios (payment creation, transaction listing) exercise the real `SendPaymentUseCase`,
`ObservePaymentsUseCase` and validator; only the backend and Firestore are faked. They document real
behaviour instead of a separate test model.

**D43. JUnit split: JUnit 6 on the JVM, JUnit 4 on Android.**
Decision: JUnit 6.1.3 (Jupiter, JUnit Platform) for shared `jvmTest` (alongside Cucumber's engine),
the server and e2e; JUnit 4.13.2 for androidApp's unit and device tests and shared's Android host and
device tests. Why: Android's instrumentation runner and AGP's unit-test tasks are JUnit 4 based;
the pure JVM builds use the current platform. `kotlin-test` abstracts the assertions in both.

**D44. Appium tests in Kotlin, as a standalone Gradle build.**
Why: same language as the project, typed ids, the root version catalog and wrapper
(`./gradlew -p e2e test`), and not included in the app build, so `./gradlew build` never needs a
device or Appium. Alternative: WebdriverIO + Mocha (closest to Appium's own ecosystem, but the only
non-Kotlin code in the project).

**D45. `appium:disableIdLocatorAutocompletion=true`.**
Why (found on the device): UiAutomator2 auto-prefixes an id without a package
(`cashi.payment.oways:id/send_email_field`), but Compose's `testTagsAsResourceId` exposes the plain
tag (`send_email_field`), so every lookup timed out. Disabling auto-completion matches the ids in
[TEST_TAGS.md](TEST_TAGS.md) exactly. Where: `e2e/…/AppiumSession.kt`.

**D46. Load test: 5 users × 20 iterations, 500 ms per request, a new key per request.**
Why: 100 requests with a 5 s ramp-up is deterministic and comparable between runs; the 500 ms
Duration Assertion fails slow requests (the local server answers in a few milliseconds); a new
`Idempotency-Key` per request (`${__UUID()}`) measures real processing, not replays. Results:
[perf/README.md](../perf/README.md#sample-results). JMeter lives in a plain `perf/` folder (no
Gradle plugin, no configuration-cache issues).

**D47. Tests are written in every step, not at the end.**
Why: each step was verified before the next one started, which caught real issues early (e.g.
Ktor 3.6's content-negotiation exception hierarchy, Firestore's `Task<Void>` result, the Appium id
prefix).

**D48. R8 is off for release builds in this demo.**
Why: release builds aren't distributed, and they can't be exercised end to end here: release allows
HTTPS only, while the local backend is plain HTTP. Shrinking that can't be run would add risk
without a way to verify it. Alternative: enable R8 now and only check that `assembleRelease`
succeeds; rejected, because a successful build doesn't prove that serialization, DI and Firestore
still work after shrinking. Production path: enable `isMinifyEnabled` and `isShrinkResources`, add
app-specific keep rules where needed, and run a release smoke test against an HTTPS backend
([Production hardening](../README.md#production-hardening)). Where: `androidApp/build.gradle.kts`
(release build type), `androidApp/proguard-rules.pro`.

## Visual design

**D49. Fixed brand colors in light and dark mode, not dynamic color.**
Why: a payment app is recognized by its brand; with Material You's wallpaper-based colors the app,
its screenshots and the demo would look different on every device. The palette (emerald teal
with an amber accent) is defined as full Material 3 tonal roles for both modes, and every text and
background pair meets WCAG AA contrast. Alternative: `dynamicLightColorScheme`/`dynamicDarkColorScheme`
on Android 12+ (the app would blend in with the system, at the cost of a consistent identity).
Where: `androidApp/…/ui/theme/`.

## Known limitations

- **Single-instance idempotency store** (memory, lost on restart): production needs a shared store
  (D21, [Production hardening](../README.md#production-hardening)).
- **No authentication:** Firestore rules allow open reads and validated creates; production should
  require `request.auth != null` ([Production hardening](../README.md#production-hardening)).
- **Client-side Firestore write:** the backend should own it in production (D24).
- **`Double` wire format:** decimals beyond `Double` precision (e.g. `10.0000000001`) can't be
  distinguished from the rounded value and are accepted as `10.00`; a string amount would remove this
  (D8).
- **Long offline periods:** a save times out as `Unavailable` and syncs later (D27).
- **Unused `ktor-client` and Koin on the server's classpath** until `shared` is split into
  `shared-core` / `shared-presentation` (D1, [ARCHITECTURE.md](ARCHITECTURE.md#cross-platform-potential-ios)).
- **No iOS app yet:** the shared logic is ready; what iOS would add is in
  [ARCHITECTURE.md](ARCHITECTURE.md#cross-platform-potential-ios).