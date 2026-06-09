# Subtask 04 Implementation - Binary ASF Compliance and Hubble Dist Audit

## Implemented

- Hubble dist assembly now sources `LICENSE`, `NOTICE`, and `licenses/**` from
  `hugegraph-dist/release-docs`.
- `hubble-dist/pom.xml` cleans stale dist directories and generated tarballs
  before packaging.
- Dist packaging removes runtime residue before creating the tarball:
  - `logs/`
  - `upload-files/`
  - H2 db files
  - root `*.pid`
  - `bin/pid`
- Startup scripts no longer leave stale pid files on failed startup.
- Legal bundle updates include additional license/notice coverage for gRPC,
  commons-collections4, parboiled, perfmark, proto-google-common-protos, and
  animal-sniffer annotations.
- `.licenserc.yaml` excludes FE generated/node directories from header checks.
- `.gitignore` keeps Node/Yarn generated directories out and keeps unreviewed
  dataset archives out of Git/release artifacts by default.

## Verification

- FE build with project-local Node.js v16.20.2 succeeded and regenerated the
  frontend build/license output used by dist packaging.
- Hubble BE unit tests passed.
- Static review found no dist assembly path that intentionally includes
  `node_modules`, Node runtime, or Yarn cache in the runtime package.

## Remaining Release Gates

- A formal Hubble binary candidate inventory has now been run against the
  rebuilt tarball. See the follow-up verification below.
- Latest Hubble binary inventory includes `hg-pd-*`, `grpc-*`,
  `commons-collections4`, `animal-sniffer`, `perfmark`, and
  `proto-google-common-protos` jars, so matching legal entries are required.
- Dataset archives remain local smoke-test inputs only unless provenance and
  redistribution terms are confirmed by a later ASF release review.

## Follow-up Binary Inventory - 2026-06-09

Checked artifacts:

- `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz`
- `hugegraph-hubble/hubble-dist/apache-hugegraph-hubble-1.7.0`

Results:

- The rebuilt tarball timestamp was `2026-06-09 10:23:55 +0800`.
- Tarball root structure is expected:
  - `LICENSE`
  - `NOTICE`
  - `README.md`
  - `bin`
  - `conf`
  - `lib`
  - `licenses`
  - `ui`
- Tarball contains `255` lib jars.
- The tarball does not contain:
  - `node_modules`
  - Node/Yarn runtime or cache
  - `logs/`
  - `upload-files/`
  - H2 database files
  - `bin/pid`
- Current expanded directory was later runtime-polluted by smoke verification and
  contains:
  - `db.mv.db`
  - `logs/hugegraph-hubble.log`
  - `bin/pid`
- The runtime residue above is not present in the tarball, so the clean release
  validation target is the tarball.
- This `10:23:55` inventory was superseded by the later `12:56:40` rebuild
  below. The later rebuild includes `hg-pd-*`, `grpc-*`, `commons-collections4`,
  `animal-sniffer`, `perfmark`, and `proto-google-common-protos` jars.
- Native files were found inside:
  - `commons-crypto-1.0.0.jar`
  - `hbase-shaded-client-byo-hadoop-2.2.3.jar`
  - `hbase-shaded-netty-2.2.1.jar`
  - `jline-2.12.jar`
  - `lz4-java-1.4.0.jar`
  - `snappy-java-1.1.8.2.jar`
- Corresponding license files are present for those native-bearing jars.
- UI inventory:
  - `.css`: `2`
  - `.html`: `1`
  - `.ico`: `1`
  - `.js`: `5`
  - `.json`: `2`
  - `.svg`: `50`
  - `.txt`: `1`
  - no source maps found
  - `ui/static/js/2.08b22988.chunk.js.LICENSE.txt` exists
  - `licenses/fe-licenses` contains `43` frontend license files
- `hugegraph-dist/release-docs/LICENSE`, `NOTICE`, and `licenses/**` are
  identical to the copies packaged into Hubble dist.

Residual risk:

- The earlier concern that `grpc-*`, `animal-sniffer`, `commons-collections4`,
  `perfmark`, and `proto-google-common-protos` were stale was based on the
  `10:23:55` inventory. The later full Hubble rebuild includes those jars, so
  their `LICENSE` entries and license text files are not stale for the latest
  candidate. Exact third-party legal alignment should still be reviewed during
  the ASF vote, but this item is no longer an identified over-inclusion issue.

## Follow-up CC Gap Closure - 2026-06-09

Command:

```bash
mvn package -DskipTests -ntp
```

Result: `BUILD SUCCESS`.

Latest checked artifact:

- `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz`
- timestamp: `2026-06-09 12:56:40 +0800`
- size: `197714261` bytes

Latest inventory results:

- Tarball contains `270` lib jars and `287` top-level `licenses/LICENSE-*.txt`
  files.
- Tarball does not contain:
  - `dataset/`
  - `logs/`
  - `upload-files/`
  - H2 database files
  - `bin/pid`
  - root `*.pid`
- The previously questioned dependencies are present in the latest binary:
  - `animal-sniffer-annotations-1.19.jar`
  - `commons-collections4-4.4.jar`
  - `grpc-api-1.39.0.jar`
  - `grpc-context-1.39.0.jar`
  - `grpc-core-1.39.0.jar`
  - `grpc-netty-shaded-1.39.0.jar`
  - `grpc-protobuf-1.39.0.jar`
  - `grpc-protobuf-lite-1.39.0.jar`
  - `grpc-stub-1.39.0.jar`
  - `hg-pd-client-1.5.0.jar`
  - `hg-pd-common-1.5.0.jar`
  - `hg-pd-grpc-1.5.0.jar`
  - `parboiled-core-1.1.8.jar`
  - `perfmark-api-0.23.0.jar`
  - `proto-google-common-protos-2.0.1.jar`
- Matching legal files are present for the directly questioned dependencies:
  - `LICENSE-animal-sniffer-annotations.txt`
  - `LICENSE-commons-collections4.txt`
  - `LICENSE-grpc-*.txt`
  - `LICENSE-parboiled-core.txt`
  - `LICENSE-perfmark-api.txt`
  - `LICENSE-proto-google-common-protos.txt`
- `LICENSE` contains entries for the same directly questioned dependencies.

Dataset disposition:

- `dataset/hlm.zip` and `dataset/movie 2.zip` are treated as local smoke-test
  inputs only.
- `.gitignore` keeps unreviewed dataset zip archives out of Git/release
  artifacts by default.
- `README.md` and `dataset/README.md` now state that dataset archives require
  provenance, license, and redistribution review before they can be used as ASF
  release artifacts.

## Follow-up Dataset Provenance Audit - 2026-06-09

Checked local archives:

- `dataset/hlm.zip`
  - SHA-256:
    `c1286d5d1cb07e635867b784bcdecd41fb744ce846b32906400f259d151a0ec0`
  - Contents: `hlm/hlm.txt`, `hlm/schema_hlm.groovy`,
    `hlm/struct_hlm.json`.
- `dataset/movie 2.zip`
  - SHA-256:
    `6d7ad7b75b87c5fb3babbb3b6f38caae2cd2d816cf0776e55d2351cfe1b0a0b4`
  - Contents: `movie/movie.csv`, `schema_movie*.groovy`,
    `struct_movie*.json`.

Evidence:

- `git ls-files dataset` is empty for these archives.
- Repository history does not record their source or contribution provenance.
- Archive contents have no source URL, copyright statement, license,
  permission grant, or redistribution terms.
- `.gitignore` keeps `dataset/*.zip` out of Git by default.
- The rebuilt Hubble tarball does not contain `dataset/`.

ASF conclusion:

- These archives cannot be included in ASF release artifacts in the current
  state.
- They may be used only as local smoke-test inputs.
- If bundled sample data is needed later, prefer a small synthetic dataset with
  clear Apache-2.0 provenance and transparent text/JSON/Groovy source files.
