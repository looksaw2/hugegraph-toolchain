<title>Subtask 05 - Server / API 欠缺确认方案</title>

> 本页用于确认 Hubble V2.0 对 HugeGraph Server 的依赖，以及失败点到底是 Hubble bug、Server bug、配置问题、数据问题还是产品边界。本文不执行 API 请求；它定义后续验证和 issue / PR 闭环方案。

## 1. 目标

Subtask 05 的核心产出是 API 依赖矩阵和缺口处置矩阵：

| 目标 | 说明 |
|-|-|
| 梳理 Hubble API | 明确 Hubble BE 对外暴露哪些能力 |
| 梳理 Server 依赖 | 明确 Hubble 每个能力依赖 Server 哪类 API 或行为 |
| 失败归因 | 将失败划分为 Hubble、Server、配置、数据、产品边界 |
| 缺口闭环 | 每个 Server 侧问题有 issue、PR、release note 或 blocker 结论 |
| 防止夸大 | Hubble 未暴露的 Cypher / OLAP 能力不能写成已支持 |

## 2. Hubble API 分域

| 域 | Hubble endpoint | 主要职责 |
|-|-|-|
| Graph connection | `/api/v1.2/graph-connections` | 保存 Server 连接、认证、图名、SSL 配置 |
| Schema | `/schema/propertykeys`、`/schema/vertexlabels`、`/schema/edgelabels`、`/schema/graphview` | 创建、复用、检查冲突、展示图模型 |
| Graph data | `/graph/vertex`、`/graph/edge` | 添加或更新顶点 / 边 |
| Upload | `/job-manager/{jobId}/upload-file` | token、分片上传、合并、格式和大小限制 |
| File mapping | `/file-mappings` | 文件设置、顶点映射、边映射、load 参数 |
| Load task | `/load-tasks` | 启动、暂停、恢复、停止、重试、失败原因 |
| Gremlin query | `/gremlin-query` | 同步查询、异步任务、展开邻接 |
| Gremlin collection | `/gremlin-collections` | 收藏查询 |
| Execute history | `/execute-histories` | 查询历史 |
| Algorithm | `/algorithms/shortestPath` | shortestPath |
| Async task | `/async-tasks` | Server 异步任务查询和取消 |

## 3. Server 依赖矩阵

| Hubble 能力 | Server 依赖 | 验证点 | 失败影响 |
|-|-|-|-|
| 图连接 | REST / Gremlin endpoint、auth、graph name | 连接成功、错误可理解 | 无法使用 Hubble |
| Schema 创建 | PropertyKey / VertexLabel / EdgeLabel API | schema 与 UI 状态一致 | 无法导入或查询 |
| Graph view schema | Server schema list / graph metadata | 图模型展示完整 | UI 元数据页受影响 |
| 数据写入 | HugeGraph Loader -> Server batch insert | 顶点 / 边写入成功 | 数据导入不可用 |
| Gremlin 查询 | Server Gremlin API | vertex / edge / path / scalar 正常返回 | 查询分析不可用 |
| Graph expand | Gremlin path 查询 | degree limit 生效，返回 path | 图可视化不可用 |
| Async task | Server task API | task list、cancel、status | 异步查询不可用 |
| shortestPath | Server traverser shortestPath | path id、vertices、edges 语义稳定 | 算法页不可用 |
| Cypher direct | Server Cypher API | direct smoke 可以执行 | 不等同 Hubble UI 支持 |
| 错误消息 | Server exception payload | Hubble 能展示可理解错误 | 影响用户定位 |

## 4. 缺口判定模型

<readonly-block type="isv"></readonly-block>

判定规则：

| 结论 | 条件 | 处理 |
|-|-|-|
| Hubble bug | Hubble 请求体错误、参数丢失、view build 错误、状态聚合错误 | 修 Hubble，补单测，回到 Subtask 01 / 03 |
| Server bug | direct Server 请求也失败，且配置 / 数据合理 | 创建或关联 Server issue / PR |
| 配置问题 | schema、label index、auth、date format、header 等配置不符合要求 | README / 文档补充，不作为 release bug |
| 数据问题 | 数据集字段、编码、授权、文件名不符合 release 要求 | 数据集 PR 修正或退出 release scope |
| 产品边界 | Hubble 没有 UI / BE API 暴露该能力 | 文档说明，不写成支持 |
| Release blocker | 影响核心功能且无修复、无 issue、无规避 | 阻断 release |

## 5. 重点问题处置

### 5.1 shortestPath graph view

| 项 | 内容 |
|-|-|
| 现象 | shortestPath 有 path 结果，但 Hubble graph view 可能为空或缺边 |
| Server 行为 | traverser 返回 path id / vertices / edges 结构 |
| Hubble 责任 | 使用返回 id 回查顶点和边，组装 `GraphView` |
| 验证 | 正常路径、source 不存在、target 不存在、label / direction 参数 |
| 结论要求 | 正式 candidate 中通过；失败归入 Hubble Function Gate |

### 5.2 Gremlin scalar `count()`

| 项 | 内容 |
|-|-|
| 现象 | 某些边标签 `count()` 可能触发 Server 错误 |
| Hubble 行为 | `count()` 是 GENERAL / scalar，不进入 graph view build |
| Server 依赖 | Server Gremlin 对边标签计数的执行语义 |
| 验证 | Hubble Gremlin 与 direct Server Gremlin 对比 |
| 结论要求 | 若 Server 未修，必须有真实 issue、影响范围和规避方式 |

### 5.3 Upload / data import

| 项 | 内容 |
|-|-|
| Hubble 责任 | token、格式白名单、分片、路径安全、file mapping、Loader options |
| Server 责任 | batch insert、schema 约束、写入错误 |
| 配置风险 | header、date format、id strategy、label index、auth |
| 验证 | 小型数据集和中型数据集分别跑上传、映射、导入、查询 |
| 结论要求 | 内置数据集必须能开箱导入；否则为 P1 |

### 5.4 Cypher / OLAP

| 项 | 内容 |
|-|-|
| direct Server Cypher | 可作为 Server smoke |
| Hubble UI Cypher | 若没有 UI / BE endpoint，不写成 Hubble 支持 |
| OLAP algorithm | 若 Hubble 未暴露，不纳入 Hubble release 功能通过项 |
| 文档口径 | “Server direct 能力”和“Hubble 产品能力”分开写 |

### 5.5 Async task

| 项 | 内容 |
|-|-|
| Hubble 入口 | `/async-tasks` 与 Gremlin async-task |
| Server 依赖 | task list、cancel、status |
| 验证 | 创建异步 Gremlin、查询状态、取消、删除 UI 记录 |
| 风险 | Server task 状态和 Hubble execute history 不一致 |

## 6. Issue / PR 规则

| 情况 | 要求 |
|-|-|
| Server bug 影响 release | 必须创建或关联 apache/hugegraph issue |
| 已有 Server PR | 写 PR 链接、review 状态、是否需要 merge 后复验 |
| Hubble bug | 写 Toolchain PR、测试覆盖和 candidate 复验 |
| accept-with-known-issue | 必须有 issue 链接、影响范围、规避方式、release note |
| 无 issue 的严重问题 | 不能按 known issue 关闭 |
| 产品边界 | 写入 README / release note，避免误导用户 |

## 7. API 证据模板

执行后每个问题按以下格式记录：

| 字段 | 要求 |
|-|-|
| Scenario | 功能场景，例如 Gremlin edge count |
| Hubble request | endpoint、method、请求体摘要 |
| Hubble response | status、错误码、响应摘要 |
| Direct Server request | 若适用，记录 direct Server 请求摘要 |
| Direct Server response | status、错误摘要 |
| Data / schema | 使用的数据集、schema、关键配置 |
| Attribution | Hubble / Server / config / data / boundary |
| Action | PR / issue / README / release note / blocker |

## 8. 与其他子任务的接口

| 来源 | 输入到 Subtask 05 |
|-|-|
| Subtask 01 | 编译、启停、已知 bug、单测结果 |
| Subtask 02 | 英文错误策略、前端展示问题 |
| Subtask 03 | 功能 smoke 失败和 direct Server 对比 |
| Subtask 04 | binary 审计中发现的 Server dependency / license 影响 |

输出到父文档：

- Server PR gate。
- Server issue / known issue gate。
- 产品边界说明。
- Release blocker 清单。

## 9. 出口条件

Subtask 05 关闭条件：

- Hubble 依赖 Server 的 API / 行为有完整矩阵。
- 每个失败点都有 direct Server 对比或明确不适用原因。
- 每个问题完成 Hubble / Server / 配置 / 数据 / 产品边界归因。
- Server 侧 release 影响项都有 issue 或 PR。
- Hubble 不支持的能力没有被写成 release 已支持。
- Known issue 都有真实 issue、影响范围、规避方式和 release note 口径。

# Subtask 05 Implementation - Server / API Gap Confirmation

## Implemented

- Hubble `shortestPath` API now treats Server traverser response ids as ids and  
backfills graph view objects explicitly.
- Graph view backfill failures are isolated from json/table path results, which  
helps distinguish Hubble view assembly problems from Server traverser results.
- Job manager status refresh now runs on:

  - single job `get`;
  - id-list `list`;
  - paged `list`.
- Empty load task lists keep a `LOADING` job in `LOADING` instead of marking it  
`SUCCESS`.
- Gremlin scalar `count()` remains a Server/API behavior boundary: Hubble graph  
view generation is only expected for vertex/edge/path result types.
- README server compatibility text documents that Hubble 2.0 needs matching  
Server-side APIs from the same release line, without claiming Cypher or OLAP  
support as Hubble UI capabilities.

## Verification

Covered by unit tests:

- job refresh to `SUCCESS`;
- job refresh to `FAILED`;
- job stays `LOADING` while a task is running;
- job stays `LOADING` when task list is empty;
- id-list job refresh.

Command:

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`.

## Remaining Release Gates

- Direct Server comparison requests were not executed in this pass.
- Any final candidate failure in Gremlin, shortestPath, async task, import, or  
schema flows must still be classified as Hubble / Server / config / data /  
product-boundary.
- Server-side release-impacting issues still need real apache/hugegraph issue or  
PR links before they can be accepted as known issues.

## Follow-up Server Comparison - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

Compared flows:

- Hubble schema create vs direct Server schema read.
- Hubble Gremlin count vs direct Server Gremlin count.
- Hubble shortestPath API vs direct Server traverser shortestPath.
- Hubble import orchestration vs direct Server post-import data visibility.

Observed comparison:

- Hubble-created property key `smoke_name_1780976106` was visible from direct Server schema API with matching `data_type`.
- Hubble-created vertex label `SmokePerson_1780976106` was visible from direct Server schema API with matching `id_strategy`.
- Hubble-created edge label `SmokeRelation_1780976106` was visible from direct Server schema API with matching source/target labels.
- Hubble and direct Server Gremlin counts matched: vertices `41` vs `41`, edges `51` vs `51`.
- Hubble shortestPath graph_view vertices/edges matched direct Server traverser vertices/edges: `6`/`5` vs `6`/`5`.

Classification:

- No Server/API gap was found in verified schema, Gremlin count, import data visibility, or shortestPath traverser flows.
- The previous label-count failure was configuration/data-shape related: Server rejects label count queries when label index is disabled.
- Hubble import remains a product-boundary orchestration flow: Hubble stores local job metadata and invokes HugeGraph Loader, while Server exposes the resulting graph data and schema.

Conclusion: Subtask 05 direct Server comparison verification is now complete.

## Follow-up UI Browser Verification - 2026-06-09

Command:

```bash
NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules \
node .workflow/hubble-v2-release/run_ui_browser_smoke.js
```

Result: `SUCCESS`.

- Browser: headless Chromium via Playwright.
- Home route `/` rendered with title `HugeGraph`.
- React root rendered non-empty visible content.
- UI API returned `1` graph connection.
- Main routes checked:

  - `/graph-management/1/data-analyze`
  - `/graph-management/1/metadata-configs`
  - `/graph-management/1/data-import/import-manager`
  - `/graph-management/1/async-tasks`
- Screenshots were written under `.workflow/hubble-v2-release/ui-browser-smoke-screenshots/`.
- Console errors: `0`.
- Page errors: `0`.
- Failed browser requests: `0`.
- Hubble was stopped after verification.

Conclusion: real browser first-screen rendering and major Hubble routes passed smoke verification. This is not exhaustive form-level UI acceptance.

### UI Full Acceptance - 2026-06-09

- Status: `SUCCESS`
- Browser: `Chromium` via Playwright
- Hubble URL: `http://127.0.0.1:8088`
- Selected graph connection: `1 / SmokeConn_1780972708 / hugegraph`
- Backend snapshot: import jobs=`8`, async tasks=`92`, property keys=`77`, vertex labels=`23`, edge labels=`10`, vertex property indexes=`3`
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

- First sandboxed run failed before UI validation because Java could not bind Hubble port `8088` with `SocketException: Operation not permitted`; the final run used the approved elevated command and passed.
- The script avoids destructive operations: no delete confirmations, no schema save, no graph connection save, no file upload, no import start, and no async task creation.
- Hubble was stopped by the script after the successful run: `stopped HugeGraphHubble`.

---

## CC Gap Closure - 2026-06-09

<callout emoji="💡">
Direct Server comparison passed on the final candidate. No Server/API gap was found in the verified schema, Gremlin count, import visibility, or shortestPath traverser flows.
</callout>

| Comparison | Result |
|-|-|
| Command | `python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py` |
| Status | `SUCCESS` |
| Suffix | `1780981142` |
| Schema | `smoke_name_1780981142`, `SmokePerson_1780981142`, and `SmokeRelation_1780981142` were visible through direct Server schema endpoints with matching core fields |
| Gremlin counts | Hubble V/E `41`/`51`, Server V/E `41`/`51` |
| shortestPath | Hubble graph_view V/E `6`/`5`, Server traverser V/E `6`/`5`, Server path length `6` |

Hubble import remains classified as product-boundary orchestration: Hubble stores local job metadata and invokes Loader, while Server exposes the resulting graph data and schema.

---

## Remaining Work Closure - Algorithm API Gap - 2026-06-09

<callout emoji="💡">
API gap inventory 已完成：Server-backed `shortestPath` 正常；非 shortestPath 的 FE 算法 slug 当前未被 Hubble BE POST API 接住，表现为 `405`。
</callout>

| Area | Result |
|-|-|
| Verified endpoint | `/api/v1.2/graph-connections/{connId}/algorithms/shortestPath` returned `PATH` with graph_view V/E `6/5` |
| Source/target | `贾太公_1780981142 -> 贾蓉_1780981142` |
| FE slug count | `16` non-shortestPath slugs checked |
| Observed status | All 16 returned HTTP/business `405`, `Request method 'POST' not supported` |
| Classification | Hubble UI/API integration gap or product-boundary issue; not a demonstrated Server traverser failure |

This pass records the gap for release decision-making and does not implement missing algorithm routes.
