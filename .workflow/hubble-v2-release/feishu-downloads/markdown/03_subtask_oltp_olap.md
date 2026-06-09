<title>Subtask 03 - OLTP / OLAP 功能验证方案</title>

> 本页定义 Hubble V2.0 的功能验证范围。本文不执行导入、查询或算法测试；它只写正式 candidate 验证时的流程、矩阵、归因和出口条件。

## 1. 目标

Subtask 03 要证明或界定：

| 目标 | 说明 |
|-|-|
| OLTP 核心链路 | 图连接、Schema、数据上传、映射配置、加载、Gremlin 查询、图可视化 |
| 图算法 API | Hubble 暴露的 shortestPath 能返回 json/table/graph 视图 |
| OLAP / Cypher 边界 | Hubble UI / BE 是否真正支持；direct Server 能力单独记录 |
| 数据集策略 | 只有进入 release PR 且被 Git 跟踪的数据集才能作为内置数据集验证对象 |
| 失败归因 | 区分 Hubble bug、Server bug、配置问题、数据问题、产品边界 |

## 2. 代码与 API 依据

| 域 | Hubble 入口 | 代码依据 |
|-|-|-|
| 图连接 | `/api/v1.2/graph-connections` | `GraphConnectionController` / `GraphConnectionService` |
| Schema | `/schema/propertykeys`、`/schema/vertexlabels`、`/schema/edgelabels` | schema controllers / services |
| 上传 | `/job-manager/{jobId}/upload-file` | `FileUploadController` |
| 文件映射 | `/file-mappings`、`file-setting`、`vertex-mappings`、`edge-mappings` | `FileMappingController` |
| 加载任务 | `/load-tasks/start`、pause、resume、stop、retry | `LoadTaskController` / `LoadTaskService` |
| Gremlin | `/gremlin-query` | `GremlinQueryController` / `GremlinQueryService` |
| 图算法 | `/algorithms/shortestPath` | `OltpAlgoController` / `OltpAlgoService` |
| 异步任务 | `/async-tasks` | `AsyncTaskController` |

## 3. 验证环境

| 项 | 要求 |
|-|-|
| Hubble | 正式 binary candidate 启动 |
| HugeGraph Server | 与 release 计划匹配的 Server commit / tag |
| 图 | 使用独立测试图，避免污染已有数据 |
| 数据集 | 只使用 release PR 中已跟踪的数据集或明确外部来源数据 |
| 浏览器 | 至少 Chromium；需要截图时记录视口 |
| 认证 | 若启用 auth，记录用户名、权限和 token 策略；不写明文凭据 |

## 4. 数据导入主流程

Hubble data import 的标准流程：

<readonly-block type="isv"></readonly-block>

关键 API 顺序：

| 步骤 | API | 判定 |
|-|-|-|
| 创建 job | `POST /graph-connections/{connId}/job-manager` | 返回 job id |
| 获取 token | `GET /job-manager/{jobId}/upload-file/token?names=<file>` | token 与文件名匹配 |
| 上传文件 | `POST /upload-file` | file mapping 完成，列名可解析 |
| 上传阶段推进 | `PUT /upload-file/next-step` | job 进入 MAPPING |
| 文件设置 | `POST /file-mappings/{id}/file-setting` | delimiter、charset、date format 生效 |
| 顶点映射 | `POST /file-mappings/{id}/vertex-mappings` | id fields、field mapping 正确 |
| 边映射 | `POST /file-mappings/{id}/edge-mappings` | source / target fields 正确 |
| 映射阶段推进 | `PUT /file-mappings/next-step` | job 进入 SETTING |
| 启动加载 | `POST /load-tasks/start?file_mapping_ids=<ids>` | load task 创建并运行 |
| 状态查询 | `GET /job-manager/{jobId}` / `GET /load-tasks/{id}` | 最终状态一致 |

## 5. 数据集策略

| 数据集类型 | 用途 | 要求 |
|-|-|-|
| 小型图谱数据集 | 快速导入、Gremlin、shortestPath、graph view | 文件进入 Git 跟踪；README 写明来源、授权、schema、预期点边 |
| 中型数据集 | 验证导入性能、分页、列表、计数、图可视化边界 | 文件进入 Git 跟踪或明确外部下载；不使用未跟踪本地文件 |
| 用户自备 CSV | 验证通用导入能力 | 不进入 release evidence，只用于扩展 smoke |

数据集进入 release 的最低文档要求：

- 文件名稳定，不含个人命名痕迹。
- 来源和授权可说明。
- Schema 定义清楚。
- 导入步骤清楚。
- 预期顶点 / 边数量由正式验证命令产生。
- 常见错误和修复方式写入 README。

## 6. OLTP 验证矩阵

| 场景 | 操作 | 通过标准 | 失败归因 |
|-|-|-|-|
| 图连接 | 创建、编辑、删除连接 | 能连接 Server，错误信息可理解 | Hubble 配置 / Server auth / 网络 |
| PropertyKey | 创建、复用、冲突检查、删除检查 | Schema API 与 UI 状态一致 | Hubble schema service / Server schema |
| VertexLabel | 创建、复用、link、optional colors | 主键、属性、索引显示正确 | Hubble mapping / Server schema |
| EdgeLabel | 创建、复用、冲突检查 | source / target、frequency、properties 正确 | Hubble schema / Server schema |
| Upload | token、分片、合并、格式限制、大小限制 | 上传成功，路径安全，非法文件拒绝 | Hubble upload API |
| File mapping | header、delimiter、date format、field mapping | 生成 Loader mapping 正确 | Hubble mapping service |
| Load task | start、pause、resume、stop、retry | 状态推进正确，错误可查看 | Hubble task / Loader / Server write |
| Gremlin vertex | `g.V().limit(n)` 类查询 | json/table/graph 视图一致 | Hubble view build / Server Gremlin |
| Gremlin edge | `g.E().limit(n)` 类查询 | 边和端点可视化正常 | Hubble 补点逻辑 / Server Gremlin |
| Gremlin scalar | `count()` 类查询 | table/json 可读；graph view 为空可接受 | Server Gremlin / Hubble 类型判断 |
| Graph expand | 从图视图扩展邻接 | 限制 degree，避免爆图 | Hubble query build / Server Gremlin |
| shortestPath | 算法 tab 或 API | json/table/graph view 都可用 | Hubble OltpAlgo / Server traverser |

## 7. Gremlin 结果视图判定

Hubble `GremlinQueryService` 会根据结果类型生成视图：

| 类型 | json view | table view | graph view |
|-|-|-|-|
| Vertex | 原始数据 | 顶点表 | 顶点及补充边 |
| Edge | 原始数据 | 边表 | 边及端点 |
| Path | path 对象 | path id 列 | path 中顶点和边 |
| General | 标量 / map / list | result 表 | 空图视图可接受 |
| Empty | 空 | 空 | 空 |

注意：

- `count()` 是标量结果，不应强行要求 graph view。
- 只有 Vertex / Edge / Path 类型才应验证 graph view 完整性。
- 如果 Server 返回错误，必须用 direct Server 请求确认是否 Hubble 引入。

## 8. 图算法验证

当前 Hubble BE 明确暴露的算法入口是 shortestPath。

| 场景 | 请求 | 判定 |
|-|-|-|
| 正常路径 | source、target、direction、label、maxDepth 等参数合法 | 返回 PATH 类型，json/table/graph view 一致 |
| source 不存在 | source id 不存在 | 返回可理解错误 |
| target 不存在 | target id 不存在 | 返回可理解错误 |
| label 为空或 all | 前端 all label 不应把内部占位值错误传给后端 | 请求参数符合真实前端行为 |
| path id 回查 | Server traverser 返回 path id | Hubble 回查顶点 / 边并组装 graph view |

## 9. OLAP / Cypher 边界

| 能力 | Hubble 支持状态 | 文档口径 |
|-|-|-|
| Gremlin OLTP 查询 | Hubble UI / BE 支持 | 进入功能验证 |
| shortestPath | Hubble BE 暴露 | 进入功能验证 |
| Cypher direct Server API | Server 能力，不是 Hubble UI 能力 | 可作为 Server direct smoke，不能写成 Hubble 支持 |
| OLAP algorithm | 若 Hubble 未暴露 UI / BE API，则不写成支持 | 记录为产品边界或 Server/API 缺口 |
| 异步 Gremlin task | Hubble 有 async task 入口 | 验证任务创建、查询、取消和结果展示 |

## 10. 失败归因规则

| 现象 | 初步归因 | 下一步 |
|-|-|-|
| Hubble API 400，Server direct 200 | Hubble 参数构造 / 校验问题 | 查 controller / service 请求体 |
| Hubble API 500，Server direct 200 | Hubble service 或 view build 问题 | 查 Hubble 日志和堆栈 |
| Hubble 与 Server direct 都失败 | Server 或数据 / schema 问题 | 进入 Subtask 05 |
| UI 显示错误但 API 成功 | FE store / component / i18n 问题 | 进入 Subtask 02 或 FE bug |
| 导入成功但 job 状态错误 | Hubble job status 聚合问题 | Subtask 01 bug 矩阵 |
| `count()` 标量失败 | Server Gremlin 执行问题概率高 | 需要 issue 或 PR |

## 11. 证据模板

执行后追加：

| 字段 | 记录要求 |
|-|-|
| Candidate | 正式 binary 文件名、commit |
| Server | Server version、commit、配置 |
| Graph | 图名、backend、是否启用 auth |
| Dataset | Git 跟踪路径、来源、授权、schema |
| Import result | job id、load task id、状态、耗时、读写数量 |
| Query result | Gremlin / algorithm 请求和响应摘要 |
| UI result | 路由、截图、视图结果 |
| Failures | 错误码、错误消息、归因、issue / PR |

## 12. 出口条件

Subtask 03 关闭条件：

- 正式 candidate 完成图连接、Schema、上传、映射、导入、Gremlin、graph view、shortestPath smoke。
- 数据集来源和授权清楚，且用于 release 的文件已进入 Git 跟踪。
- Cypher / OLAP 不夸大，direct Server smoke 与 Hubble 支持边界分开记录。
- 所有失败点完成 Hubble / Server / 配置 / 数据 / 产品边界归因。
- 需要上游处理的问题已同步到 Subtask 05。

# Subtask 03 Implementation - OLTP / OLAP Function Verification

## Implemented

- `shortestPath` now builds `graph_view` from `PathOfVertices.vertices` and  
`PathOfVertices.edges` by querying the real vertices and edges through  
`TraverserManager`.
- If `vertices` is missing from the Server response, shortestPath falls back to  
vertex ids from the returned path.
- If graph-view backfill fails, shortestPath still returns PATH json/table  
results and degrades graph_view instead of failing the entire algorithm API.
- `upload_file.format_list` now defaults to `csv,txt` in both backend and  
dist configuration.
- Upload file extension validation is case-insensitive and trims whitelist  
entries, so files like `HLM.TXT` are accepted when `txt` is allowed.
- Hubble Loader mapping now treats ID fields as scalar by default where Hubble  
does not expose unfold controls.
- Added `dataset/README.md` and README import notes for `hlm.zip` and  
`movie 2.zip`.

## Verification

Covered by Hubble BE unit tests:

- shortestPath graph view from returned ids;
- shortestPath fallback when `vertices` is missing;
- shortestPath empty graph view for empty path;
- shortestPath result preserved when graph-view lookup fails;
- upload `.TXT` accepted, unsupported and missing extensions rejected;
- load mapping uses scalar ids by default.

Command:

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`.

## Remaining Release Gates

- No live Hubble + HugeGraph Server import/query smoke was executed in this  
implementation pass.
- The dataset archives are present in the working tree but release provenance  
and redistribution terms still need final review before they can be treated as  
ASF release evidence.
- Cypher and OLAP remain documented as Server/product-boundary items unless  
Hubble exposes and verifies those APIs in the final candidate.

## Follow-up Live Verification - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

- Rebuilt binary Hubble started from `hubble-dist/apache-hugegraph-hubble-1.7.0`.
- Reused local Server connection `conn_id=1` to `127.0.0.1:8080/hugegraph`.
- Imported isolated HLM smoke data with suffix `1780976106`.
- Import job `225` finished as `SUCCESS`; load task `193` finished as `SUCCEED`.
- Hubble Gremlin counts: vertices `41`, edges `51`.
- Direct Server Gremlin counts for the same labels: vertices `41`, edges `51`.
- Hubble shortestPath returned `PATH` with graph_view vertices/edges `6`/`5`.
- Direct Server shortestPath returned vertices/edges `6`/`5`, path length `6`.
- Hubble was stopped after verification.

Conclusion: Subtask 03 live Hubble + Server import/query smoke is now complete.

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
The real Hubble + Server import/query smoke passed on the final candidate. Dataset archives remain local-only inputs unless provenance and redistribution terms are confirmed for ASF release use.
</callout>

| Check | Result |
|-|-|
| Command | `python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py` |
| Status | `SUCCESS` |
| Suffix | `1780981142` |
| Import | job `1` status `SUCCESS`, task `1` status `SUCCEED` |
| Gremlin counts | Hubble V/E `41`/`51`, Server V/E `41`/`51` |
| shortestPath | Hubble graph_view V/E `6`/`5`, Server traverser V/E `6`/`5`, path length `6` |
| Schema compare | Hubble-created property key, vertex label, and edge label were visible through direct Server schema endpoints with matching core fields |

`dataset/hlm.zip` and `dataset/movie 2.zip` are ignored by default and are not included in the Hubble binary candidate.

---

## Remaining Work Closure - Algorithm API and Dataset - 2026-06-09

<callout emoji="💡">
图算法 API 已做真实 inventory：Hubble BE 的 `shortestPath` 通过；FE 列出的 16 个其他算法 slug 当前返回 `405`，记录为 Hubble UI/API integration gap 或产品边界项，不归因为 Server traverser 失败。
</callout>

| Check | Result |
|-|-|
| Command | `python3 .workflow/hubble-v2-release/run_algorithm_api_inventory.py` |
| Status | `SUCCESS` |
| Successful endpoint | `POST /api/v1.2/graph-connections/1/algorithms/shortestPath`, result `PATH`, graph_view V/E `6/5` |
| Checked source/target | `贾太公_1780981142 -> 贾蓉_1780981142` |
| Non-success FE slugs | `shortpath`, `allshortpath`, `paths`, `rings`, `crosspoints`, `fsimilarity`, `neighborrank`, `kneighbor`, `kout`, `customizedpaths`, `rays`, `sameneighbors`, `weightedshortpath`, `singleshortpath`, `jaccardsimilarity`, `personalrank` |
| Observed status | All 16 FE slugs returned HTTP/business `405`, `Request method 'POST' not supported` |

<callout emoji="💡">
数据集来源/许可已审计：`dataset/hlm.zip` 和 `dataset/movie 2.zip` 没有可验证来源、版权归属、许可证或再分发条款，当前不能纳入 ASF release artifact。
</callout>

| Archive | SHA-256 | Disposition |
|-|-|-|
| `dataset/hlm.zip` | `c1286d5d1cb07e635867b784bcdecd41fb744ce846b32906400f259d151a0ec0` | Local smoke-test input only |
| `dataset/movie 2.zip` | `6d7ad7b75b87c5fb3babbb3b6f38caae2cd2d816cf0776e55d2351cfe1b0a0b4` | Local smoke-test input only |

Per CLAUDE.md, this pass marks and analyzes abnormal non-shortestPath algorithm API behavior; it does not implement missing Hubble algorithm routes.
