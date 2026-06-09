# Subtask 05 Implementation - Server / API Gap Confirmation

## Implemented

- Hubble `shortestPath` API now treats Server traverser response ids as ids and
  backfills graph view objects explicitly.
- Graph view backfill failures are isolated from json/table path results, which
  helps distinguish Hubble view assembly problems from Server traverser results.
- Job manager status refresh now runs on:
  - single job `get`;
  - id-list `list`;
  - paged `list`.
- Empty load task lists keep a `LOADING` job in `LOADING` instead of marking it
  `SUCCESS`.
- Gremlin scalar `count()` remains a Server/API behavior boundary: Hubble graph
  view generation is only expected for vertex/edge/path result types.
- README server compatibility text documents that Hubble 2.0 needs matching
  Server-side APIs from the same release line, without claiming Cypher or OLAP
  support as Hubble UI capabilities.

## Verification

Covered by unit tests:

- job refresh to `SUCCESS`;
- job refresh to `FAILED`;
- job stays `LOADING` while a task is running;
- job stays `LOADING` when task list is empty;
- id-list job refresh.

Command:

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`.

## Remaining Release Gates

- Direct Server comparison requests have now been executed for schema, Gremlin
  counts, and shortestPath. See the follow-up verification below.
- Remaining future failures in async task or other untested APIs should still be
  classified as Hubble / Server / config / data / product-boundary.
- Server-side release-impacting issues still need real apache/hugegraph issue or
  PR links before they can be accepted as known issues.

## Follow-up Server Comparison - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

Compared flows:

- Hubble schema create vs direct Server schema read.
- Hubble Gremlin count vs direct Server Gremlin count.
- Hubble shortestPath API vs direct Server traverser shortestPath.
- Hubble import orchestration vs direct Server post-import data visibility.

Observed comparison:

- Hubble-created property key `smoke_name_1780976106` was visible from direct
  Server schema API with matching `data_type`.
- Hubble-created vertex label `SmokePerson_1780976106` was visible from direct
  Server schema API with matching `id_strategy`.
- Hubble-created edge label `SmokeRelation_1780976106` was visible from direct
  Server schema API with matching source/target labels.
- Hubble Gremlin count matched direct Server Gremlin count:
  - vertices: `41` vs `41`
  - edges: `51` vs `51`
- Hubble shortestPath matched direct Server traverser shape for the checked path:
  - Hubble graph_view vertices/edges: `6`/`5`
  - Server traverser vertices/edges: `6`/`5`
  - Server path length: `6`

Classification:

- No Server/API gap was found in the verified schema, Gremlin count, import data
  visibility, or shortestPath traverser paths.
- The previous label-count failure was configuration/data-shape related:
  Server rejects `hasLabel(...).count()` when label index is disabled. The final
  smoke created isolated labels with `open_label_index=true` for count checks.
- The Hubble shortestPath graph_view fix is validated against a real Server
  traverser response with non-empty vertices and edges.
- Hubble import remains a product-boundary orchestration flow: Hubble stores job
  metadata locally and invokes HugeGraph Loader, while Server only exposes the
  resulting graph data and schema.

## Follow-up UI Browser Verification - 2026-06-09

Command:

```bash
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result: `SUCCESS`.

Observed comparison-relevant UI state:

- The browser-rendered graph list showed the real connection
  `SmokeConn_1780972708` for graph `hugegraph` at `127.0.0.1:8080`.
- `/graph-management/1/data-analyze` rendered execution history including the
  real shortestPath smoke query.
- `/graph-management/1/metadata-configs` rendered the Server-backed schema list.
- `/graph-management/1/data-import/import-manager` rendered completed smoke
  import jobs.
- `/graph-management/1/async-tasks` rendered Server task rows.
- Browser console errors, page errors, and failed requests were all `0`.

Classification:

- No UI/API integration gap was found in the checked first-screen routes.
- This does not replace exhaustive UI form-level testing, but it validates real
  Chromium rendering of the primary Hubble routes against the local Server data.

## Follow-up Final Candidate Server Comparison - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

Latest comparison:

- Suffix: `1780981142`.
- Hubble-created property key `smoke_name_1780981142` was visible from direct
  Server schema API with matching `data_type`.
- Hubble-created vertex label `SmokePerson_1780981142` was visible from direct
  Server schema API with matching `id_strategy`.
- Hubble-created edge label `SmokeRelation_1780981142` was visible from direct
  Server schema API with matching source/target labels.
- Hubble Gremlin count matched direct Server Gremlin count:
  - vertices: `41` vs `41`
  - edges: `51` vs `51`
- Hubble shortestPath matched direct Server traverser shape:
  - Hubble graph_view vertices/edges: `6`/`5`
  - Server traverser vertices/edges: `6`/`5`
  - Server path length: `6`

Classification:

- No Server/API gap was found in the final-candidate schema, Gremlin count,
  import data visibility, or shortestPath traverser paths.

## Follow-up Algorithm API Gap Inventory - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_algorithm_api_inventory.py
```

Result: `SUCCESS`.

API classification:

- Hubble BE exposes one dedicated algorithm endpoint:
  `POST /api/v1.2/graph-connections/{connId}/algorithms/shortestPath`.
- The endpoint passed against final smoke data:
  - source/target: `贾太公_1780981142 -> 贾蓉_1780981142`
  - result type: `PATH`
  - graph_view vertices/edges: `6`/`5`
- FE exposes 16 additional algorithm slugs under
  `/api/v1.2/graph-connections/{connId}/algorithms/{slug}`:
  `shortpath`, `allshortpath`, `paths`, `rings`, `crosspoints`,
  `fsimilarity`, `neighborrank`, `kneighbor`, `kout`, `customizedpaths`,
  `rays`, `sameneighbors`, `weightedshortpath`, `singleshortpath`,
  `jaccardsimilarity`, and `personalrank`.
- All 16 FE slugs returned HTTP/business status `405` with
  `Request method 'POST' not supported`.

Conclusion:

- The verified Server traverser path for shortestPath is healthy.
- The non-shortestPath algorithm behavior is an Hubble UI/API integration gap
  or product-boundary issue in this candidate, not a demonstrated Server
  traverser failure.
- This pass records the gap for release decision-making; it does not implement
  missing Hubble algorithm routes.
