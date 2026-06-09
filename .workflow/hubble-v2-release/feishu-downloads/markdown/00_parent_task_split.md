# Hubble V2.0 发版收尾任务拆分

> 本页是 Hubble V2.0 进入 HugeGraph Toolchain 1.8.0 发版评审前的总控文档。它不是执行报告，不声明任何未执行验证已经通过；它定义任务拆分、证据口径、release gate、跨任务依赖和最终写入规则。每个子文档承担一个可独立 review 的专题。

## 1. 文档定位

Hubble V2.0 收尾不是单纯的“能不能编译”问题，而是一个 release readiness 问题。需要同时回答：

<table><colgroup><col/><col/><col/></colgroup><thead><tr><th vertical-align="top">问题</th><th vertical-align="top">为什么必须单列</th><th vertical-align="top">归属</th></tr></thead><tbody><tr><td vertical-align="top">编译、打包、启停是否有可复现路径</td><td vertical-align="top">Hubble 后端、前端、Loader、Client 和 dist 打包是跨模块链路</td><td vertical-align="top"><cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite></td></tr><tr><td vertical-align="top">已知 bug 是否有 release disposition</td><td vertical-align="top">reviewer 需要看到每个问题是修复、接受为 known issue 还是 blocker</td><td vertical-align="top"><cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite> / <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr><tr><td vertical-align="top">中英文是否真的可用</td><td vertical-align="top">i18n 不只是资源文件存在，还包括运行态 raw key、英文长文案和错误提示策略</td><td vertical-align="top"><cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite></td></tr><tr><td vertical-align="top">OLTP / OLAP 功能边界是否清楚</td><td vertical-align="top">Hubble UI、Hubble BE proxy、HugeGraph Server direct API 不能混为一谈</td><td vertical-align="top"><cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite> / <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr><tr><td vertical-align="top">binary 包里二进制内容如何判定合规</td><td vertical-align="top">Hubble binary 本来就会包含 jar 和前端 build 产物，不能简单按“有二进制即失败”处理</td><td vertical-align="top"><cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite></td></tr><tr><td vertical-align="top">Server 是否缺 API 或行为不满足 Hubble</td><td vertical-align="top">失败点要归因到 Hubble、Server、配置、数据集或产品边界</td><td vertical-align="top"><cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr></tbody></table>

## 2. 输入来源与使用规则

| 来源 | 用途 | 使用限制 |
|-|-|-|
| 用户给出的 `rules/` 工作流 | 采用阶段化、文档驱动、研究优先、明确 gate 的任务组织方式 | 仅作为协作流程约束，不把其中未进入仓库的临时内容写成 release 事实 |
| `CLAUDE.md` | 提取用户对本轮 Hubble V2.0 收尾的目标、优先级和关注点 | 作为需求背景，不替代正式验证证据 |
| 参考 Wiki《Hubble V2.0 收尾发版 - 方案·实施·测试》 | 复用其三段式结构：方案设计、实施方案、测试方案；复用 gate、矩阵、known issue 的粒度 | 不迁移旧的本地候选件路径、旧 inventory 数字和占位模板 |
| 已跟踪 Hubble 源码与配置 | 说明 Hubble API、i18n、dist 打包、legal bundle 的事实依据 | 只能说明代码设计和预期结构，不能证明最终 candidate 已通过 |
| 正式 release candidate | 最终 sign-off 的唯一 artifact 依据 | 必须写明 candidate 文件名、生成命令、commit、检查命令和时间 |

硬规则：

- 不写占位式空表格。未闭环项必须写明原因、影响、下一步动作和是否 blocker。\`
- 不引用未纳入 Git 跟踪的生成目录、临时二进制包、临时数据集文件或个人证据目录作为 Wiki 事实。
- 所有实际数量都必须来自正式 candidate 的命令输出；未生成正式 candidate 前，不预写 jar 数、license 数、UI 文件数或截图数。
- direct Server smoke 只能说明 Server 能力，不等同于 Hubble UI 支持。
- source release 与 binary distribution 的规则分开写。本文重点关注 Hubble binary，但会保留 source/binary 的边界说明，避免审计口径混用。

## 3. 总目标

在 1.8.0 release review 前，Hubble V2.0 需要达到以下总目标：

<table><colgroup><col/><col/><col/></colgroup><thead><tr><th vertical-align="top">目标</th><th vertical-align="top">最低通过标准</th><th vertical-align="top">子文档</th></tr></thead><tbody><tr><td vertical-align="top">可编译、可打包、可启动</td><td vertical-align="top">从干净工作区按记录命令生成正式 candidate；脚本启停、健康检查和 UI 首页 smoke 可复现</td><td vertical-align="top"><cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite></td></tr><tr><td vertical-align="top">bug 有闭环口径</td><td vertical-align="top">每个已知问题都有 fix-before-release、accept-with-known-issue、server-upstream 或 release blocker 结论</td><td vertical-align="top"><cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite> / <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr><tr><td vertical-align="top">i18n 可用</td><td vertical-align="top"><code>zh-CN</code> / <code>en-US</code> key 对称，无空值；运行态核心路由无 raw key；英文长文案不破坏布局</td><td vertical-align="top"><cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite></td></tr><tr><td vertical-align="top">OLTP 核心链路可用</td><td vertical-align="top">图连接、Schema、上传、映射、导入、Gremlin 查询、graph view、shortestPath 有可复现验证方案</td><td vertical-align="top"><cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite></td></tr><tr><td vertical-align="top">OLAP / Cypher 边界清楚</td><td vertical-align="top">Hubble 未提供的 UI / proxy 不写成支持；direct Server 能力单独记录</td><td vertical-align="top"><cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite> / <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr><tr><td vertical-align="top">Binary 合规可解释</td><td vertical-align="top">jar、前端产物、图片、native-in-jar、shaded jar、license / NOTICE、运行残留均能分类</td><td vertical-align="top"><cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite></td></tr><tr><td vertical-align="top">Server API 依赖清楚</td><td vertical-align="top">Hubble 对 Server 的依赖形成矩阵，缺口有 issue / PR / release note 口径</td><td vertical-align="top"><cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr></tbody></table>

## 4. 范围与非范围

### 4.1 In Scope

| 范围 | 说明 |
|-|-|
| Hubble BE | controller -> service -> mapper 路径，尤其 graph connection、schema、load、gremlin、algorithm |
| Hubble FE | React + TypeScript + MobX + Ant Design，重点是 i18n、路由、核心页面和 build 产物 |
| Hubble dist | `hubble-dist` 的 Maven build、assembly descriptor、脚本、配置、legal bundle、`ui/` 拷贝 |
| HugeGraph Loader 集成 | Hubble data import 通过 Loader mapping 和 LoadTaskExecutor 执行 |
| HugeGraph Server 依赖 | Gremlin、schema、traverser、写入、Cypher direct smoke 的边界 |
| ASF binary 合规 | convenience binary 中 jar、UI、license、NOTICE、第三方依赖、未知二进制的分类与处置 |

### 4.2 Out of Scope

| 非范围 | 处理方式 |
|-|-|
| 本轮实际编译、启动、导入或跑测试 | 用户明确要求只写方案；所有命令只作为执行方案写入 |
| 未跟踪生成目录 | 不写入 Wiki，不作为 candidate 证据 |
| 未进入 release PR 的数据集文件 | 不作为内置数据集证据；只能写数据集进入 PR 后的验证规则 |
| Hubble 不提供的 Cypher UI | 不写成 Hubble 功能；只能写 direct Server smoke 和产品边界 |
| Source release 完整审计 | 子任务 04 会说明边界，但本轮重点是 Hubble binary distribution |

## 5. 任务依赖图

原始 Mermaid 代码：

<readonly-block type="isv"></readonly-block>

依赖解释：

- <cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite> 依赖 <cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite>：只有 Hubble candidate 能按脚本启动，导入、查询、算法 smoke 才有意义。
- <cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite> 依赖 <cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite>：binary inventory 必须基于正式 candidate，而不是源码树或临时目录。
- <cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite> 依赖 <cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite>：最终 `ui/` 必须来自包含 i18n 修复的前端 build。
- <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite> 依赖 <cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite>：功能验证中的失败点需要回流到 Server / API 依赖矩阵。

## 6. 子任务总览

<table><colgroup><col/><col/><col/><col/></colgroup><thead><tr><th vertical-align="top">子任务</th><th vertical-align="top">主要问题</th><th vertical-align="top">核心交付物</th><th vertical-align="top">Wiki</th></tr></thead><tbody><tr><td vertical-align="top"><cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite></td><td vertical-align="top">编译、单测、前端 build、打包、启停、已知 bug disposition</td><td vertical-align="top">编译路径、验证命令、bug 矩阵、候选件启停 smoke、blocker 清单</td><td vertical-align="top"><cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite></td></tr><tr><td vertical-align="top"><cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite></td><td vertical-align="top">静态 key、runtime raw key、英文长文案、错误提示语言策略</td><td vertical-align="top">i18n 静态检查、运行态路由矩阵、截图要求、release note 口径</td><td vertical-align="top"><cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite></td></tr><tr><td vertical-align="top"><cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite></td><td vertical-align="top">数据导入、Gremlin、graph view、shortestPath、Cypher / OLAP 边界</td><td vertical-align="top">数据导入流程、查询矩阵、算法 smoke、边界说明、证据模板</td><td vertical-align="top"><cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite></td></tr><tr><td vertical-align="top"><cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite></td><td vertical-align="top">Binary 中哪些可保留、哪些存疑、哪些 blocker</td><td vertical-align="top">分类规则、inventory 流程、license / NOTICE 规则、native / shaded 检查</td><td vertical-align="top"><cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite></td></tr><tr><td vertical-align="top"><cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td><td vertical-align="top">Hubble 依赖 Server 的接口和行为，失败点如何归因</td><td vertical-align="top">API 依赖矩阵、缺口判定、issue / PR 规则、known issue 出口条件</td><td vertical-align="top"><cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite></td></tr></tbody></table>

## 7. Release Sign-off 入口条件

本文只有在下列 gate 都完成后，才能从“方案文档”升级为“可支撑 release sign-off 的最终报告”。

<table><colgroup><col/><col/><col/></colgroup><thead><tr><th vertical-align="top">Gate</th><th vertical-align="top">判定标准</th><th vertical-align="top">未闭环时的处置</th></tr></thead><tbody><tr><td vertical-align="top">PR Gate</td><td vertical-align="top">Toolchain 与 Server 相关 PR 的 required checks、review 和 merge 状态明确</td><td vertical-align="top">标记为外部 release blocker，不用方案文档替代 PR 状态</td></tr><tr><td vertical-align="top">Version / Artifact Naming Gate</td><td vertical-align="top">POM version、压缩包名、解压根目录、README 版本说明一致</td><td vertical-align="top">正式 candidate 生成前不写实际 artifact 名称</td></tr><tr><td vertical-align="top">Build Gate</td><td vertical-align="top">client、loader、hubble-be、hubble-fe、hubble-dist 的构建路径可复现</td><td vertical-align="top">失败则进入 <cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite> bug 矩阵</td></tr><tr><td vertical-align="top">Function Gate</td><td vertical-align="top">图连接、Schema、上传、导入、Gremlin、graph view、shortestPath 通过正式 smoke</td><td vertical-align="top">失败点按 Hubble / Server / 配置 / 数据归因</td></tr><tr><td vertical-align="top">i18n Gate</td><td vertical-align="top">中英文 key 对称、无空值、核心路由无 raw key、英文布局无明显溢出</td><td vertical-align="top">UI 影响 release 体验时按 P1 修复；后端原文错误按策略记录</td></tr><tr><td vertical-align="top">Binary Artifact Gate</td><td vertical-align="top">candidate 中 jar、<code>ui/</code>、legal bundle、native、运行残留、未知二进制都有结论</td><td vertical-align="top">不明来源二进制、license 缺失、NOTICE 缺失、运行残留为 blocker</td></tr><tr><td vertical-align="top">Server API Gate</td><td vertical-align="top">Hubble 依赖 Server 的接口和行为都有矩阵、结论和 issue / PR</td><td vertical-align="top">无法归因的问题不能进入 release sign-off</td></tr><tr><td vertical-align="top">Known Issue Gate</td><td vertical-align="top">每个 known issue 有真实 issue 链接、影响范围、规避方式和 release note 口径</td><td vertical-align="top">没有 issue 链接的 release 影响项不能按 known issue 关闭</td></tr></tbody></table>

## 8. 当前状态矩阵

这一节用于避免把“方案文档”误读成“执行报告”。当前 Wiki 已定义 release readiness 的检查方法、证据口径和 gate，但正式 sign-off 仍依赖后续实际 candidate、PR、CI、数据集和 legal 审计结果。

<table><colgroup><col/><col/><col/><col/></colgroup><thead><tr><th vertical-align="top">Gate / 风险项</th><th vertical-align="top">当前状态</th><th vertical-align="top">影响</th><th vertical-align="top">下一步</th></tr></thead><tbody><tr><td vertical-align="top">文档体系</td><td vertical-align="top">已形成父文档 + 5 个子任务方案</td><td vertical-align="top">可作为 release review 的执行手册和评审框架</td><td vertical-align="top">后续只追加正式证据和结论，不把方案改写成已执行结果</td></tr><tr><td vertical-align="top">正式 1.8.0 candidate</td><td vertical-align="top">未在本文中声明已生成</td><td vertical-align="top">编译、启停、binary inventory、runtime smoke 不能写成已通过</td><td vertical-align="top">生成正式 candidate 后记录文件名、commit、生成命令和检查命令</td></tr><tr><td vertical-align="top">PR / CI gate</td><td vertical-align="top">本文不声明已闭环</td><td vertical-align="top">Toolchain / Server 相关 PR 状态仍是 release gate 的外部输入</td><td vertical-align="top">在最终报告中补 PR 链接、required checks、review / merge 状态</td></tr><tr><td vertical-align="top">Release 数据集</td><td vertical-align="top">本文只定义“必须进入 release PR / Git 跟踪后才能作为 release evidence”的规则</td><td vertical-align="top">未跟踪或临时数据集不能支撑 <cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite> 的最终结论</td><td vertical-align="top">数据集进入 PR 后补来源、授权、schema、预期点边和导入证据</td></tr><tr><td vertical-align="top">Binary legal 覆盖</td><td vertical-align="top">本文定义对账规则，不声明当前已经全覆盖</td><td vertical-align="top">jar、npm、native、shaded、图片、字体、NOTICE 仍需按正式 candidate 审计</td><td vertical-align="top">按 <cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite> 生成 inventory，并把 allow / review / blocker 清单写入</td></tr><tr><td vertical-align="top">Function gate</td><td vertical-align="top">本文不声明已执行功能 smoke</td><td vertical-align="top">图连接、Schema、上传、导入、Gremlin、shortestPath 的状态未形成正式 sign-off</td><td vertical-align="top">按 <cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite> 对正式 candidate 执行并记录失败归因</td></tr><tr><td vertical-align="top">i18n gate</td><td vertical-align="top">本文不声明已执行运行态检查</td><td vertical-align="top">中英文 key、raw key、英文布局仍需在正式 <code>ui/</code> 上复验</td><td vertical-align="top">按 <cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite> 记录静态检查和运行态路由结果</td></tr><tr><td vertical-align="top">Server / API gate</td><td vertical-align="top">本文只定义依赖矩阵和归因模型</td><td vertical-align="top">Server 行为缺口必须通过 direct Server 对比、issue 或 PR 闭环</td><td vertical-align="top">按 <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite> 补 issue / PR / release note / blocker 结论</td></tr><tr><td vertical-align="top">Known issue gate</td><td vertical-align="top">未绑定真实 issue 的问题不能按 known issue 关闭</td><td vertical-align="top">release review 会追问影响范围、规避方式和上游状态</td><td vertical-align="top">每个 accepted issue 必须补真实 issue 链接和 release note 口径</td></tr></tbody></table>

结论：当前文档的价值在于把“能编译”提升为“release readiness”检查体系；当前最大风险不在文档结构，而在后续执行时是否能让代码、candidate、数据集和 legal bundle 对齐这套标准。

## 9. 证据分层

| 层级 | 可以证明 | 不能证明 |
|-|-|-|
| 源码与配置 | Hubble 设计、预期打包内容、API 路由、i18n 资源组织、默认配置 | candidate 实际内容、运行成功、jar 数量、license 覆盖数量 |
| 参考 Wiki | 参考结构、历史问题、验证思路、release gate 粒度 | 当前分支或最终 candidate 已通过 |
| 正式 candidate | 最终 binary inventory、目录结构、legal bundle、启停、UI smoke | 未写命令和 commit 时不能支撑 review |
| PR / issue | 上游状态、review 状态、known issue 归属 | 不能替代本地或 CI artifact 审计 |

证据记录必须包含：

| 字段 | 要求 |
|-|-|
| Candidate | 文件名、版本、commit、生成命令 |
| 环境 | JDK、Maven、Node/Yarn 来源、OS、Server 版本 |
| 命令 | 可复制命令，不写个人绝对路径 |
| 输出摘要 | 关键状态码、关键计数、错误摘要 |
| 原始证据 | 日志、截图、命令输出、CI 链接或 artifact 链接 |
| 结论 | PASS / FAIL / 未闭环，并解释是否 blocker |

## 10. 跨任务问题矩阵

| 问题 | 初始归属 | 判定口径 | Release 影响 |
|-|-|-|-|
| shortestPath `graph_view` 为空或不完整 | Hubble BE / Server traverser 返回结构 | Hubble 需要用 path 中的 vertex / edge id 回查完整图对象；Server 返回 path id 本身不是错误 | 若正式 smoke 失败，为 Function Gate blocker |
| 上传白名单不支持文本数据 | Hubble 配置 / 上传 API | `upload_file.format_list` 的默认值需要覆盖 release 内置数据集使用的扩展名 | 若内置数据集无法开箱导入，为 P1 |
| load task 成功后 job 状态未推进 | Hubble BE job / load task 状态聚合 | JobManager 需要在查询 job 时刷新已完成任务状态 | 若 UI 长期显示 LOADING，为 P1 |
| Gremlin 边标签 `count()` 触发 Server 错误 | Server Gremlin 执行语义 | Hubble 的 suffix limit 不能保护标量 `count()`；需要 Server issue 或 release note | 有真实 issue 和影响说明可 known issue；否则 blocker |
| 英文长文案溢出 | Hubble FE i18n / CSS | 需要桌面和移动端运行态截图或 DOM 规则验证 | 影响核心流程时为 P1 |
| Cypher direct Server smoke | Server 能力 / 产品边界 | 只能证明 Server direct API，不说明 Hubble UI 支持 | 文档必须避免夸大 |
| 数据集文件未进入 Git 跟踪 | Release packaging | 只有进入 PR 并能由 `git ls-files` 确认后，才能作为内置数据集方案 | 未进入 PR 时不能写入 release evidence |

## 11. 执行阶段建议

<table><colgroup><col/><col/><col/></colgroup><thead><tr><th vertical-align="top">阶段</th><th vertical-align="top">动作</th><th vertical-align="top">输出</th></tr></thead><tbody><tr><td vertical-align="top">阶段 1: 准备</td><td vertical-align="top">确认 PR、branch、version、JDK/Maven/Node/Yarn、Server 依赖版本</td><td vertical-align="top">环境记录和 PR gate 状态</td></tr><tr><td vertical-align="top">阶段 2: 编译</td><td vertical-align="top">按 <cite doc-id="H25JwfNl5iX0lTkuT6Lc0uNpnlc" file-type="wiki" title="Subtask 01 - 编译确认与 Bug 收敛方案" type="doc"></cite> 执行 client -&gt; loader -&gt; hubble 的构建与单测路径</td><td vertical-align="top">构建日志摘要</td></tr><tr><td vertical-align="top">阶段 3: 打包</td><td vertical-align="top">生成正式 Hubble candidate，记录文件名、commit 和生成命令</td><td vertical-align="top">candidate 基础信息</td></tr><tr><td vertical-align="top">阶段 4: 启停</td><td vertical-align="top">用 candidate 内 <code>bin/start-hubble.sh</code> / <code>bin/stop-hubble.sh</code> 做 smoke</td><td vertical-align="top">健康检查、pid、日志摘要</td></tr><tr><td vertical-align="top">阶段 5: 功能</td><td vertical-align="top">按 <cite doc-id="LsDowJMR0iJndKkx8EkcUnxlncg" file-type="wiki" title="Subtask 03 - OLTP / OLAP 功能验证方案" type="doc"></cite> 做图连接、Schema、导入、查询、算法验证</td><td vertical-align="top">功能矩阵</td></tr><tr><td vertical-align="top">阶段 6: i18n</td><td vertical-align="top">按 <cite doc-id="FhnQwuizHiyiaFksHm3cMbhXnyf" file-type="wiki" title="Subtask 02 - 国际化与英文支持方案" type="doc"></cite> 做静态和运行态检查</td><td vertical-align="top">key 对称报告、截图或 DOM 检查结果</td></tr><tr><td vertical-align="top">阶段 7: Binary 审计</td><td vertical-align="top">按 <cite doc-id="DKe6wlE4AiREYjkxCdocPYgdnGf" file-type="wiki" title="Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案" type="doc"></cite> inventory 和 legal review 分类</td><td vertical-align="top">allow / review / blocker 清单</td></tr><tr><td vertical-align="top">阶段 8: API 缺口</td><td vertical-align="top">按 <cite doc-id="E8chwMzBJiWrW8kneIOcYPC5nxf" file-type="wiki" title="Subtask 05 - Server / API 欠缺确认方案" type="doc"></cite> 对失败点归因</td><td vertical-align="top">issue / PR / release note 矩阵</td></tr><tr><td vertical-align="top">阶段 9: Sign-off</td><td vertical-align="top">汇总所有 gate，形成最终结论</td><td vertical-align="top">release readiness 报告</td></tr></tbody></table>

## 12. 父文档维护规则

- 子文档可以记录执行细节，父文档只记录 gate、依赖、跨任务结论和最终状态。
- 子文档出现 blocker 时，父文档的对应 gate 必须同步更新。
- 任何数量和路径必须能从正式 candidate 或 Git 跟踪文件复现。
- 任何 known issue 必须绑定真实 issue 链接、影响范围和规避方式。
- 不能把参考 Wiki 的旧 runtime 结果直接复制为本轮结论。
- 不能把工作区未跟踪文件写成 release 输入。

## 13. 当前文档结论

本轮交付的是“可执行的 release 收尾方案 + 子任务文档结构”，不是最终执行结果。下一次实际发版验证时，按五个子任务逐项填入正式 candidate 证据、PR 状态、CI 链接、issue 链接和最终 sign-off 结论。

---

## Final Traceability Review - 2026-06-09

<callout emoji="💡">
本节修正 CC 汇总中容易误读的地方：Hubble release-readiness 核心链路已验证，但算法支持范围不能写成所有 UI 算法均通过。
</callout>

| Requirement | Result | Release Interpretation |
|-|-|-|
| Build / package / start | Complete | Core build gate satisfied |
| Dataset import and Gremlin query | Complete | HLM smoke imported `41` vertices and `51` edges; Hubble and Server counts matched |
| Graph algorithm API | Inventoried | `shortestPath` works; 16 additional FE slugs return `405` and must be treated as unsupported/product-boundary unless implemented |
| ASF binary compliance | Complete | Final Hubble tarball has no `dataset/`, runtime residue, source maps, Node/Yarn runtime, or unknown binary residue |
| Runtime i18n | Complete | English render and real AppBar `zh-CN -> en-US -> zh-CN` switch passed with zero browser errors |
| Dataset provenance | Audited | `dataset/hlm.zip` and `dataset/movie 2.zip` are local-only smoke inputs and are excluded from ASF release artifacts while provenance is unverified |

### Corrections to CC Summary

- `dataset/*.zip` is not tracked by Git in this workspace; `git ls-files dataset` is empty and `.gitignore` ignores `dataset/*.zip`.
- The Hubble tarball does not include `dataset/`.
- The algorithm gate should be phrased as `shortestPath` passed plus non-shortestPath API gap recorded, not as all algorithms passed.
- "No release blocker" is valid for the verified release scope only: build, start, import, Gremlin, `shortestPath`, UI smoke, i18n, and binary compliance.

### Release Note Items

- State that Hubble algorithm API verification covers `shortestPath`.
- Do not claim support for additional algorithm frontend forms/routes until matching Hubble backend APIs are implemented and verified.
- State that local dataset archives are excluded from source and binary release artifacts until provenance and license terms are reviewed.

---

## Release Note / Vote Thread Snippet - 2026-06-09

<callout emoji="✅">
本节把最终可发版口径落成可直接复制到 release note 或 vote thread 的片段，避免把未实现的算法入口描述成已支持能力。
</callout>

### Suggested Release Scope Wording

Hubble V2.0 release-readiness was verified for the core management flow: build/package/start, graph connection, schema creation, HLM dataset import, Gremlin count queries, the Hubble `shortestPath` backend algorithm API, major UI routes, runtime zh-CN/en-US language switching, and binary artifact compliance.

### Verification Evidence

| Area | Result |
|-|-|
| Hubble unit tests | `mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp` passed with `15` tests |
| Live import/query smoke | HLM smoke imported `41` vertices and `51` edges; Hubble and direct Server counts matched |
| Algorithm API | `POST /api/v1.2/graph-connections/{connId}/algorithms/shortestPath` returned `PATH` with graph view V/E `6/5` |
| UI browser smoke | Major graph-management routes rendered with `0` console errors, `0` page errors, and `0` failed requests |
| UI full acceptance | `31` screenshots captured across desktop/mobile flows with no severe browser failures |
| Runtime i18n | AppBar switch `zh-CN -> en-US -> zh-CN` passed with no browser errors |
| Binary inventory | Final Hubble tarball had `270` lib jars and `287` license files; no `dataset/`, source maps, Node/Yarn runtime, logs, pid files, upload files, or H2 runtime database files |

### Required Known Scope Notes

- Hubble algorithm API verification covers `shortestPath` only.
- The additional frontend algorithm slugs checked in this candidate returned HTTP/business `405` from Hubble BE and should not be claimed as release-supported Hubble backend APIs unless matching routes are implemented and verified.
- `dataset/hlm.zip` and `dataset/movie 2.zip` are local smoke-test inputs only. They are ignored/untracked in this workspace and excluded from ASF source and binary release artifacts until provenance, copyright ownership, license, and redistribution terms are reviewed.
- Final ASF vote review should still validate the source release, signatures, checksums, LICENSE, NOTICE, and dependency license alignment for the actual release candidate artifact.

### Suggested Vote Thread Paragraph

> For Hubble V2.0, this candidate has been smoke-tested through build/package, startup, HLM import, Gremlin query comparison with HugeGraph Server, `shortestPath` algorithm API verification, UI browser acceptance, runtime i18n switching, and binary inventory checks. The verified Hubble algorithm API scope is `shortestPath`; additional frontend algorithm forms are treated as product boundary items until corresponding Hubble backend routes are implemented and verified. Local dataset archives under `dataset/` are not part of the source or binary release artifacts.
