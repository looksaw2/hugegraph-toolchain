# Subtask 01 Implementation - Build, Startup, and Bug Disposition

## Implemented

- Added Hubble BE unit coverage for release-blocking bug fixes:
  - `OltpAlgoServiceTest`
  - `JobManagerServiceTest`
  - `LoadTaskServiceTest`
  - `FileUploadControllerTest`
- Updated `UnitTestSuite` so the Hubble unit profile covers the new focused
  service/controller tests.
- Added `maven-surefire-plugin` include configuration in `hubble-be/pom.xml`
  so `-P unit-test` runs `UnitTestSuite` only, matching the documented Hubble
  unit-test command and avoiding API tests that require an external Server.
- Fixed `start-hubble.sh -f` to work as documented: `-f` now starts foreground
  mode without requiring a `true|false` argument.
- Improved startup script behavior:
  - normalize bind host `0.0.0.0` / `::` to `127.0.0.1` for health probing;
  - detect already healthy Hubble before starting;
  - remove stale pid on startup failure;
  - terminate the spawned process if health check fails.

## Verification

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`.

An earlier run without the Surefire include fix executed `GraphConnectionTest`
and failed because no HugeGraph Server was listening on `127.0.0.1:8080`. That
confirmed the profile was running beyond unit scope; the profile now runs only
`UnitTestSuite`.

## Remaining Release Gates

- Final Hubble binary candidate generation and runtime smoke have now been
  recorded. See the follow-up candidate rerun below.
- Any future runtime failures from a later candidate must still be classified as
  fix-before-release, known issue with issue link, or release blocker.

## Follow-up Final Candidate Rerun - 2026-06-09

Candidate:

- `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz`
- timestamp: `2026-06-09 12:56:40 +0800`
- size: `197714261` bytes

Commands and results:

```bash
mvn package -DskipTests -ntp
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_full_acceptance.js
```

Results:

- Hubble package build: `BUILD SUCCESS`.
- Hubble BE unit test: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0,
  Skipped: 0`.
- Live Hubble + Server smoke: `SUCCESS`.
- UI browser smoke: `SUCCESS`.
- UI full acceptance: `SUCCESS`.
- Hubble process check after UI verification found no remaining
  `HugeGraphHubble` process.
