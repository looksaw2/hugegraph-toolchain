# Subtask 03 Implementation - OLTP / OLAP Function Verification

## Implemented

- `shortestPath` now builds `graph_view` from `PathOfVertices.vertices` and
  `PathOfVertices.edges` by querying the real vertices and edges through
  `TraverserManager`.
- If `vertices` is missing from the Server response, shortestPath falls back to
  vertex ids from the returned path.
- If graph-view backfill fails, shortestPath still returns PATH json/table
  results and degrades graph_view instead of failing the entire algorithm API.
- `upload_file.format_list` now defaults to `csv,txt` in both backend and
  dist configuration.
- Upload file extension validation is case-insensitive and trims whitelist
  entries, so files like `HLM.TXT` are accepted when `txt` is allowed.
- Hubble Loader mapping now treats ID fields as scalar by default where Hubble
  does not expose unfold controls.
- Added `dataset/README.md` and README import notes for `hlm.zip` and
  `movie 2.zip`.

## Verification

Covered by Hubble BE unit tests:

- shortestPath graph view from returned ids;
- shortestPath fallback when `vertices` is missing;
- shortestPath empty graph view for empty path;
- shortestPath result preserved when graph-view lookup fails;
- upload `.TXT` accepted, unsupported and missing extensions rejected;
- load mapping uses scalar ids by default.

Command:

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`.

## Remaining Release Gates

- Live Hubble + HugeGraph Server import/query smoke has now been executed and
  passed. See the follow-up verification below.
- Dataset archive provenance and redistribution terms remain unconfirmed. The
  archives are treated as local smoke-test inputs only and kept out of Hubble
  binary/release artifacts unless a later ASF review records their source and
  license status.
- Cypher and OLAP remain documented as Server/product-boundary items unless
  Hubble exposes and verifies those APIs in the final candidate.

## Follow-up Live Verification - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

Scope:

- Started rebuilt binary Hubble from
  `hugegraph-hubble/hubble-dist/apache-hugegraph-hubble-1.7.0/bin/start-hubble.sh`.
- Reused local HugeGraph Server connection `conn_id=1` to
  `127.0.0.1:8080/hugegraph`.
- Generated an isolated HLM smoke dataset with suffix `1780976106`.
- Created isolated Hubble schema:
  - vertex label `SmokePerson_1780976106`
  - edge label `SmokeRelation_1780976106`
  - property keys `smoke_*_1780976106`
- Uploaded `HLM_SMOKE_1780976106.TXT` through Hubble upload APIs.
- Configured file setting, two vertex mappings, and one edge mapping through
  Hubble job-manager/file-mapping APIs.
- Started the Hubble load task.

Observed results:

- Import job `225` finished as `SUCCESS`.
- Load task `193` finished as `SUCCEED`.
- Hubble Gremlin count:
  - vertices: `41`
  - edges: `51`
- Direct Server Gremlin count for the same labels:
  - vertices: `41`
  - edges: `51`
- Hubble shortestPath returned type `PATH` with graph_view:
  - vertices: `6`
  - edges: `5`
- Direct Server traverser shortestPath returned:
  - vertices: `6`
  - edges: `5`
  - path length: `6`
- Hubble-created schema was visible through direct Server schema endpoints with
  matching core fields.
- Hubble was stopped after the smoke: `stopped HugeGraphHubble`.

Notes:

- The smoke script intentionally opened label indexes on the isolated smoke
  vertex/edge labels so count queries by label are accepted by Server.
- Earlier failed smoke attempts created isolated timestamped smoke schema/data in
  the local development Server. They are not part of the release artifact.

## Follow-up UI Browser Verification - 2026-06-09

Command:

```bash
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result: `SUCCESS`.

Scope:

- Installed Playwright temporarily under `/tmp/hubble-ui-smoke-playwright`.
- Launched headless Chromium.
- Started binary Hubble from the rebuilt dist.
- Opened the real Hubble UI in Chromium.
- Captured screenshots under
  `.workflow/hubble-v2-release/ui-browser-smoke-screenshots/`.

Routes checked:

- `/`
- `/graph-management/1/data-analyze`
- `/graph-management/1/metadata-configs`
- `/graph-management/1/data-import/import-manager`
- `/graph-management/1/async-tasks`

Observed results:

- Home page title: `HugeGraph`.
- React root rendered non-empty visible content.
- UI API returned `1` graph connection.
- Routed pages checked: `4`.
- Console errors: `0`.
- Page errors: `0`.
- Failed browser requests: `0`.
- Hubble was stopped after verification: `stopped HugeGraphHubble`.

Limit:

- This is a browser smoke for first-screen rendering and major route loading,
  not a full manual UI acceptance test for every button/form branch.

## Follow-up Final Candidate Rerun - 2026-06-09

Commands:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_full_acceptance.js
```

Results:

- Live Hubble + Server smoke: `SUCCESS`, suffix `1780981142`.
- Import job `1` finished `SUCCESS`; load task `1` finished `SUCCEED`.
- Hubble and direct Server Gremlin counts matched:
  - vertices: `41` vs `41`
  - edges: `51` vs `51`
- Hubble shortestPath and direct Server traverser shape matched:
  - Hubble graph_view vertices/edges: `6`/`5`
  - Server traverser vertices/edges: `6`/`5`
- UI full acceptance: `SUCCESS`.
- UI full acceptance covered graph management, data analyze, metadata config,
  data import, async tasks, desktop/mobile rendering, and console/page/request
  error collection.

## Follow-up Algorithm API Inventory - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_algorithm_api_inventory.py
```

Result: `SUCCESS`.

Observed Hubble algorithm API behavior:

- `POST /api/v1.2/graph-connections/1/algorithms/shortestPath` succeeded.
- It returned type `PATH` and graph_view vertices/edges `6`/`5` for
  `贾太公_1780981142 -> 贾蓉_1780981142`.
- The Server-backed smoke data and direct traverser comparison remain valid for
  shortestPath.
- The FE algorithm panel exposes these slugs:
  `shortpath`, `allshortpath`, `paths`, `rings`, `crosspoints`,
  `fsimilarity`, `neighborrank`, `kneighbor`, `kout`, `customizedpaths`,
  `rays`, `sameneighbors`, `weightedshortpath`, `singleshortpath`,
  `jaccardsimilarity`, and `personalrank`.
- All 16 FE slugs returned HTTP/business status `405` with
  `Request method 'POST' not supported`.

Classification:

- Hubble BE currently exposes only the dedicated `shortestPath` algorithm
  endpoint.
- Other FE algorithm slugs are not validated as working Hubble APIs in this
  candidate.
- This is recorded as an Hubble UI/API integration gap or product-boundary
  item, not a Server traverser correctness failure.
- Per the CLAUDE.md task note, this pass marks and analyzes the abnormal
  algorithm API behavior; it does not implement the missing algorithm routes.

## Follow-up Dataset Provenance Audit - 2026-06-09

Checked archives:

- `dataset/hlm.zip`, SHA-256
  `c1286d5d1cb07e635867b784bcdecd41fb744ce846b32906400f259d151a0ec0`.
- `dataset/movie 2.zip`, SHA-256
  `6d7ad7b75b87c5fb3babbb3b6f38caae2cd2d816cf0776e55d2351cfe1b0a0b4`.

Result:

- No source URL, copyright statement, license, permission grant, or
  redistribution terms were found inside the archives.
- Git history does not provide release provenance for these archives.
- They remain local smoke-test inputs only and must not be published in ASF
  source releases, binary convenience artifacts, documentation bundles, or ASF
  mirrors unless a later review records acceptable provenance and license data.
