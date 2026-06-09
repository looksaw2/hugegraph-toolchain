# Hubble V2.0 Release Note / Vote Thread Snippet - 2026-06-09

## Suggested Release Scope Wording

Hubble V2.0 release-readiness was verified for the core management flow:
build/package/start, graph connection, schema creation, local HLM/movie dataset
import, Gremlin query checks, the Hubble `shortestPath` backend algorithm API,
major UI routes, runtime zh-CN/en-US language switching, and binary artifact
compliance.

## Verification Evidence

| Area | Result |
|------|--------|
| Hubble unit tests | `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp` passed with `15` tests |
| Live import/query smoke | HLM smoke imported `41` vertices and `51` edges; Hubble and direct Server counts matched |
| Local movie dataset smoke | Movie smoke derived `15368` CSV rows from the local archive, load task `129` finished `SUCCEED`, Server counted `15038` movie vertices / `14957` artist vertices / `98` genre vertices, and Hubble + Server sample relationship checks returned `1` |
| Algorithm API | `POST /api/v1.2/graph-connections/{connId}/algorithms/shortestPath` returned `PATH` with graph view V/E `6/5` |
| UI browser smoke | Major graph-management routes rendered with `0` console errors, `0` page errors, and `0` failed requests |
| UI full acceptance | `31` screenshots captured across desktop/mobile flows with no severe browser failures |
| Runtime i18n | AppBar switch `zh-CN -> en-US -> zh-CN` passed with no browser errors |
| Binary inventory | Final Hubble tarball had `270` lib jars and `287` license files; no `dataset/`, source maps, Node/Yarn runtime, logs, pid files, upload files, or H2 runtime database files |

## Required Known Scope Notes

- Hubble algorithm API verification covers `shortestPath` only.
- The additional frontend algorithm slugs checked in this candidate returned
  HTTP/business `405` from Hubble BE and should not be claimed as
  release-supported Hubble backend APIs unless matching routes are implemented
  and verified.
- `dataset/hlm.zip` and `dataset/movie 2.zip` are local smoke-test inputs only.
  They are ignored/untracked in this workspace and excluded from ASF source and
  binary release artifacts until provenance, copyright ownership, license, and
  redistribution terms are reviewed.
- The movie smoke initially hit two verification-only issues: sandbox localhost
  socket denial, then HugeGraph Server's big-id length guard on
  `g.E().hasLabel(...).count()`. The final passing check uses vertex counts and
  anchored relationship-edge samples instead of full edge-label counts.
- Final ASF vote review should still validate the source release, signatures,
  checksums, LICENSE, NOTICE, and dependency license alignment for the actual
  release candidate artifact.

## Suggested Vote Thread Paragraph

For Hubble V2.0, this candidate has been smoke-tested through build/package,
startup, local HLM/movie dataset import, Gremlin query comparison with
HugeGraph Server, `shortestPath` algorithm API verification, UI browser
acceptance, runtime i18n switching, and binary inventory checks. The verified
Hubble algorithm API scope is `shortestPath`; additional frontend algorithm
forms are treated as product boundary items until corresponding Hubble backend
routes are implemented and verified. Local dataset archives under `dataset/`
are not part of the source or binary release artifacts.
