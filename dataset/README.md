# HugeGraph Hubble Example Datasets

This directory is reserved for small local sample archives used by Hubble import
smoke tests. These archives are not Apache release artifacts unless their
provenance, license, and redistribution terms are reviewed and recorded for an
ASF release.

| Archive | SHA-256 | Included Files | Purpose |
|---------|---------|----------------|---------|
| `hlm.zip` | `c1286d5d1cb07e635867b784bcdecd41fb744ce846b32906400f259d151a0ec0` | `hlm/hlm.txt`, `hlm/schema_hlm.groovy`, `hlm/struct_hlm.json` | Small character relationship graph |
| `movie 2.zip` | `6d7ad7b75b87c5fb3babbb3b6f38caae2cd2d816cf0776e55d2351cfe1b0a0b4` | `movie/movie.csv`, `movie/schema_movie*.groovy`, `movie/struct_movie*.json` | Movie graph import demo |

## Release Review Notes

- Do not treat untracked local archives as ASF release evidence.
- If an archive is later proposed for release, record its source, license, and
  redistribution terms before intentionally tracking or publishing it.
- Do not publish these local archives in source releases, binary convenience
  artifacts, documentation bundles, or ASF mirrors while provenance is
  unverified.
- If provenance or license terms cannot be confirmed, keep these archives
  untracked, out of the release artifact, and use them only as local smoke-test
  inputs.
- Hubble accepts both `.csv` and `.txt` upload files by default through
  `upload_file.format_list=csv,txt`.

## Suggested Smoke Flow

1. Extract the archive locally.
2. Create the schema from the bundled `schema_*.groovy` file, or create the same
   schema in Hubble.
3. Upload the data file from Hubble's Data Import page.
4. Use the matching `struct_*.json` file as the field-mapping reference.
5. After the load finishes, verify the job status, Gremlin query result, and
   graph view.
