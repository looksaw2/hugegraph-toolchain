## Follow-up Binary Inventory - 2026-06-09

Checked artifacts:

- `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz`
- `hugegraph-hubble/hubble-dist/apache-hugegraph-hubble-1.7.0`

Results:

- Rebuilt tarball timestamp: `2026-06-09 12:56:40 +0800`.
- Rebuilt tarball size: `197714261` bytes.
- Tarball root structure is expected: `LICENSE`, `NOTICE`, `README.md`, `bin`, `conf`, `lib`, `licenses`, `ui`.
- Tarball contains `270` lib jars and `287` top-level `licenses/LICENSE-*.txt` files.
- Tarball does not contain `dataset/`, `node_modules`, Node/Yarn runtime/cache, `logs/`, `upload-files/`, H2 database files, `bin/pid`, or root `*.pid`.
- The latest rebuilt tarball includes `hg-pd-*`, `grpc-*`, `commons-collections4`, `animal-sniffer`, `parboiled-core`, `perfmark`, and `proto-google-common-protos` jars.
- Matching legal files and root `LICENSE` entries are present for the directly questioned dependencies.
- UI has no source maps; `licenses/fe-licenses` contains `43` frontend license files.
- `hugegraph-dist/release-docs` LICENSE/NOTICE/licenses are identical to Hubble dist copies.

Residual risk: exact third-party legal alignment should still be reviewed during the ASF vote, but the earlier `grpc-*`, `animal-sniffer`, `commons-collections4`, `perfmark`, and `proto-google-common-protos` over-inclusion concern is superseded by this latest inventory because those jars are present in the final candidate.

Conclusion: Subtask 04 final binary inventory is now complete for the rebuilt tarball.
