#!/usr/bin/env bash
# Runs the POST /payments load test in JMeter's non-GUI mode and builds the HTML dashboard.
# Usage: ./perf/run-load-test.sh [-Jthreads=5] [-Jloops=20] [-JrampUp=5] [-Jhost=127.0.0.1]
#                                [-Jport=8080] [-JmaxResponseMs=500]
# Set HEALTH_URL when the server isn't at the default address.
# Exits non-zero if the server is unreachable or any request failed an assertion.
set -euo pipefail

PERF_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly PERF_DIR
readonly PLAN="$PERF_DIR/payments-load-test.jmx"
readonly RESULTS_DIR="$PERF_DIR/results"
readonly RESULTS_FILE="$RESULTS_DIR/results.jtl"
readonly JMETER_LOG="$RESULTS_DIR/jmeter.log"
readonly REPORT_DIR="$PERF_DIR/report"
readonly HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/health}"
# Column of the "success" flag in JMeter's default CSV result format.
readonly SUCCESS_COLUMN=8

if ! command -v jmeter >/dev/null 2>&1; then
  echo "error: jmeter not found. Install it (macOS: brew install jmeter) and retry." >&2
  exit 1
fi

if ! curl -fsS "$HEALTH_URL" >/dev/null; then
  echo "error: the server isn't reachable at $HEALTH_URL. Start it first (see perf/README.md)." >&2
  exit 1
fi

# JMeter requires an empty report directory and appends to existing result files.
rm -rf "$REPORT_DIR"
mkdir -p "$RESULTS_DIR"
rm -f "$RESULTS_FILE"

jmeter -n -t "$PLAN" -l "$RESULTS_FILE" -j "$JMETER_LOG" -e -o "$REPORT_DIR" "$@"

failed=$(awk -F, -v col="$SUCCESS_COLUMN" 'NR > 1 && $col == "false"' "$RESULTS_FILE" | wc -l | tr -d ' ')
echo "HTML report: $REPORT_DIR/index.html"
if [ "$failed" -gt 0 ]; then
  echo "error: $failed request(s) failed an assertion; see the report." >&2
  exit 1
fi
echo "All requests passed."