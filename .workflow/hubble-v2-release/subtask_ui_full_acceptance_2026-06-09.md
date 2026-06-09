### UI Full Acceptance - 2026-06-09

- Status: `SUCCESS`
- Browser: `Chromium` via Playwright
- Hubble URL: `http://127.0.0.1:8088`
- Selected graph connection: `1 / SmokeConn_1780981142 / hugegraph`
- Backend snapshot: import jobs=`1`, async tasks=`92`, property keys=`83`, vertex labels=`24`, edge labels=`11`, vertex property indexes=`3`
- Browser errors: console=`0`, page=`0`, failed requests=`0`, severe console=`0`, severe failed requests=`0`
- Screenshots captured: `31`
- Result JSON: `.workflow/hubble-v2-release/hubble_ui_full_acceptance_result.json`
- Result summary: `.workflow/hubble-v2-release/hubble_ui_full_acceptance_result.md`
- Screenshot directory: `.workflow/hubble-v2-release/ui-full-acceptance-screenshots/`

#### Covered UI Areas

- Graph management: graph list render, search/clear, create graph form open/cancel, edit graph form open/cancel, visit graph.
- Sidebar navigation: expand/collapse, route jumps to data analyze, metadata config, data import, async tasks.
- Data analyze: CodeMirror Gremlin query `g.V().limit(1)`, query result tabs, favorite popover open/cancel, execution log render, algorithm tab and shortest-path form render.
- Metadata config: list mode, property/vertex/edge/property-index tabs, vertex/edge property index tabs, property create row open/cancel, vertex create page open/cancel, edge create page open/cancel, graph mode, graph-mode create-property drawer open/cancel.
- Data import: import job list, create job modal open/cancel, existing completed job detail route, import-detail tab.
- Async tasks: task list, search/clear, filter popups, completed task result route.
- i18n and responsive: mobile data-analyze viewport `390x844`, desktop home viewport `1440x960`, English render check, restored `localStorage.languageType` to `zh-CN`.

#### Notes

- First sandboxed run failed before UI validation because Java could not bind Hubble port `8088` with `SocketException: Operation not permitted`; the final candidate run used the approved elevated command and passed.
- The script avoids destructive operations: no delete confirmations, no schema save, no graph connection save, no file upload, no import start, and no async task creation.
- Hubble was stopped by the script after the successful run: `stopped HugeGraphHubble`.
