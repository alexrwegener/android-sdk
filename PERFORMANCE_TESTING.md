# Performance test suite

A small, opt-in benchmark suite for the SDK's latency-sensitive paths. It lives alongside the
unit tests in `src/test` and runs under JUnit4 + Robolectric using the same
`MockWebServer`/`mockk` harness as the rest of the suite.

## What it measures

`StartupPerformanceTest` (`measureStartupPerformance`):
- **online cold start** — a fresh client + fresh user initialized against a `MockWebServer`
  that serves a real initialize response (exercises the full network → parse → store → cache path)
- **offline warm start** — `initializeOffline = true` reading previously-cached values from disk
- **updateUser** — re-fetch + re-evaluate for a new user on an already-initialized client
- **online cold start, large payload** — same as online, with 1,000 gates + 1,000 configs, to
  surface parse/format scaling

`EvaluationPerformanceTest` (`measureEvaluationPerformance`):
- per-call latency of `checkGate` / `getFeatureGate` / `getConfig` / `getExperiment` / `getLayer`
- the same evaluations against a 1,000-entry payload, evaluated round-robin, to surface
  lookup/eval scaling

Each scenario reports mean / p50 / p90 / p99 / min / max (see `PerfHarness`).

## Running

```bash
# Both perf classes
./gradlew testDebugUnitTest -PstatsigPerf --tests "*PerformanceTest"

# Just one
./gradlew testDebugUnitTest -PstatsigPerf --tests "*EvaluationPerformanceTest"
```

`-PstatsigPerf` sets `-Dstatsig.perf=true`, forces a single test fork (parallel forks make
timings meaningless), and bumps the heap. Without it, the perf tests **skip themselves** via a
JUnit assumption, so they never affect the normal `testDebugUnitTest` job or CI. The timing
tables print to the console (and to the HTML/XML reports under
`build/reports/tests/testDebugUnitTest/`).

This suite is intended for **local** perf testing only — it is not wired into any CI workflow.

## Reading the numbers

These are **JVM/Robolectric** measurements: no ART, no real device IO scheduler, localhost
sockets. Treat them as **relative** signals — compare a branch against `main` on the same
machine, or compare code paths against each other. They are not device-representative absolute
benchmarks. For that, an instrumented `androidx.benchmark`/macrobenchmark module on a real
device would be the next step (the repo has no `src/androidTest` today).

## Extending

Add scenarios with `PerfHarness.measure { ... }` (in-place work) or
`PerfHarness.measureFresh(setup = ..., block = ...)` (fresh untimed state per iteration), then
pass the resulting `Stats` to `PerfHarness.report(...)`. Payload builders live in `PerfPayloads`.
