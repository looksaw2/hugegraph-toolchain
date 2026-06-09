<title>Subtask 01 - 编译确认与 Bug 收敛方案</title>

> 本页定义 Hubble V2.0 的编译、打包、启停和 bug disposition 方案。本页不声明本轮已经执行任何编译或测试；命令只作为后续正式 candidate 验证时的执行路径。

## 1. 目标

Subtask 01 解决两个问题：

| 问题 | 输出 |
|-|-|
| Hubble V2.0 是否能从干净工作区编译、打包、启动 | 可复现命令、环境要求、candidate 记录模板、启停 smoke |
| 影响 release 的已知 bug 是否有明确处置 | bug 矩阵、owner、归因、是否 blocker、复验方式 |

本页的最终结论必须建立在正式 release candidate 上。源码树可用于说明构建路径和预期，不能替代 candidate 运行结果。

## 2. 入口条件

| 条件 | 要求 |
|-|-|
| 分支 / commit | 使用准备进入 release 的 commit，记录完整 hash |
| PR 状态 | Toolchain 与 Server 相关 PR 状态明确 |
| 环境 | JDK、Maven、Node、Yarn、OS 版本记录完整 |
| 依赖 | HugeGraph Server 版本、启动方式、图名、认证方式记录完整 |
| 工作区 | 不使用未跟踪生成目录作为输入；正式验证前确认工作区状态 |
| 证据目录 | 只记录正式验证产物，不引用个人临时路径 |

## 3. 模块编译路径

Hubble 不是孤立模块。根据仓库结构，Hubble 依赖 client 和 loader，Hubble FE 又通过 `frontend-maven-plugin` 在 `hubble-dist` 打包中构建。

原始 Mermaid 代码：

<readonly-block type="isv"></readonly-block>

建议执行路径：

```bash
# 1. 编译 Hubble 依赖模块
mvn install -pl hugegraph-client,hugegraph-loader -am -DskipTests -ntp

# 2. Hubble 后端单测
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp

# 3. Hubble 全模块打包
cd hugegraph-hubble
mvn package -DskipTests -ntp
```

执行记录必须包含：

| 项 | 记录要求 |
|-|-|
| 命令 | 完整命令，不省略参数 |
| 耗时 | Maven 总耗时和关键模块耗时 |
| 结果 | BUILD SUCCESS / FAIL |
| 失败摘要 | 第一处失败原因、失败模块、是否依赖下载或测试失败 |
| 产物 | candidate 文件名、路径、commit |

## 4. 前端 build 确认

Hubble FE 的 `package.json` 中 `build` 脚本会执行 `react-app-rewired build` 并运行 license 生成脚本。`hubble-dist/pom.xml` 通过 `frontend-maven-plugin` 安装 Node / Yarn、执行 yarn install 和 yarn build，然后将 build 结果拷贝为 binary 中的 `ui/`。

检查点：

| 检查项 | 判定 |
|-|-|
| Yarn install | dependency resolve 成功，无 lockfile 异常 |
| FE build | `build/` 生成成功 |
| FE license | license 生成脚本成功，结果进入 release legal bundle 或可对账 |
| `ui/` 拷贝 | candidate 中存在 `ui/index.html` 和静态资源 |
| 缓存排除 | candidate 中不包含 `node_modules/`、Node runtime、Yarn cache |

## 5. Binary 启停 smoke

正式 candidate 解压后，只使用包内脚本启动，不使用源码目录脚本。

```bash
cd <candidate-dir>/bin
./start-hubble.sh
curl -s -D - http://127.0.0.1:8088/actuator/health
curl -s -D - http://127.0.0.1:8088/
./stop-hubble.sh
```

启停 smoke 判定：

| 检查点 | 通过标准 | 失败归因 |
|-|-|-|
| `start-hubble.sh` | 返回成功，pid 文件指向真实进程 | 脚本、classpath、端口、JDK |
| health | HTTP 200，body 可解析 | Spring Boot 启动、端口、actuator |
| UI 首页 | HTTP 200，返回前端入口 | `ui/` 缺失、静态资源路径、代理 |
| `stop-hubble.sh` | 停止目标进程并清理 pid | pid 管理、权限、脚本逻辑 |
| 日志 | 无 fatal error、无类冲突 | dependency、Guava / shaded jar、配置 |

## 6. Bug disposition 分类

所有 release 影响项必须归入下列三类之一：

| 分类 | 定义 | Release 处理 |
|-|-|-|
| fix-before-release | 影响核心链路或 ASF 合规，必须修复后进入 candidate | 修复、补测试、重打 candidate |
| accept-with-known-issue | 影响可接受，有真实 issue、规避方式和 release note | 绑定 issue，父文档同步 known issue |
| release blocker | 无法规避或无法归因，不能进入 release | 阻断 release，直到关闭 |

## 7. 已知问题矩阵

| ID | 问题 | 归因方向 | 验证方式 | Release 口径 |
|-|-|-|-|-|
| BUG-01 | shortestPath 返回路径但 `graph_view` 缺失或为空 | Hubble BE 需要基于 Server traverser 返回的 path id 回查顶点和边 | `POST /api/v1.2/graph-connections/{connId}/algorithms/shortestPath`，检查 json/table/graph 三视图 | 正式 candidate 必须通过；失败为 Function Gate blocker |
| BUG-02 | 上传文本类数据集时被格式白名单拒绝 | Hubble upload API 依赖 `upload_file.format_list` | 请求 upload token 和 upload API，使用 release PR 中已跟踪的数据集扩展名 | 若内置数据集无法导入，为 P1 |
| BUG-03 | load task 成功后 job 状态仍显示 LOADING | Hubble job 与 load task 状态聚合 | 导入任务完成后查询 job-manager 和 load-tasks API | UI 状态错误影响用户判断，正式 candidate 需复验 |
| BUG-04 | Gremlin 边标签 `count()` 触发 Server 错误 | Server Gremlin 执行语义；Hubble suffix limit 不保护标量 count | Hubble Gremlin 与 direct Server Gremlin 分别复现 | 若 Server 侧未修，必须有真实 issue 和 known issue 口径 |
| BUG-05 | 英文按钮、状态标签、移动端 tab 溢出 | Hubble FE i18n / CSS | Subtask 02 路由 smoke 和截图 / DOM 检查 | 核心流程溢出为 P1 |
| NOTE-01 | 直接 `java -jar` 后端 jar 无法作为完整 Hubble 启动方式 | Hubble binary 应通过包内脚本和完整 classpath 启动 | README 和脚本验证 | 非 bug，但 README 不能误导 |
| NOTE-02 | 依赖 jar 类冲突风险 | Runtime classpath / dependency | 启动日志和健康检查 | 若启动失败为 blocker，否则记录为风险 |

## 8. 单测与回归覆盖

建议至少覆盖：

| 测试 | 文件 / 范围 | 覆盖目标 |
|-|-|-|
| Hubble BE UnitTestSuite | `hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/UnitTestSuite.java` | 后端核心 service 回归 |
| shortestPath graph view | `OltpAlgoServiceTest` | path id 回查顶点 / 边、fallback 行为 |
| load job status | `JobManagerServiceTest` / `LoadTaskServiceTest` | load task 状态聚合和 job 刷新 |
| upload validation | FileUploadController / FileMappingService 相关单测 | 扩展名白名单、token、路径安全、大小限制 |
| FE build | `hubble-fe` build | i18n 和 CSS 改动不破坏构建 |

如果某项没有自动测试，必须在执行报告中说明原因，并给出对应 runtime smoke。

## 9. Candidate 记录模板

执行时追加如下信息，不能提前写实际值：

| 字段 | 记录要求 |
|-|-|
| Candidate | 正式文件名 |
| Commit | 完整 hash |
| Branch / tag | release 分支或 tag |
| Build command | 完整命令 |
| Build result | 成功或失败摘要 |
| Unit test result | suite、耗时、失败数 |
| FE build result | build 命令、license 脚本结果 |
| Startup smoke | health、UI 首页、stop |
| Known bugs | 每项 disposition |

## 10. 失败处理规则

| 失败类型 | 处理 |
|-|-|
| Maven dependency 下载失败 | 标记为环境或网络，不写成代码 bug；复跑前记录 repository |
| Java 编译失败 | 归入 Subtask 01 blocker，记录模块和首个编译错误 |
| 单测失败 | 记录 test class、test method、失败断言和相关 PR |
| FE build 失败 | 记录 TypeScript / ESLint / webpack / license 脚本错误 |
| 启动失败 | 记录 stdout、stderr、日志、pid、端口占用、classpath |
| 功能 smoke 失败 | 回流 Subtask 03 / 05 做归因 |
| ASF 审计失败 | 回流 Subtask 04，修复后重打 candidate |

## 11. 出口条件

Subtask 01 关闭条件：

- 正式 candidate 可由记录命令生成。
- Hubble BE 单测范围明确，关键回归测试通过或有 blocker 说明。
- FE build 和 license 生成流程通过。
- Binary 脚本启停、health、UI 首页 smoke 通过。
- 所有已知 bug 都有 disposition。
- 任何未闭环问题都已经同步到父文档 release gate。

# Subtask 01 Implementation - Build, Startup, and Bug Disposition

## Implemented

- Added Hubble BE unit coverage for release-blocking bug fixes:

  - `OltpAlgoServiceTest`
  - `JobManagerServiceTest`
  - `LoadTaskServiceTest`
  - `FileUploadControllerTest`
- Updated `UnitTestSuite` so the Hubble unit profile covers the new focused  
service/controller tests.
- Added `maven-surefire-plugin` include configuration in `hubble-be/pom.xml`  
so `-P unit-test` runs `UnitTestSuite` only, matching the documented Hubble  
unit-test command and avoiding API tests that require an external Server.
- Fixed `start-hubble.sh -f` to work as documented: `-f` now starts foreground  
mode without requiring a `true|false` argument.
- Improved startup script behavior:

  - normalize bind host `0.0.0.0` / `::` to `127.0.0.1` for health probing;
  - detect already healthy Hubble before starting;
  - remove stale pid on startup failure;
  - terminate the spawned process if health check fails.

## Verification

```bash
mvn test -P unit-test -pl hugegraph-hubble/hubble-be -ntp
```

Result: `BUILD SUCCESS`, `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`.

An earlier run without the Surefire include fix executed `GraphConnectionTest`  
and failed because no HugeGraph Server was listening on `127.0.0.1:8080`. That  
confirmed the profile was running beyond unit scope; the profile now runs only  
`UnitTestSuite`.

## Remaining Release Gates

- Formal binary candidate generation has not been recorded in this file.
- Binary script startup/health/UI smoke must still be run from the final  
candidate directory.
- Any actual runtime failures from the final candidate must be classified as  
fix-before-release, known issue with issue link, or release blocker.

---

## CC Gap Closure - 2026-06-09

<callout emoji="💡">
Final candidate validation was rerun and passed for build, unit, live smoke, UI browser smoke, and UI full acceptance.
</callout>

| Item | Result |
|-|-|
| Candidate | `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` |
| Timestamp | `2026-06-09 12:56:40 +0800` |
| Size | `197714261` bytes |
| Package build | `BUILD SUCCESS` |
| Hubble BE unit test | `BUILD SUCCESS`, tests `15`, failures `0`, errors `0`, skipped `0` |
| Live Hubble + Server smoke | `SUCCESS` |
| UI browser smoke | `SUCCESS` |
| UI full acceptance | `SUCCESS` |

Post-check found no remaining `HugeGraphHubble` process.
