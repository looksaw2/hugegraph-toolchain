# Hubble V2.0 Final Traceability Review - 2026-06-09

## Scope

This review maps the original Feishu task split and CLAUDE.md priorities to the
local implementation records, code changes, and final verification artifacts.

## Corrected Summary

The core Hubble release-readiness checks have been executed and recorded. The
verified release scope is narrower than the broad UI surface in two places:

- Hubble algorithm API support is verified for `shortestPath` only.
- Local dataset archives are smoke-test inputs only and are excluded from ASF
  release artifacts while provenance is unverified.

## Traceability Matrix

| Requirement | Evidence | Result | Release Interpretation |
|-------------|----------|--------|------------------------|
| Build, package, and start Hubble | `subtask_implement_01.md`, Hubble tarball, unit tests | Complete | Core build gate satisfied |
| Runtime import and query with local test datasets | `hubble_live_smoke_result.md`, `hubble_movie_dataset_smoke_result.md` | Complete | HLM smoke imported `41` vertices and `51` edges; movie smoke imported a derived `15368`-row CSV and verified movie/artist/genre counts plus representative relationship edges |
| Graph algorithm API | `hubble_algorithm_api_inventory_result.md` | Partially implemented, fully inventoried | `shortestPath` works; 16 additional FE slugs return `405` and must be release-noted as unsupported/product-boundary unless implemented |
| ASF binary compliance | `subtask_implement_04.md`, binary inventory | Complete | Hubble tarball contains no `dataset/`, no runtime residue, and legal bundle covers final binary inventory |
| Runtime i18n | `hubble_ui_full_acceptance_result.md`, `hubble_ui_i18n_switch_result.md` | Complete | English render and real AppBar language switching passed with zero browser errors |
| Dataset source and license | `dataset_provenance_audit_2026-06-09.md` | Audited | `dataset/hlm.zip` and `dataset/movie 2.zip` have no verified provenance and must remain local-only |

## Corrections to the CC Summary

1. `dataset/*.zip` is not tracked by Git in this workspace. `git ls-files
   dataset` is empty, and `.gitignore` ignores `dataset/*.zip`.
2. The Hubble tarball does not include `dataset/`, so the current archives are
   not release artifacts.
3. The algorithm gate should not be described as "all algorithms pass".
   `shortestPath` passes; the other 16 frontend slugs are documented as an
   Hubble UI/API integration gap or product-boundary item.
4. "No release blocker" is accurate only for the verified release scope:
   build, start, import, Gremlin, `shortestPath`, UI smoke, i18n, and binary
   compliance. It should not be used to imply support for unimplemented Hubble
   algorithm routes.
5. Movie dataset verification failures observed during iteration were not load
   failures. One was sandbox localhost socket denial; later failures happened
   after successful import when `g.E().hasLabel(...).count()` triggered
   HugeGraph Server's big-id length guard (`Big id max length is 16384`) through
   a large edge-id `IN` query. Final evidence uses vertex counts and anchored
   sample edge traversals instead.

## Final Release Note / Vote Thread Snippet

The concrete release note / vote thread wording is recorded in:

- `.workflow/hubble-v2-release/release_note_vote_snippet_2026-06-09.md`

Include these points in the release note or vote thread:

- Hubble algorithm API verification covers `shortestPath`.
- Additional algorithm forms/routes observed in the frontend are not validated
  Hubble backend APIs in this candidate.
- `dataset/hlm.zip` and `dataset/movie 2.zip` are local smoke-test inputs only,
  excluded from source and binary release artifacts until provenance and
  license terms are reviewed.
- Local smoke verification did exercise both archives: HLM passed full import,
  Gremlin count, and `shortestPath`; movie passed import plus movie/artist/genre
  counts and representative relationship-edge checks. These checks do not make
  the archives ASF release artifacts.

## Feishu Record

The remaining-work closure was uploaded and read back for subtasks 02-05:

| Subtask | Revision | Readback |
|---------|----------|----------|
| 02 | `45` | `Remaining Work Closure` found |
| 03 | `57` | `Remaining Work Closure` found |
| 04 | `39` | `Remaining Work Closure` found |
| 05 | `44` | `Remaining Work Closure` found |

Local Feishu snapshots were refreshed after the upload by
`bash .workflow/hubble-v2-release/refresh_feishu_downloads.sh`.
