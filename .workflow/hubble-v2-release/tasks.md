# 收尾计划: Hubble V2.0 / 1.8.0 Release Gate

最后核对日期: `2026-06-03`

## 1. 当前结论

- 本地计划内验证已完成，可作为 Hubble V2.0 的本地验证基线。
- 仍存在发版阻塞项，当前还不能直接下“可进入 1.8.0 发版流程”的结论。
- 阻塞项分为两类:
  - 外部 gate: PR review / merge / CI、最终 source / binary artifact 复验
  - 本地补项: i18n runtime gate（英文溢出 / 错误消息策略）、shortestPath `graph_view=null` 处理口径、`hlm.zip` 上传兼容性、`movie` 边标签 `count()` 查询稳定性

## 2. 已完成基线

- [x] 全链路编译验证: `hugegraph-client`、`hugegraph-loader`、`hugegraph-hubble`
- [x] 二进制包打包、解压、目录结构检查
- [x] `start-hubble.sh` / `stop-hubble.sh` 启停链路验证
- [x] 数据导入链路验证: `dataset/movie 2.zip`
- [x] Gremlin 查询可视化验证
- [x] Schema 管理基础验证
- [x] shortestPath API 返回路径验证
- [x] i18n 静态检查: zh-CN / en-US key 对称、无空值
- [x] 工作区合规检查: `.gitignore`、`.licenserc.yaml`、`DISCLAIMER`、`assembly.xml`
- [x] `dataset/*.zip` 跟踪规则修正

## 3. 当前阻塞项

| ID | 阻塞项 | 级别 | 当前状态 | 解除条件 |
|----|--------|------|----------|----------|
| B1 | Server PR `apache/hugegraph#3008` review / merge 未闭环 | P0 | PR 仍为 `open`，`mergeable_state=clean`，check-runs 当前全绿，但未见 `APPROVED` review；`2026-05-21` 的 member review 仍指出剩余问题 | 修完 review comments，拿到 maintainer sign-off，合并到 `master` |
| B2 | Toolchain PR `apache/hugegraph-toolchain#632` CI / review 未闭环 | P0 | PR 仍为 `open`，`mergeable_state=unstable`；`2026-05-21` check-runs 中 `codecov/project`、`codecov/patch` 失败，未见 `APPROVED` review | 处理 CI / coverage 问题，必要时拆分或瘦身 PR，拿到 reviewer sign-off |
| B3 | 最终 source artifact / binary artifact 合规复验未闭环 | P0 | 已对当前本地候选 tarball 执行复验：`target/apache-hugegraph-toolchain-1.7.0.tar.gz` 扫出 596 个 `lib/*.jar`，且顶层缺 `DISCLAIMER` / `README.md`；`hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` 目录和法律文件基本齐全，但包内包含 `db.mv.db`、`logs/hugegraph-hubble.log`，且 `start-hubble.sh` 在 8088 已有健康实例时可能误报启动成功 | 清理 source / binary 候选件问题，并在最终 source tarball 和 binary tarball 上重新做二进制、法律文件、第三方内容扫描 |
| B4 | README 数据集说明待随 PR 合入并复核 | P1 | README 已在本地补齐数据集位置、导入步骤、默认映射、预期数据量与常见错误；当前剩余动作是 reviewer 复核并随 PR #632 合入 | reviewer 确认口径无误，随 PR #632 合入 |
| B5 | i18n runtime gate 未闭环 | P1 | `2026-06-03` 已用无头 Chromium 覆盖 `/`、`/graph-management`、`/graph-management/1/metadata-configs`、`/graph-management/1/data-import/import-manager`、`/graph-management/1/data-analyze` 的 Gremlin / Algorithm 视图；默认中文页与英文关键路由均未见 raw key，但 `Create Graph`、`Create Property`、`Reuse Existing Property` 按钮以及导入列表 `succeeded` 单元格存在英文溢出 / 挤压风险；后端错误消息当前保持原文 English，需明确 release 口径 | 修复英文布局问题，或明确登记 follow-up；同时在结论 / Release Note 写清“前端 UI 本地化、后端错误保持原文”的策略 |
| B6 | shortestPath `graph_view=null` 处理口径未闭环 | P1 | API 路径返回正常，但图形视图降级；是否修复 / 是否带 issue 发版尚未签字 | 二选一: 修复；或明确登记 Issue + Release Note + known issue 口径 |
| B7 | `dataset/hlm.zip` 内置文件名与默认上传白名单冲突 | P1 | `2026-06-03` runtime smoke 发现包内文件是 `hlm.txt`，但 Hubble 默认 `upload_file.format_list=csv`，直接上传报 `The upload file format is unsupported`；同内容改名为 `hlm.csv` 后导入 41 个 `人物` 顶点、51 条 `关系` 边成功 | 二选一: 将白名单放宽到 `txt`；或将内置文件改名为 `.csv` 并在 README / Release Note 明确口径 |
| B8 | `movie` 数据集边标签 `count()` 查询触发 `Big id max length` | P1 | `2026-06-03` runtime rerun（schema `open_label_index=true`）中，`movie.csv` 导入任务 `SUCCEED`；`g.V('0.5毫米')`、`g.V().hasLabel('电影').limit(3)`、`g.E().hasLabel('属于')` 均成功，只有 `g.E().hasLabel('属于').count()` 失败。定位显示异常在 `GremlinQueryService.executeGremlin()` 抛出，`count()` 结果类型是 `GENERAL`，不会进入 `buildGraphView()`；同时 Hubble 的 `gremlin.suffix_limit=250` 只会补到以 `.E()` / `.V()` / `.hasLabel()` 结尾的语句，`count()` 会绕过该保护并在 HugeGraph 执行阶段触发超长 edge-id 查询 | 需要确认按 HugeGraph server 侧 known issue 处理还是补上游修复；若本轮不修，需在 release note / known issue 中明确写清 |

### 3.1 发版出口定义

| Gate | 判定标准 | 当前状态 | 备注 |
|------|----------|----------|------|
| PR Gate | `apache/hugegraph-toolchain#632` required checks 全绿并 merged；`apache/hugegraph#3008` 获得 maintainer sign-off 并 merged | 未闭环 | 当前仍是 release readiness 最大外部阻塞项；详细见 B1/B2 与详细报告 `PR 状态 Gate` |
| Server API / 依赖 Gate | Hubble V2 使用到的上游 Server 能力已矩阵化，且每项都能说明“本地验证结果”与“上游依赖关系” | 已补矩阵 | 见详细报告 `Server API / Release 依赖矩阵` |
| Source Artifact Gate | 最终 source tarball 不含二进制泄漏，法律文件齐全，README 与源码一致 | 当前本地候选件复验失败 | `target/apache-hugegraph-toolchain-1.7.0.tar.gz` 扫出 596 个 `lib/*.jar`，顶层仅见 `LICENSE` / `NOTICE`，缺 `DISCLAIMER` / `README.md`；当前候选件不能视为 ASF source release；详细见 B3 与 `Artifact 审计与证据要求` |
| Binary Artifact Gate | 最终 binary tarball 目录完整，法律文件齐全，`ui/` 产物、脚本、README 与交付内容一致 | 当前本地候选件部分通过 | `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` 含 `bin/`、`conf/`、`lib/`、`ui/` 与 `LICENSE` / `NOTICE` / `DISCLAIMER` / `README.md`，`/actuator/health` 返回 200；但包内仍带 `db.mv.db`、`logs/hugegraph-hubble.log`，且 `start-hubble.sh` 在已有健康实例场景下可能写入失效 pid 并误报成功；详细见 B3 与 `Artifact 审计与证据要求` |
| Known-Issue Gate | 所有已知问题均有最终 disposition：`fix-before-release`、`accept-with-known-issue` 或 `defer-and-block` | 部分完成 | 见 B5/B6/B7/B8 与详细报告 `已知问题处置矩阵` |

通过标准:
- 只有上述 5 个 gate 全部满足，本文才能从“本地验证报告 + 收尾方案”升级为“可支撑 release sign-off 的终稿”。
- 若 `PR Gate` 或 `Source Artifact Gate` / `Binary Artifact Gate` 未闭环，无论本地 smoke 多完整，都不能给出“已满足 ASF 发版基本需要”的结论。

## 4. 收尾执行计划

### 阶段 A: 外部 Gate 闭环

- [ ] A1. 跟进 Server PR `#3008`
  - 目标: 消除当前 review comments，拿到 maintainer 明确通过
  - 退出条件: PR merged

- [ ] A2. 跟进 Toolchain PR `#632`
  - 目标: 先消除 CI / coverage 红灯，再处理 review
  - 退出条件: required checks 全绿，review 通过，PR merged

### 阶段 B: 发版材料补齐

- [x] B1. README 补齐 Hubble V2 数据集说明
  - 已本地补齐:
    - `dataset/hlm.zip`、`dataset/movie 2.zip` 的位置
    - 导入步骤
    - 默认字段映射
    - 预期顶点 / 边数量
    - 常见错误: `has_header`、`date_format`
  - 待完成:
    - reviewer 复核
    - 随 PR #632 合入

- [ ] B2. i18n runtime gate 收尾
  - [x] 中文默认页无 raw key
  - [x] 切换英文后主导航、图管理、数据导入、查询、算法页无缺失文案
  - [ ] 错误提示和后端返回消息有可接受语言策略
  - [ ] 英文长文案在按钮、表格、弹窗中不溢出

  执行矩阵:

  | 区域 | 入口 / 路由 | 必查项 | 记录 |
  |------|-------------|--------|------|
  | 应用框架 | `/`、`/graph-management` | 中文默认页无 raw key；English 后顶部导航、侧边栏无残留中文 | `[x]` |
  | 图管理 | `/graph-management`、`/graph-management/:id/metadata-configs` | 图列表、创建图弹窗、元数据页标题 / 按钮 / 表头无缺失文案 | `[ ]` `Create Graph` / `Create Property` / `Reuse Existing Property` 检出英文宽度不足 |
  | 数据导入 | `/graph-management/:id/data-import/import-manager/:rest*` | 上传、映射、加载、完成页文案完整；错误日志入口可理解 | `[ ]` 导入列表 `succeeded` 状态单元格检出高度挤压 |
  | 查询 | `/graph-management/:id/data-analyze` 的 Gremlin tab | 查询模式 / 任务模式文案完整；校验错误提示语言可接受 | `[x]` 文案完整、无 raw key |
  | 算法 | `/graph-management/:id/data-analyze` 的 Algorithm tab | 算法表单字段、提示文案、结果区域无 raw key；长英文不溢出 | `[x]` 本轮走查范围内未见 raw key / overflow |
  | 任务结果 | `/graph-management/:id/async-tasks`、`/graph-management/:id/async-tasks/:taskId/result` | 任务类型、状态、结果页标题和表格无缺失文案 | `[ ]` |
  | 错误策略 | 任意连接失败 / 导入失败 / 非法查询场景 | 前端校验消息本地化；后端返回消息要么本地化，要么保持统一可接受策略 | `[ ]` 当前观察到后端错误保持原文 English，需显式确认是否接受 |

  通过标准:
  - 每项至少在 zh-CN 和 en-US 各走查一次
  - 若发现 raw key、空白文案、文本溢出，必须截图并登记到 release follow-up
  - 若后端错误暂不做多语言，需在结论中明确写成“后端错误消息保持原文，前端提示已本地化”

- [ ] B3. shortestPath 已知问题口径闭环
  - 方案 1: 修复 `graph_view=null`
  - 方案 2: 不修，但必须补 Issue、Release Note、已知问题说明

- [ ] B4. 处理 `hlm.zip` 包内 `hlm.txt` 与上传白名单冲突
  - 方案 1: 默认 `upload_file.format_list` 放宽到 `csv,txt`
  - 方案 2: 将内置文件改名为 `hlm.csv`
  - 方案 3: README / Release Note 明确说明需要改名或改配置

- [ ] B5. 处理 `movie` 数据集边标签 `count()` 查询 `Big id max length`
  - 最少确认:
    - 已确认不属于 Hubble `graph_view` 放大逻辑，失败点在 HugeGraph 执行阶段
    - `g.E().hasLabel('属于')` 是否继续依赖 Hubble `suffix_limit=250` 作为查询台保护
    - 若本轮不修，需按 server-side known issue 登记到 release note

### 阶段 B 的交付物要求

- B2 需输出:
  - 英文态溢出截图或修复前后对比
  - “前端本地化 / 后端错误原文”是否接受的 release 口径
- B3 / B4 / B5 需输出:
  - 每个问题的最终 disposition：`fix-before-release`、`accept-with-known-issue` 或 `defer-and-block`
  - 对应 GitHub Issue / PR / Release Note 落点
- 任一问题若选择 `accept-with-known-issue`:
  - 必须能指出用户影响面
  - 必须有可执行绕行方式
  - 必须在 release note 中可见

### 阶段 C: Artifact 审计

- [x] C1. 当前本地候选 source tarball 复验
  - 结果: `target/apache-hugegraph-toolchain-1.7.0.tar.gz` 扫出 596 个 `lib/*.jar`，顶层只有 `LICENSE` / `NOTICE`，缺 `DISCLAIMER` / `README.md`
  - 判定: 当前候选件不满足 ASF source release 要求
  - 后续: PR merge 后仍需对最终 source tarball 重跑

- [x] C2. 当前本地候选 binary tarball 复验
  - 结果: `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` 含 `bin/`、`conf/`、`lib/`、`ui/` 与 `LICENSE` / `NOTICE` / `DISCLAIMER` / `README.md`，`ui/index.html` 可访问且 `/actuator/health` 返回 200
  - 风险: 包内同时带 `db.mv.db`、`logs/hugegraph-hubble.log`；`start-hubble.sh` 在已有健康实例场景下可能写入失效 pid 并误报启动成功
  - 后续: 清理打包残留并在最终 binary tarball 上重跑

## 5. 优先级顺序

1. 先闭环 `A1` + `A2`
2. 并行完成 `B2` + `B3` + `B4` + `B5`
3. 最后执行 `C1` + `C2`

## 6. 当前判断

### 仍然阻塞

- `apache/hugegraph-toolchain#632`
- `apache/hugegraph#3008`
- 最终 source / binary artifact 合规复验
  - 当前本地候选件结果: source 失败；binary 仅部分通过，仍需清理打包残留并在最终件上重跑
- i18n runtime gate（英文溢出 / 错误消息策略）
- `dataset/hlm.zip` 内置 `hlm.txt` / 上传白名单冲突
- `movie` 数据集边标签 `count()` 查询 `Big id max length`

### 不单独阻塞但必须有口径

- shortestPath `graph_view=null`
- `java -jar hubble-be.jar` 非 fat jar

## 7. 备注

- 这份计划替代此前“已全部完成”的任务口径，聚焦 Hubble V2.0 收尾阶段的剩余工作。
- 文档质量提升目标:
  - 已补上发版出口定义、依赖矩阵、已知问题处置口径三类结构化信息
  - 剩余真正影响评分上限的因素，不再是文档结构，而是外部 gate 和最终 artifact 证据
- `2026-06-03` 本地 runtime smoke 已补充验证:
  - 中文默认页与英文关键路由未见 raw key；`/graph-management/1/data-analyze` 的 Gremlin / Algorithm 视图文案完整
  - 英文态按钮 `Create Graph`、`Create Property`、`Reuse Existing Property` 与导入列表 `succeeded` 状态单元格存在布局挤压风险
  - 后端错误消息当前保持原文 English；若接受该策略，需在结论 / Release Note 中明确写出
  - `hlm` 导入链路可跑通，但需把包内 `hlm.txt` 改名为 `hlm.csv` 才能绕过默认上传白名单
  - `shortestPath` 在前端真实请求口径下返回 200，路径正确，但 `graph_view` 仍为 `null`
  - `movie` 导入任务 `SUCCEED`；`g.V('0.5毫米')`、`g.V().hasLabel('电影').limit(3)`、`g.E().hasLabel('属于')` 均通过，但 `g.E().hasLabel('属于').count()` 仍会触发 `Big id max length`
  - `target/apache-hugegraph-toolchain-1.7.0.tar.gz` 当前不符合 source release 口径：扫出 596 个 `lib/*.jar`，且顶层缺 `DISCLAIMER` / `README.md`
  - `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` 当前已完成结构 / 运行态复验：法律文件、`ui/`、README、健康检查均可验证，但包内仍带 `db.mv.db`、`logs/hugegraph-hubble.log`，且启动脚本在已有健康实例场景下存在误判风险
- GitHub 状态基于 `2026-06-03` 对公开 API 的只读查询:
  - Toolchain PR: `https://github.com/apache/hugegraph-toolchain/pull/632`
  - Server PR: `https://github.com/apache/hugegraph/pull/3008`
