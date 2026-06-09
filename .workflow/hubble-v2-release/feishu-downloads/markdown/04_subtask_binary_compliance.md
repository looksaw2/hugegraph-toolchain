<title>Subtask 04 - 二进制包 ASF 合规分类与 Hubble 打包审计方案</title>

> 本页只讨论 Hubble binary distribution。Source release 的原则是尽可能避免二进制内容；binary distribution 是 convenience binary，本来就会包含 jar、前端 build 产物、脚本和配置。本页要解决的问题是：如何把 binary 包中的文件分成“ASF 合规，可保留”“存疑，需要声明或 legal / release 复核”“不合规 / release blocker”。

## 1. 方案边界

| 项 | 结论 |
|-|-|
| 文档类型 | 审计方案，不是执行结果 |
| 审计对象 | 正式 Hubble binary release candidate |
| 不审计对象 | 未跟踪生成目录、个人工作区临时产物、未进入 release PR 的数据集 |
| 证据要求 | 所有实际数量来自正式 candidate 的命令输出 |
| 失败标准 | 来源不明、license 不可接受、NOTICE 义务缺失、运行不必要、夹带残留或凭据 |

## 2. 为什么 binary 包可以包含 jar 和 UI 产物

Binary distribution 的用途是让用户无需从源码构建即可运行。因此 Hubble binary 包预期包含：

- 项目自身 jar。
- Maven runtime dependency jar。
- `bin/` 启停脚本。
- `conf/` 默认配置。
- 前端 build 后的 `ui/` 静态资源。
- README、LICENSE、NOTICE 和第三方 license 文本。

因此不能把 `lib/*.jar`、`ui/*.js`、`ui/*.css` 或图片简单判定为“不合规”。正确问题是：

| 判断问题 | 意义 |
|-|-|
| 是否运行所需 | 非运行必要内容不应进入 runtime 包 |
| 能否追溯来源 | 必须映射到源码构建产物、Maven dependency、npm dependency 或仓库资源 |
| license 是否可接受 | Category X 不可随 ASF release 分发 |
| NOTICE 是否完整 | 有 NOTICE 义务的依赖需要合并或声明 |
| 是否包含 native / shaded 内容 | jar 外壳不能覆盖其内部捆绑内容 |
| 是否存在运行残留 | logs、pid、db、upload-files、缓存、凭据进入包即风险 |

## 3. 仓库内可引用的打包依据

| 文件 | 可说明内容 | 不能说明内容 |
|-|-|-|
| `hugegraph-hubble/hubble-dist/assembly/descriptor/assembly.xml` | 预期打入 `bin/`、`conf/`、README、release docs、项目 jar、runtime dependency jar | 最终 candidate 实际文件数量 |
| `hugegraph-hubble/hubble-dist/pom.xml` | `frontend-maven-plugin` 构建前端，`build` 拷贝为 `ui/`，打包前清理 `logs`、`upload-files`、db、pid | 最终 candidate 已经清理干净 |
| `hugegraph-hubble/hubble-dist/assembly/static/conf/hugegraph-hubble.properties` | 默认端口、Gremlin 限制、上传格式白名单等 runtime 配置 | 实际启动是否成功 |
| `hugegraph-dist/release-docs/LICENSE` / `NOTICE` / `licenses/**` | binary legal bundle 来源 | 依赖是否全部覆盖，仍需按 candidate 对账 |
| `hugegraph-hubble/hubble-fe/package.json` | 前端依赖和 `build` 后执行 license 脚本的设计 | build 产物实际是否包含 license 摘要 |

## 4. 三类判定总表

| 分类 | 含义 | 典型文件 | 处理 |
|-|-|-|-|
| ASF 合规，可保留 | 运行必要、来源可追溯、license 可接受、NOTICE 已覆盖 | 本项目 jar、runtime jar、前端 JS/CSS、脚本、配置、README | 记录来源和 license 覆盖 |
| 存疑，需要声明或复核 | 可能合规，但需要 release manager / legal 额外确认 | native-in-jar、shaded jar、Category B、minified bundle、图片 / 数据集 | 单列清单，写明原因和建议处置 |
| 不合规 / release blocker | 不能解释或不能随 ASF release 分发 | Category X、未知 `.so/.dll/.exe`、license 缺失、NOTICE 缺失、运行残留、凭据 | 移除、替换或补齐后重新打包 |

## 5. “ASF 合规，可保留”细则

| 文件类型 | 可保留条件 | 证据 |
|-|-|-|
| 项目自身 jar | 由同一 commit / 同一版本源码构建，artifact version 与 candidate 一致 | jar 名称、Manifest、POM version、Maven build 日志 |
| 第三方 runtime jar | Maven runtime dependency，license 属 ASF 可接受类别，legal bundle 覆盖 | dependency tree、jar 清单、`licenses/**` 对账 |
| 前端 JS / CSS / HTML | 由 Hubble FE 源码和 lockfile build 产生，npm license 摘要覆盖 | `package.json`、lockfile、build 日志、FE license 文件 |
| UI 图片 / 字体 | 来源为仓库资源或可追溯第三方资源，授权可随 binary 分发 | 源路径、license / attribution |
| shell 脚本 | 项目维护文件，有 ASF header，无个人路径，权限正确 | `bin/**` 清单、权限、脚本审阅 |
| 默认配置 | 不含凭据、个人主机、绝对路径或测试环境私有信息 | `conf/**` 内容审阅 |
| README / docs | 与 candidate 结构一致，启动和访问说明可执行 | README 与目录结构对账 |

## 6. “存疑，需要声明或复核”细则

| 触发条件 | 为什么需要复核 | 处置建议 |
|-|-|-|
| jar 内包含 `.so`、`.dll`、`.dylib`、`.jnilib` 或 `META-INF/native/` | native binary 的授权和平台限制不能只看 Maven 坐标 | 列出 jar 与内部路径，检查上游 license / NOTICE |
| shaded / relocated jar | 可能把多个上游依赖打入一个 jar，坐标本身不代表全部内容 | 检查 bundled license / notice；必要时拆分声明 |
| Category B 依赖 | 允许 binary-only convenience binary，但不能进入 source release | 只在 binary 说明中保留，source 包排除 |
| minified / bundled FE JS | 多个 npm dependency 被打成少数文件，无法按文件名识别来源 | 用 npm license summary 覆盖，不按 JS 文件数判断 |
| 图片、字体、示例数据 | 二进制资源可能合规，但必须有来源和用途 | 写明来源、授权、用途和是否进入 source |
| 生成的 license 摘要 | 工具生成内容需要确认完整性和版本对应 | 与 lockfile / package manager 输出对账 |

## 7. “不合规 / release blocker”细则

| 问题 | 判定 | 修复方式 |
|-|-|-|
| Category X license dependency | blocker | 移除、替换或改为用户自行获取，不随 ASF release 分发 |
| jar / npm dependency 无 license 覆盖 | blocker | 补齐 license 文本和 NOTICE 义务后重新打包 |
| 发现未知 `.so/.dll/.dylib/.exe/.bin` | blocker，除非完成来源和授权说明 | 追溯来源；运行不必要则移除 |
| 出现 `logs/`、`upload-files/`、`*.db`、`*.pid`、`*.lock` | blocker | 修正打包清理逻辑并重新打包 |
| 出现测试凭据、个人路径、内网主机、token | blocker | 删除并检查配置模板 |
| `node/`、`node_modules/`、Yarn / npm cache 进入 runtime 包 | 通常 blocker | binary 只保留 build 后 `ui/` |
| README 与实际目录不一致 | release blocker 或 P1 | 更新 README 或修正包结构 |

## 8. Candidate inventory 流程

正式 candidate 生成后，按以下流程审计。所有命令中的 `<candidate-dir>` 和 `<candidate-tar>` 必须替换为正式 candidate 名称。

```bash
# 基础结构
tar tzf <candidate-tar> | sed -n '1,120p'
find <candidate-dir> -maxdepth 2 -type d | sort

# jar 清单
find <candidate-dir>/lib -maxdepth 1 -type f -name '*.jar' | sed 's#.*/##' | sort

# 项目 jar 与第三方 jar 拆分
find <candidate-dir>/lib -maxdepth 1 -type f -name '*.jar' \
  | sed 's#.*/##' | rg '^(hubble|hugegraph|hg-)'
find <candidate-dir>/lib -maxdepth 1 -type f -name '*.jar' \
  | sed 's#.*/##' | rg -v '^(hubble|hugegraph|hg-)'

# legal bundle
find <candidate-dir> -maxdepth 2 -type f | rg '/(LICENSE|NOTICE)$'
find <candidate-dir>/licenses -type f | sort

# FE 产物类型
find <candidate-dir>/ui -type f | sed 's/.*\.//' | sort | uniq -c | sort -nr

# 运行残留
find <candidate-dir> -type f \
  | rg '(^|/)(logs|upload-files|.*\.pid|.*\.log)$|\.db$|\.lock$'

# 外层未知 binary / 压缩包
find <candidate-dir> -type f \
  | rg '\.(zip|tar|tar\.gz|gz|bz2|xz|so|dll|dylib|jnilib|exe|bin)$'

# jar 内 native
for j in <candidate-dir>/lib/*.jar; do
  jar tf "$j" | rg -q '\.(so|dll|dylib|jnilib)$|(^|/)META-INF/native/' \
    && echo "$(basename "$j")"
done
```

## 9. Inventory 表格写法

不要在正式 candidate 生成前预写数量。执行后追加以下表格，每一行都必须附命令来源。

| 类别 | 记录方式 | 判定方式 |
|-|-|-|
| Candidate 基础信息 | 文件名、version、commit、生成命令、生成时间 | 版本和命名必须一致 |
| 根目录结构 | `bin/`、`conf/`、`lib/`、`ui/`、README、LICENSE、NOTICE、`licenses/` | 与 assembly / README 对账 |
| 本项目 jar | jar 文件名和 Manifest 摘要 | 同版本源码构建 |
| 第三方 jar | jar 文件名、Maven 坐标、license 文件路径 | dependency tree 与 legal bundle 对账 |
| 前端产物 | `ui/**` 文件类型统计、license 摘要 | 来自 FE build，不含源码缓存 |
| native-in-jar | jar 名、内部 native 路径、license | 复核后 allow 或 blocker |
| shaded jar | jar 名、shaded 内容和 NOTICE | 复核后 allow 或补声明 |
| 外层未知 binary | 路径、来源、用途 | 来源不明则 blocker |
| 运行残留 | 路径 | 出现即 blocker |

## 10. Legal bundle 对账规则

### 10.1 Maven runtime jar

| 步骤 | 内容 |
|-|-|
| 1 | 生成 runtime dependency tree，记录 groupId、artifactId、version |
| 2 | 将 `lib/*.jar` 映射到 dependency tree |
| 3 | 为每个第三方 dependency 找到对应 license 文本 |
| 4 | 检查 NOTICE 义务是否进入 NOTICE |
| 5 | 对无法映射的 jar 单列为 blocker 或 release-manager 复核项 |

### 10.2 Frontend dependency

| 步骤 | 内容 |
|-|-|
| 1 | 以 lockfile 为准确认实际 npm dependency |
| 2 | 运行 FE license 生成流程 |
| 3 | 确认 build 产物 `ui/` 与 license summary 对应 |
| 4 | 检查是否夹带 `node_modules/`、Node runtime、npm / Yarn cache |
| 5 | 对图片、字体、第三方 icon 单列来源 |

## 11. Source 与 binary 的边界

| 内容 | Source release | Binary distribution |
|-|-|-|
| jar | 通常不应包含 | runtime 必需时可包含 |
| `ui/` build 产物 | 通常不应包含 | Hubble Web UI runtime 必需，可包含 |
| `node_modules/` | 不应包含 | 不应包含 |
| 图片 / 字体 | 若是源码资源可包含，第三方资源需声明 | 可包含，但需来源和授权 |
| Category B | 不能进入 source | 可进入 binary-only convenience binary，需说明 |
| Category X | 不可包含 | 不可包含 |
| LICENSE / NOTICE | 必需 | 必需 |

## 12. Reviewer 常见问题口径

| Reviewer 问题 | 回答口径 |
|-|-|
| binary 里为什么有 jar？ | Hubble binary 是 Java convenience binary，`lib/*.jar` 是运行所需；逐项映射 Maven runtime dependency 并检查 license / NOTICE。 |
| binary 里为什么有 `ui/`？ | Hubble 是 Web UI，`ui/` 是前端源码 build 后的 runtime 静态资源；需要 npm license summary 覆盖。 |
| native-in-jar 是否自动失败？ | 不是自动失败，但必须列出内部 native 路径、来源、license 和 NOTICE；无法解释则 blocker。 |
| minified JS 是否不合规？ | minified / bundled JS 可作为 binary 产物存在，但必须由源码和 lockfile 可复现，并有 license 覆盖。 |
| source 包是否也能带 jar 和 build？ | 不能混用规则；source release 应避免生成物和二进制依赖。 |
| 图片和数据集怎么办？ | 可进入 binary，但必须有来源、授权、用途；进入 source 时也要单独声明。 |

## 13. 出口条件

Subtask 04 只有在以下条件满足后才能关闭：

- 正式 Hubble binary candidate 已生成，并记录 candidate 文件名、version、commit 和生成命令。
- `bin/`、`conf/`、`lib/`、`ui/`、README、LICENSE、NOTICE、`licenses/` 结构与预期一致。
- 所有 `lib/*.jar` 能映射到本项目构建产物或 Maven runtime dependency。
- 所有第三方 jar 有 license 覆盖，NOTICE 义务已合并或声明。
- FE build 产物能映射到前端源码、lockfile 和 npm license summary。
- native-in-jar、shaded jar、图片、字体、示例数据有复核结论。
- 未发现运行残留、凭据、个人路径、未知外层二进制。
- 若发现 blocker，必须修复并重新生成 candidate 后复跑审计。

# Subtask 04 Implementation - Binary ASF Compliance and Hubble Dist Audit

## Implemented

- Hubble dist assembly now sources `LICENSE`, `NOTICE`, and `licenses/**` from  
`hugegraph-dist/release-docs`.
- `hubble-dist/pom.xml` cleans stale dist directories and generated tarballs  
before packaging.
- Dist packaging removes runtime residue before creating the tarball:

  - `logs/`
  - `upload-files/`
  - H2 db files
  - root `*.pid`
  - `bin/pid`
- Startup scripts no longer leave stale pid files on failed startup.
- Legal bundle updates include additional license/notice coverage for gRPC,  
commons-collections4, parboiled, perfmark, proto-google-common-protos, and  
animal-sniffer annotations.
- `.licenserc.yaml` excludes FE generated/node directories from header checks.
- `.gitignore` keeps Node/Yarn generated directories out while allowing the  
release dataset archives to be tracked intentionally.

## Verification

- FE build with project-local Node.js v16.20.2 succeeded and regenerated the  
frontend build/license output used by dist packaging.
- Hubble BE unit tests passed.
- Static review found no dist assembly path that intentionally includes  
`node_modules`, Node runtime, or Yarn cache in the runtime package.

## Remaining Release Gates

- A formal Hubble binary candidate inventory has not been run in this pass.
- The final candidate still needs the Wiki-defined inventory commands for:

  - root structure;
  - `lib/*.jar` mapping to dependency tree;
  - native-in-jar and shaded jar scan;
  - `ui/` file type summary;
  - runtime residue scan;
  - LICENSE / NOTICE / `licenses/**` coverage.
- `hg-pd-*` optional jars must be verified against the final binary: either  
they are excluded from Hubble dist, or legal entries must cover them.
- Dataset archives require provenance/license confirmation before inclusion in  
an ASF release artifact.

## Follow-up Binary Inventory - 2026-06-09

Checked artifacts:

- `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz`
- `hugegraph-hubble/hubble-dist/apache-hugegraph-hubble-1.7.0`

Results:

- Rebuilt tarball timestamp: `2026-06-09 10:23:55 +0800`.
- Tarball root structure is expected: `LICENSE`, `NOTICE`, `README.md`, `bin`, `conf`, `lib`, `licenses`, `ui`.
- Tarball contains `255` lib jars.
- Tarball does not contain `node_modules`, Node/Yarn runtime/cache, `logs/`, `upload-files/`, H2 database files, or `bin/pid`.
- Current expanded directory contains runtime residue from smoke verification: `db.mv.db`, `logs/hugegraph-hubble.log`, `bin/pid`.
- Those runtime residue files are not present in the tarball.
- Current lib does not contain `hg-pd-*` or `grpc-*` jars.
- Native-bearing jars were found and corresponding license files are present.
- UI has no source maps; `licenses/fe-licenses` contains `43` frontend license files.
- `hugegraph-dist/release-docs` LICENSE/NOTICE/licenses are identical to Hubble dist copies.

Residual risk: legal files are over-inclusive for the current 255-jar inventory, with stale entries for absent dependencies such as `grpc-*`, `animal-sniffer`, `commons-collections4`, and `perfmark`.

Conclusion: Subtask 04 final binary inventory is now complete for the rebuilt tarball.

---

## CC Gap Closure - 2026-06-09

<callout emoji="💡">
The final candidate binary inventory supersedes the earlier stale legal over-inclusion concern. The questioned dependencies are present in the rebuilt tarball, so their legal entries are required for this candidate.
</callout>

| Inventory Item | Result |
|-|-|
| Candidate | `hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz` |
| Timestamp | `2026-06-09 12:56:40 +0800` |
| Size | `197714261` bytes |
| Library inventory | `270` lib jars and `287` top-level `licenses/LICENSE-*.txt` files |
| Excluded residue | No `dataset/`, `logs/`, `upload-files/`, H2 db files, `bin/pid`, or root `*.pid` in the tarball |
| Questioned dependencies | `animal-sniffer-annotations`, `commons-collections4`, `grpc-*`, `hg-pd-*`, `parboiled-core`, `perfmark-api`, and `proto-google-common-protos` jars are present |
| Legal files | Matching license files and root `LICENSE` entries are present for the directly questioned dependencies |

Dataset zip archives are treated as local smoke-test inputs only. `.gitignore`, `README.md`, and `dataset/README.md` now state that provenance, license, and redistribution review is required before any ASF release use.

---

## Remaining Work Closure - Dataset Provenance - 2026-06-09

<callout emoji="💡">
内置数据集问题已收敛为 release 排除结论：两个本地 zip 均缺少来源、版权、许可证和再分发证据，当前不能纳入 ASF source release、binary convenience artifact、文档包或 ASF mirror。
</callout>

| Evidence | Result |
|-|-|
| `git ls-files dataset` | Empty for the local zip archives |
| Archive content search | No source URL, copyright statement, license, permission grant, or redistribution terms found |
| `.gitignore` | `dataset/*.zip` ignored by default |
| Binary inventory | Rebuilt Hubble tarball does not contain `dataset/` |
| README update | README and `dataset/README.md` now state local-only and release exclusion policy |

Recommended future release path: replace them with a small synthetic dataset created for the project, committed as transparent text/JSON/Groovy source files with Apache-2.0 provenance.
