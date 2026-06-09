# Dataset Provenance Audit - 2026-06-09

## Scope

Checked local archives:

| Archive | SHA-256 | Release conclusion |
|---------|---------|--------------------|
| `dataset/hlm.zip` | `c1286d5d1cb07e635867b784bcdecd41fb744ce846b32906400f259d151a0ec0` | Do not include in ASF release artifacts without provenance and license evidence |
| `dataset/movie 2.zip` | `6d7ad7b75b87c5fb3babbb3b6f38caae2cd2d816cf0776e55d2351cfe1b0a0b4` | Do not include in ASF release artifacts without provenance and license evidence |

## Local Evidence

- `dataset/hlm.zip` contains `hlm/hlm.txt`, `hlm/schema_hlm.groovy`, and
  `hlm/struct_hlm.json`.
- `dataset/movie 2.zip` contains `movie/movie.csv`, `schema_movie*.groovy`, and
  `struct_movie*.json`.
- `git ls-files dataset` is empty and `git log --all --stat -- dataset` has no
  release provenance for the archives.
- Searches inside the archives found no source URL, copyright statement,
  license, permission grant, or redistribution terms.
- `unzip -z` found no archive comments.
- `.gitignore` ignores `dataset/*.zip` by default.
- The rebuilt Hubble binary candidate does not contain `dataset/`.

## ASF Disposition

The archives are local Hubble import smoke-test inputs only. They must not be
published in Apache HugeGraph source releases, binary convenience artifacts,
documentation bundles, or ASF mirrors unless a later review records:

- original source;
- copyright ownership;
- ASF-compatible license;
- redistribution permission;
- required `LICENSE` / `NOTICE` handling.

If a release needs bundled sample data, prefer a small synthetic dataset created
for the project and committed as transparent text/JSON/Groovy source files with
clear Apache-2.0 provenance.

## Local Smoke Results

These checks validate the local Hubble import path only. They do not change the
ASF disposition above.

| Archive | Command | Result | Evidence |
|---------|---------|--------|----------|
| `dataset/hlm.zip` | `python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py` | `SUCCESS`; imported `41` vertices and `51` edges; Hubble `shortestPath` graph_view returned `6` vertices and `5` edges | `.workflow/hubble-v2-release/hubble_live_smoke_result.md` |
| `dataset/movie 2.zip` | `python3 .workflow/hubble-v2-release/run_movie_dataset_smoke.py` | `SUCCESS`; derived `15368` CSV rows; load task `129` finished `SUCCEED`; Server counted `15038` movie vertices, `14957` artist vertices, and `98` genre vertices; Hubble and Server sample edge checks returned `1` for `directed`, `acted_in`, and `in_genre` | `.workflow/hubble-v2-release/hubble_movie_dataset_smoke_result.md` |

## Failure Analysis

The failed movie dataset attempts were verification-environment or query-shape
issues, not archive or Hubble import failures:

- The first movie script run failed before import because sandbox networking
  denied localhost socket creation (`Operation not permitted`). The script was
  rerun with approved local service access.
- Later failed movie runs reached `SUCCESS` / `SUCCEED` import states, then
  failed while validating `g.E().hasLabel(...).count()`. For this dataset,
  HugeGraph materialized a large edge-id `IN` query and rejected it with
  `Big id max length is 16384`.
- Final validation uses full vertex counts and anchored sample edge traversals
  instead of full edge-label counts. That proves the imported graph contains the
  expected movie, artist, genre vertices and representative relationship edges
  without triggering the large edge-id query path.
