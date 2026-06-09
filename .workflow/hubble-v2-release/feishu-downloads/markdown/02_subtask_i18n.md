<title>Subtask 02 - 国际化与英文支持方案</title>

> 本页定义 Hubble V2.0 中英文支持的静态检查、运行态 smoke、布局检查和 release gate。本文不声明本轮已经执行 i18n 检查；所有命令和表格用于后续正式 candidate 验证。

## 1. 目标

Hubble V2.0 的 i18n gate 不只检查“有英文文件”，而是确认：

| 目标 | 判定 |
|-|-|
| 资源完整 | `zh-CN` 与 `en-US` key 对称，无空值、无明显拼写占位 |
| 运行态可用 | 核心路由切换英文后无 raw key、无空白文案 |
| 布局可用 | 英文长文案在桌面和移动端不截断、不重叠、不撑破核心控件 |
| 错误策略清晰 | 前端壳层文案随 locale；后端原文错误是否翻译有明确 release 策略 |
| Binary 复验 | 正式 candidate 的 `ui/` 产物包含最终 i18n 改动 |

## 2. 代码依据

| 文件 / 目录 | 含义 |
|-|-|
| `hugegraph-hubble/hubble-fe/src/i18n/index.ts` | 初始化 i18next，默认 `zh-CN`，fallback `zh-CN`，注册 `zh-CN` 与 `en-US` resource |
| `hugegraph-hubble/hubble-fe/src/i18n/resources/zh-CN/**` | 中文资源 |
| `hugegraph-hubble/hubble-fe/src/i18n/resources/en-US/**` | 英文资源 |
| `hugegraph-hubble/hubble-fe/src/components/graph-management/**` | 核心 Hubble UI 页面 |
| `hugegraph-hubble/hubble-fe/src/stores/**` | API 调用、错误消息和视图状态 |
| `hugegraph-hubble/hubble-fe/package.json` | `build` 与 license 生成脚本 |

资源文件范围：

| 资源域 | 覆盖页面 |
|-|-|
| `GraphManagementSidebar.json` | 图管理侧边栏 |
| `AsyncTasks.json` | 异步任务 |
| `addition.json` | 通用新增 / 操作 / 提示 |
| `common.json` | 通用组件和状态 |
| `ImportTasks.json` | 数据导入任务 |
| `dataAnalyze.json` | Gremlin / Algorithm / 查询结果 |

## 3. 静态检查方案

### 3.1 Key 对称

目标：中文和英文 resource 的递归 key 集完全一致。

检查方案：

```bash
# 方案命令，正式执行时输出 key diff
node <script> \
  hugegraph-hubble/hubble-fe/src/i18n/resources/zh-CN \
  hugegraph-hubble/hubble-fe/src/i18n/resources/en-US
```

判定：

| 情况 | 结论 |
|-|-|
| key 完全一致 | 进入运行态 smoke |
| 英文缺 key | release blocker 或 P1，视页面范围 |
| 中文缺 key | 说明资源结构不一致，必须修复 |
| key 存在但值为空 | P1，不能进入 sign-off |

### 3.2 Raw key 和疑似占位

检查项：

| 检查 | 示例 |
|-|-|
| 空字符串 | `""` |
| 仅空白 | `"   "` |
| raw key 值 | `"addition.common.save"` |
| 未翻译中文混入英文 | 英文 resource 中大量中文 UI 文案 |
| 调试占位 | `TODO`、`TBD`、`xxx`、`test` |

注意：后端错误码或 Server 原文错误不一定要前端翻译，必须在错误策略章节说明。

## 4. 运行态路由矩阵

正式 candidate 启动后，至少覆盖以下路由。每个路由需要中文默认态和英文态各检查一次。

| 区域 | 路由 / 页面 | 检查点 |
|-|-|-|
| 首页 / 图管理 | `/`、`/graph-management` | 创建图、访问、更多、空态、列表操作 |
| 元数据 | `/graph-management/{id}/metadata-configs` | property、vertex label、edge label、index、复用属性 |
| 数据导入 | `/graph-management/{id}/data-import/import-manager` | job 列表、上传、映射、设置、加载任务、错误日志 |
| 查询分析 | `/graph-management/{id}/data-analyze` Gremlin tab | query editor、execute、favorite、clear、json/table/graph view |
| 算法分析 | `/graph-management/{id}/data-analyze` Algorithm tab | 算法列表、参数表单、执行结果 |
| 异步任务 | `/graph-management/{id}/async-tasks` | task list、状态、取消、删除 |

## 5. Raw key 运行态判定

运行态检查不能只靠肉眼，建议增加 DOM 启发式规则：

| 规则 | 说明 |
|-|-|
| 文本包含 `addition.` / `server-` / `graph-management.` | 高概率 raw key |
| 文本为 resource key path 格式 | 高概率漏翻 |
| 按钮文本为空 | UI 语义缺失 |
| 表格列名为空 | 核心操作风险 |
| tooltip / modal title 为空 | 影响可用性 |

误报处理：

- Gremlin 查询内容、用户数据、Server 返回错误原文不按 raw key 处理。
- 误报必须记录筛除原因，不能直接删除规则。

## 6. 英文布局检查

英文比中文长，必须覆盖桌面和移动端。

| 组件 | 风险 | 判定 |
|-|-|-|
| 主按钮 | `Create Graph` 等文本较长 | 不截断、不溢出、不遮挡 icon |
| 状态标签 | `Succeeded` / `Failed` / `In Progress` | 高度和宽度自适应 |
| Tabs | 移动端 tab 文案较长 | 不被固定高度裁切 |
| 表格列头 | 列名变长 | 不和排序 / 操作按钮重叠 |
| Modal footer | Cancel / Confirm / Create 等按钮 | 不换行到不可点击状态 |
| Algorithm 参数表单 | label 较长 | label 与 input 不重叠 |
| Toast / Notification | 错误消息较长 | 不遮挡核心操作，内容可读 |

建议断点：

| 视口 | 用途 |
|-|-|
| 1440 x 900 | 桌面主场景 |
| 1280 x 720 | 窄桌面 |
| 390 x 844 | 移动端 |
| 768 x 1024 | 平板 / 竖屏 |

## 7. 错误消息语言策略

Hubble 的错误来源有三类，不能混同：

| 来源 | 示例 | 策略 |
|-|-|-|
| 前端本地校验 | 必填、格式错误、确认弹窗 | 必须 i18n |
| Hubble BE 业务错误码 | upload、job、mapping、gremlin proxy 错误 | 以 Hubble 错误码映射为主；未覆盖项记录 |
| HugeGraph Server 原文错误 | Gremlin 执行错误、Server schema / traverser 异常 | 本 release 可保留英文原文，但必须在 release note / 文档中说明 |

判定规则：

- 前端静态文案 raw key 是 i18n bug。
- Server 原文英文错误不是自动 i18n bug，但如果被前端包了一层中文壳，需要保持语言策略一致。
- 错误消息如果影响用户判断，需要增加更清晰的 Hubble 前置提示。

## 8. Binary 复验要求

i18n 修复必须在正式 binary candidate 中复验，不能只看源码。

| 检查项 | 说明 |
|-|-|
| `ui/` 来源 | 来自当前 commit 的 FE build |
| resource 文件 | build 产物中包含最终中英文资源 |
| 页面加载 | 静态资源路径正确，刷新路由不 404 |
| 语言切换 | localStorage / UI 切换生效 |
| 截图证据 | 桌面和移动端核心路由有截图或 DOM 检查报告 |

## 9. 证据模板

执行后在文档中追加：

| 字段 | 要求 |
|-|-|
| Candidate | 正式 binary 文件名和 commit |
| Static key check | key diff 摘要、空值数量、异常 key 清单 |
| Runtime routes | 覆盖路由、视口、语言 |
| Raw key result | 命中项和筛除项 |
| Layout result | 溢出 / 重叠 / 截断清单 |
| Error policy | 后端原文错误保留策略和例外 |
| Blocker | 影响 release 的 i18n 问题 |

## 10. 出口条件

Subtask 02 关闭条件：

- 中英文 resource key 对称。
- 无空翻译值。
- 核心路由中文默认态和英文态无 raw key。
- 桌面和移动端核心页面无明显英文文案溢出。
- 后端 / Server 错误消息语言策略写清楚。
- 正式 candidate 的 `ui/` 产物已复验。
- 所有 i18n 问题已同步到父文档 release gate。

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

- Runtime locale switching still needs browser smoke on the final binary  
candidate.
- Desktop/mobile screenshot or DOM overflow checks are still required for final  
i18n sign-off.
- Existing ESLint warnings remain in the FE build output; they are not newly  
introduced by this i18n fix, but should be reviewed separately if the release  
gate requires warning-free builds.

---

## CC Gap Closure - 2026-06-09

<callout emoji="💡">
Runtime i18n and layout validation passed on the final Hubble candidate through the full Playwright acceptance script.
</callout>

| Runtime Check | Result |
|-|-|
| Command | `NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules node .workflow/hubble-v2-release/run_ui_full_acceptance.js` |
| Status | `SUCCESS` |
| Mobile viewport | `390x844` data-analyze page rendered |
| Desktop viewport | `1440x960` home page rendered |
| English render | Home page checked with English locale |
| Browser issues | console `0`, page `0`, failed requests `0`, severe console `0`, severe failed requests `0` |

The script restored `localStorage.languageType` to `zh-CN` after validation.

---

## Remaining Work Closure - Runtime i18n Switch - 2026-06-09

<callout emoji="💡">
运行时中英文切换已用真实浏览器补测通过：通过页面 AppBar 语言选择器执行 `zh-CN -> en-US -> zh-CN`，不是只改 `localStorage`。
</callout>

| Item | Result |
|-|-|
| Command | `NODE_PATH=/tmp/hubble-ui-smoke-playwright/node_modules node .workflow/hubble-v2-release/run_ui_i18n_switch_smoke.js` |
| Status | `SUCCESS` |
| Flow | `zh-CN -> en-US -> zh-CN` through visible AppBar selector |
| Text check | `图管理 / 创建图` changed to `Graph Manager / Create Graph`, then back to Chinese |
| Browser issues | console `0`, page `0`, failed requests `0`, severe console `0`, severe failed requests `0` |
| Screenshots | `.workflow/hubble-v2-release/ui-i18n-switch-screenshots/` |

Hubble was stopped after the script: `stopped HugeGraphHubble`.
