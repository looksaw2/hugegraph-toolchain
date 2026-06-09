# Hubble V2.0 Testing Checkpoint - 2026-06-09 18:32 +0800

## Current State

- Working directory: `/home/looksaw/hugegraph-toolchain`.
- HugeGraph Server container `hugegraph-server` was started for smoke tests and
  is currently expected to be running on `127.0.0.1:8080`.
- No `HugeGraphHubble` process was found after the interrupted UI smoke rerun.
- `http://127.0.0.1:8088/actuator/health` was not reachable after interruption.
- Playwright was not present at the old temp path
  `/tmp/hubble-ui-smoke-playwright/node_modules`.
- Reusable local Playwright module path found:
  `/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules`.

## Tests Rerun In This Session

### Hubble BE Unit Tests

Command:

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result:

- `BUILD SUCCESS`
- `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`

### Live HLM Smoke

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result:

- Status: `SUCCESS`
- Result files:
  - `.workflow/hubble-v2-release/hubble_live_smoke_result.json`
  - `.workflow/hubble-v2-release/hubble_live_smoke_result.md`
- Suffix: `1781000742`
- Connection mode: `reused`, `conn_id=1`
- Import job/task: `SUCCESS` / `SUCCEED`
- Hubble Gremlin counts: vertices `41`, edges `51`
- Direct Server Gremlin counts: vertices `41`, edges `51`
- Hubble shortestPath graph view: vertices `6`, edges `5`
- Direct Server shortestPath shape: vertices `6`, edges `5`, path length `6`
- Hubble stopped cleanly by the script.

### Movie Dataset Smoke

Command:

```bash
python3 .workflow/hubble-v2-release/run_movie_dataset_smoke.py
```

Result:

- Status: `SUCCESS`
- Result files:
  - `.workflow/hubble-v2-release/hubble_movie_dataset_smoke_result.json`
  - `.workflow/hubble-v2-release/hubble_movie_dataset_smoke_result.md`
- Suffix: `1781000789`
- Derived CSV rows: `15368`
- Import job/task: `SUCCESS` / `SUCCEED`
- Server vertex counts:
  - movie: `15038`
  - artist: `14957`
  - genre: `98`
- Representative relationship checks matched Hubble and Server:
  - directed: `1` / `1`
  - acted_in: `1` / `1`
  - in_genre: `1` / `1`
- Hubble stopped cleanly by the script.

### Algorithm API Inventory

Command:

```bash
python3 .workflow/hubble-v2-release/run_algorithm_api_inventory.py
```

Result:

- Status: `SUCCESS`
- Result files:
  - `.workflow/hubble-v2-release/hubble_algorithm_api_inventory_result.json`
  - `.workflow/hubble-v2-release/hubble_algorithm_api_inventory_result.md`
- Suffix: `1781000818`
- Successful algorithm endpoints: `15`
- Non-success FE algorithm slug endpoints: `2`
- `shortestPath` returned HTTP/business `200/200`, type `PATH`, graph `2/1`.
- These slugs returned HTTP/business `200/200` in this run:
  - `shortpath`
  - `allshortpath`
  - `paths`
  - `rings`
  - `crosspoints`
  - `fsimilarity`
  - `neighborrank`
  - `kneighbor`
  - `kout`
  - `customizedpaths`
  - `rays`
  - `sameneighbors`
  - `weightedshortpath`
  - `singleshortpath`
- Non-success results:
  - `jaccardsimilarity`: business `400`, JSON serialization/cast error.
  - `personalrank`: business `400`, edge label must link different vertex labels.
- This supersedes the older local note that all 16 FE slugs returned `405`.
- Hubble stopped cleanly by the script.

### UI Browser Smoke

First command:

```bash
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result:

- Failed before running UI checks because Node could not find module
  `playwright`.

Second command:

```bash
NODE_PATH=/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result:

- Wrote:
  - `.workflow/hubble-v2-release/hubble_ui_browser_smoke_result.json`
  - `.workflow/hubble-v2-release/hubble_ui_browser_smoke_result.md`
- Status: `FAILED`
- Failure happened during Hubble startup before browser route checks.
- Root error: Tomcat failed to bind `8088` with
  `java.net.SocketException: Operation not permitted`.
- Routed pages checked: `0`
- Browser console errors/page errors/failed requests: `0/0/0`

Attempted elevated rerun:

```bash
NODE_PATH=/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result:

- Requested elevated execution to allow local socket binding for Hubble and
  Playwright.
- User interrupted the turn after roughly `168s`.
- Post-interruption check found no `HugeGraphHubble` process and no reachable
  `8088` health endpoint.

## Next Resume Point

Recommended next command, with elevated execution if the sandbox still blocks
local socket binding:

```bash
NODE_PATH=/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

After that, rerun full UI acceptance if browser smoke passes:

```bash
NODE_PATH=/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules \
node .workflow/hubble-v2-release/run_ui_full_acceptance.js
```

The backend/unit/live smoke gates are green at this checkpoint. The remaining
unfinished item is browser/UI verification under a runtime that allows binding
Hubble to `8088`.
