# Subtask 02 Implementation - i18n and English Support

## Implemented

- Removed a stale commented sidebar block containing hard-coded Chinese labels.
- Replaced the hard-coded `删除` label in `CustomPath.tsx` with the existing
  i18n key `data-analyze.algorithm-forms.custom-path.delete`.
- Fixed raw-key risks by adding or correcting keys used by components:
  - `addition.newGraphConfig.create-success`
  - missing `placeholder.no-edge-types` entries for model similarity, neighbor
    rank, and personal rank
  - missing custom path `placeholder.no-property`
  - wrong `addition.range.*` references changed to `addition.menu.*`
- Polished visible English strings that were clearly mistranslated, including
  graph analysis actions, no-edge-type empty text, import failure reason labels,
  and edge endpoint check text.
- Existing layout fixes use `minWidth`, `whiteSpace: nowrap`, wrapping, and
  flexible status labels to reduce English overflow risk in buttons, status
  pills, tabs, and empty-state actions.

## Verification

Static i18n checks:

- `zh-CN` keys: `1042`
- `en-US` keys: `1042`
- Missing keys in either locale: `0`
- Empty English values: `0`
- Chinese residual in `en-US`: `0`
- TODO/TBD/xxx placeholders in `en-US`: `0`
- Static component/store `t()` keys checked: `941`
- Missing `t()` keys: `0`

Frontend build:

```bash
PATH=/home/looksaw/hugegraph-toolchain/hugegraph-hubble/hubble-fe/node:/home/looksaw/hugegraph-toolchain/hugegraph-hubble/hubble-fe/node/yarn/dist/bin:$PATH yarn build
```

Result: build succeeded and `add-license.js` completed. Running with system
Node.js v25 failed with `ERR_OSSL_EVP_UNSUPPORTED`; using the project local
Node.js v16.20.2 matches `hubble-dist/pom.xml` and succeeds.

## Remaining Release Gates

- Runtime locale switching, desktop/mobile render, and English home render have
  now been checked on the final Hubble candidate through UI full acceptance.
- Existing ESLint warnings remain in the FE build output; they are not newly
  introduced by this i18n fix, but should be reviewed separately if the release
  gate requires warning-free builds.

## Follow-up Runtime UI / i18n Verification - 2026-06-09

Command:

```bash
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_full_acceptance.js
```

Result: `SUCCESS`.

Runtime coverage:

- Mobile data-analyze viewport: `390x844`.
- Desktop home viewport: `1440x960`.
- English home render checked.
- `localStorage.languageType` restored to `zh-CN`.
- Browser console errors: `0`.
- Page errors: `0`.
- Failed requests: `0`.
- Severe console errors: `0`.
- Severe failed requests: `0`.

## Follow-up Runtime i18n Switch Smoke - 2026-06-09

Command:

```bash
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_i18n_switch_smoke.js
```

Result: `SUCCESS`.

Runtime switch coverage:

- Opened the real binary Hubble UI in headless Chromium.
- Used the visible AppBar language selector, not only `localStorage` mutation.
- Verified flow: `zh-CN -> en-US -> zh-CN`.
- `localStorage.languageType` changed to `en-US` after selecting English and
  back to `zh-CN` after selecting Chinese.
- Page text changed from `图管理 / 创建图` to `Graph Manager / Create Graph`,
  then back to Chinese.
- Browser console errors: `0`.
- Page errors: `0`.
- Failed requests: `0`.
- Severe console errors: `0`.
- Severe failed requests: `0`.
- Screenshots:
  `.workflow/hubble-v2-release/ui-i18n-switch-screenshots/`.
