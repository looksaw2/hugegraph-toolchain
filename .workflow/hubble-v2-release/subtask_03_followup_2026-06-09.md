## Follow-up Live Verification - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

- Rebuilt binary Hubble started from `hubble-dist/apache-hugegraph-hubble-1.7.0`.
- Reused local Server connection `conn_id=1` to `127.0.0.1:8080/hugegraph`.
- Imported isolated HLM smoke data with suffix `1780981142`.
- Import job `1` finished as `SUCCESS`; load task `1` finished as `SUCCEED`.
- Hubble Gremlin counts: vertices `41`, edges `51`.
- Direct Server Gremlin counts for the same labels: vertices `41`, edges `51`.
- Hubble shortestPath returned `PATH` with graph_view vertices/edges `6`/`5`.
- Direct Server shortestPath returned vertices/edges `6`/`5`, path length `6`.
- Hubble was stopped after verification.

Conclusion: Subtask 03 live Hubble + Server import/query smoke is now complete.

## Follow-up Local Dataset Verification - 2026-06-09

The local-only dataset archives were also exercised through Hubble import smoke
paths. These archives remain outside ASF artifacts because their provenance and
redistribution terms are unverified.

### HLM

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

- Imported `dataset/hlm.zip` through Hubble upload, mapping, and load-task APIs.
- Hubble import completed with `41` vertices and `51` edges.
- Hubble `shortestPath` returned `PATH` with graph_view vertices/edges `6`/`5`.
- Result evidence: `.workflow/hubble-v2-release/hubble_live_smoke_result.md`.

### Movie

Command:

```bash
python3 .workflow/hubble-v2-release/run_movie_dataset_smoke.py
```

Result: `SUCCESS`.

- Derived a temporary single-value CSV from `dataset/movie 2.zip` with `15368`
  rows. This keeps the source archive local-only and avoids depending on
  Hubble controls that are not exposed for edge endpoint list unfolding.
- Imported the derived CSV through Hubble upload, mapping, and load-task APIs.
- Load task `129` finished as `SUCCEED`.
- Direct Server counts after import: `15038` movie vertices, `14957` artist
  vertices, and `98` genre vertices.
- Hubble and direct Server sample checks returned `1` for `directed`,
  `acted_in`, and `in_genre`.
- Result evidence:
  `.workflow/hubble-v2-release/hubble_movie_dataset_smoke_result.md`.

### Failed Attempt Root Cause

The movie failures were not dataset-ingestion failures:

- One run failed before import because sandbox networking blocked localhost
  socket creation (`Operation not permitted`).
- Later runs imported successfully but failed during validation of
  `g.E().hasLabel(...).count()`. HugeGraph generated a large edge-id `IN` query
  for the movie edge labels and rejected it with `Big id max length is 16384`.

Final validation uses vertex counts plus anchored sample edge traversals, which
proves representative relationship data landed without triggering that large
edge-id query path.
