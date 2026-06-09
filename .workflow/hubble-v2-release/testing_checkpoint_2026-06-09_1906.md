# Hubble V2.0 Testing Checkpoint - 2026-06-09 19:06 +0800

## Current State

- Working directory: `/home/looksaw/hugegraph-toolchain`.
- Hubble browser smoke and full UI acceptance were rerun successfully after
  allowing the scripts to run outside the sandbox so Hubble could bind
  `127.0.0.1:8088`.
- No `HugeGraphHubble` process remained after the reruns.
- WSL memory was not reconfigured. Peak pressure was handled within the
  existing WSL limit.

## Latest UI Reruns

### UI Browser Smoke

Command:

```bash
NODE_PATH=/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result:

- Status: `SUCCESS`
- Result files:
  - `.workflow/hubble-v2-release/hubble_ui_browser_smoke_result.json`
  - `.workflow/hubble-v2-release/hubble_ui_browser_smoke_result.md`
- Routed pages checked: `4`
- Connection count from UI API: `1`
- Console/page/request errors: `0/0/0`
- Hubble stopped cleanly.

### UI Full Acceptance

Command:

```bash
NODE_PATH=/home/looksaw/.npm/_npx/e41f203b7505f1fb/node_modules \
node .workflow/hubble-v2-release/run_ui_full_acceptance.js
```

Result:

- Status: `SUCCESS`
- Result files:
  - `.workflow/hubble-v2-release/hubble_ui_full_acceptance_result.json`
  - `.workflow/hubble-v2-release/hubble_ui_full_acceptance_result.md`
- Selected graph connection: `1/AlgorithmApiInventory/hugegraph`
- Backend snapshots: import jobs `0`, async tasks `92`, property keys `123`,
  vertex labels `42`, edge labels `29`, vertex indexes `3`
- Covered graph management, data analyze, sidebar navigation, metadata configs,
  data import, async tasks, desktop/mobile rendering, and error collection.
- Console/page/request/severe errors: `0/0/0/0`
- Import detail view was skipped because the current Hubble metadata had no
  `SUCCESS` or `FAILED` import job.
- Hubble stopped cleanly.

## Aggregate Result Status

All current `.workflow/hubble-v2-release/hubble_*_result.md` files report
`SUCCESS`:

- Algorithm API inventory
- Live Hubble smoke
- Movie dataset smoke
- UI browser smoke
- UI full acceptance
- UI i18n switch smoke
- UI served smoke

## Resume Point

No unfinished test gate is known at this checkpoint.
