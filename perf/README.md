# Load test: POST /payments

A JMeter plan that sends payments from **5 concurrent users** and measures response times.

| File | Purpose |
|---|---|
| `payments-load-test.jmx` | The test plan (open it in the JMeter GUI to inspect, run it non-GUI) |
| `run-load-test.sh` / `run-load-test.bat` | Runs the plan non-GUI and builds the HTML dashboard |
| `results/` | Raw results (`results.jtl`) and `jmeter.log` of the last run (gitignored) |
| `report/` | HTML dashboard of the last run (gitignored) |

## What it does

- **Load:** 5 users (threads), 5 s ramp-up, 20 iterations each = **100 requests**.
- **Each request** is a distinct, valid payment: a unique recipient (`load-<user>-<iteration>@example.com`),
  a random amount from 1.00 to 999.99 (always 2 decimals) and a random currency (USD or EUR).
- **A new `Idempotency-Key` per request** (`${__UUID()}`), so the server processes every request
  instead of replaying a stored result. The test measures real processing.
- **Assertions per request:** status `201 Created`; the response has an `id`; its `currency`
  matches the request; the response arrives within **500 ms**.
- **Scope:** the backend only (validation, idempotency store, JSON). Firestore isn't involved: the
  app writes to Firestore after the API call, not the server.

## Run it

1. Install JMeter 5.6.3 (needs Java 17+):
   - macOS: `brew install jmeter`
   - Windows/Linux: download the binary from https://jmeter.apache.org/download_jmeter.cgi and add
     its `bin` folder to `PATH`.
2. Start the backend **standalone**, so Gradle doesn't sit between the server and its output:
   ```bash
   ./gradlew :server:installDist && server/build/install/server/bin/server
   ```
3. In another terminal, from the repo root:
   ```bash
   ./perf/run-load-test.sh            # Windows: perf\run-load-test.bat
   open perf/report/index.html        # the dashboard
   ```
   The script checks that JMeter is installed and the server answers `/health`, clears the previous
   report, runs the plan and exits non-zero if any request failed an assertion.

### Parameters

Override any default with `-J<name>=<value>`, e.g. `./perf/run-load-test.sh -Jloops=100`:

| Property | Default | Meaning |
|---|---|---|
| `threads` | `5` | Concurrent users |
| `rampUp` | `5` | Seconds until all users are running |
| `loops` | `20` | Requests per user |
| `host` / `port` | `127.0.0.1` / `8080` | Backend address (also set `HEALTH_URL` for the script's check) |
| `maxResponseMs` | `500` | Per-request response-time limit |

## Reading the report

In `perf/report/index.html`:
- **Statistics** table: samples, error %, average, median (50th pct), 90th/95th/99th percentile,
  min/max response time and throughput (requests per second).
- **Errors** / **Top 5 Errors by sampler**: which assertion failed, if any.
- **Charts → Response Times Over Time** and **Response Time Percentiles**: how latency developed
  while the users ramped up. The first requests are usually slower while the JVM warms up.

## Sample results

One run of the default plan, so reviewers can see typical numbers without running JMeter.

- **Date:** 2026-10-23
- **Machine:** MacBook Pro, Apple M1 Max, 32 GB RAM, macOS 27.0.1
- **Server:** run standalone via `installDist`, bound to `127.0.0.1:8080`
- **Load:** 5 users, 5 s ramp-up, 20 iterations each (`./perf/run-load-test.sh`, defaults)

| Metric (POST /payments) | Value |
|---|---|
| Samples | 100 |
| Errors | 0 (0.00 %) |
| Average | 2.46 ms |
| Min | 1 ms |
| Median | 2.00 ms |
| 90th percentile | 3.00 ms |
| 95th percentile | 9.70 ms |
| 99th percentile | 47.63 ms |
| Max | 48 ms |
| Throughput | 26.92 requests/s |

Notes:
- **Max and p99 are most likely JVM warm-up:** the slowest requests are among the first ones
  (class loading, JIT compilation). Median and p90 stay at 2–3 ms.
- **Throughput reflects the test's pacing, not the server's capacity:** 5 users with a 5 s ramp-up
  send 100 requests in about 4 s. To find the server's limit, raise `threads` and `loops`.
- All requests passed every assertion, including the 500 ms limit.
