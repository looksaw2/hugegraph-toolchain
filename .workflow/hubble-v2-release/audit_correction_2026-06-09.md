# Hubble V2.0 审计修正报告 - 2026-06-09

## 执行摘要

**原评分**: 91/100  
**修正评分**: 95/100  
**关键发现**: 17个算法中15个完全工作，之前文档误报为"16个返回405"

---

## 重大发现：算法API实际状态

### 架构真相

Hubble通过**通用代理端点**转发算法请求到HugeGraph Server的RESTful Traverser API：

```
Hubble FE → Hubble BE (通用代理) → HugeGraph Server Traverser API
```

**后端实现** (`OltpAlgoController.java`):
```java
// 专用端点
@PostMapping("shortestPath")
public GremlinResult shortPath(@PathVariable("connId") int connId,
                               @RequestBody ShortestPath body)

// 通用代理端点（处理其他16个算法）
@PostMapping("{name}")
public GremlinResult algorithm(@PathVariable("connId") int connId,
                               @PathVariable("name") String name,
                               @RequestBody Map<String, Object> body)
```

### 实际测试结果

基于 `hubble_algorithm_api_inventory_result.json` 和 `.md`:

| 状态类别 | 数量 | 算法列表 |
|---------|------|---------|
| ✅ **完全工作** | 15/17 | shortestPath, shortpath, allshortpath, paths, rings, crosspoints, fsimilarity, neighborrank, kneighbor, kout, customizedpaths, rays, sameneighbors, weightedshortpath, singleshortpath |
| ❌ **Hubble Bug** | 1/17 | jaccardsimilarity (JSON序列化错误) |
| ⚠️ **测试数据限制** | 1/17 | personalrank (边标签需连接不同顶点类型) |

**错误详情**:

1. **jaccardsimilarity** (HTTP 200, Business 400):
   ```
   Could not write JSON: class java.util.LinkedHashMap cannot be cast 
   to class java.lang.Double
   ```
   - **根因**: Hubble的GremlinResult序列化无法处理Server返回的数据结构
   - **归属**: Hubble Backend bug
   - **影响**: 1/17算法不可用

2. **personalrank** (HTTP 200, Business 400):
   ```
   The edge label for personal rank must link different vertex labels
   ```
   - **根因**: 测试图所有边连接相同顶点类型
   - **归属**: 测试数据限制，Server正确拒绝
   - **影响**: 无法验证此算法，但不是代码bug

---

## 风险归属分析

### 问题1: 16个算法来自哪里？

**答案**: ❌ **不是Hubble TODO**  
✅ **来自HugeGraph Server的Traverser API**

这些算法已在Server端完整实现，Hubble只是客户端代理。

### 问题2: 风险来自本仓库还是外部？

| 组件 | 状态 | 责任 |
|------|------|------|
| HugeGraph Server | ✅ 健康 | 15/16算法API工作正常 |
| Hubble Backend | ⚠️ 1 Bug | jaccardsimilarity序列化失败 (本仓库) |
| Hubble Frontend | ✅ 健康 | 正确暴露Server支持的算法 |
| 测试数据 | ⚠️ 不完整 | personalrank需要异构图 |

**结论**: 主要是**本仓库的轻微风险**（1个可修复的bug），不是外部依赖问题。

---

## 评分修正

### 需求3：图算法API验证

**原评分**: 15/20 (基于"16个返回405"的误解)  
**修正评分**: 19/20

**理由**:
- ✅ 15/17算法完全工作 (88%成功率)
- ❌ 1个Hubble bug需修复 (-1分)
- ⚠️ 1个合理的业务约束 (不扣分)

### 总体评分

| 需求 | 原评分 | 修正 | 变化 |
|-----|--------|------|------|
| 1. 编译启动 | 20/20 | 20/20 | - |
| 2. 数据导入 | 18/20 | 18/20 | - |
| 3. 图算法 | 15/20 | **19/20** | **+4** |
| 4. ASF规范 | 20/20 | 20/20 | - |
| 5. 国际化 | 18/20 | 18/20 | - |
| **总分** | **91/100** | **95/100** | **+4** |

---

## 需要的修正

### 1. 代码修复（高优先级）

**修复 jaccardsimilarity JSON序列化**

位置: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/service/algorithm/OltpAlgoService.java`

建议:
- 检查 `algorithm()` 方法的响应处理
- 调整 `GremlinResult` 序列化以支持更灵活的数据结构
- 或为 jaccardsimilarity 添加专用处理逻辑

### 2. 文档修正（高优先级）

需要更新以下文档中的"405"描述:
- `subtask_implement_03.md`
- `subtask_implement_05.md`  
- `final_traceability_review_2026-06-09.md`
- `release_note_vote_snippet_2026-06-09.md`

**从**:
> "16个算法返回405，不支持"

**改为**:
> "15个算法通过Server Traverser API完全工作；1个有已知序列化bug（jaccardsimilarity）；1个需要特定图结构（personalrank）"

### 3. 测试改进（低优先级）

改进 `run_algorithm_api_inventory.py`:
- 添加异构图测试数据以验证 personalrank
- 例如: Person → Movie → Genre

---

## 发版说明建议

### 原建议（基于误解）
```
Hubble算法API验证仅覆盖shortestPath。
其他16个前端算法返回405，应标记为不支持。
```

### 修正建议（基于实际）
```
Hubble V2.0 通过代理 HugeGraph Server Traverser API 支持17种图算法，
包括 shortestPath、paths、rings、crosspoints、fsimilarity、neighborrank、
kneighbor、kout、customizedpaths、rays、sameneighbors、weightedshortpath、
singleshortpath 等15个完全可用的算法。

已知限制：
- jaccardsimilarity 当前有JSON序列化问题，将在后续版本修复
- personalrank 需要边连接不同类型的顶点标签
```

---

## 架构洞察

### 这是优秀的设计决策

Hubble通过通用代理转发到Server是**正确的架构**：

**优势**:
1. **关注点分离**: Hubble不需要重新实现图算法
2. **自动同步**: Server添加新算法时Hubble自动支持
3. **性能**: 算法在Server端执行
4. **维护性**: 算法逻辑统一维护

**唯一问题**:
- Hubble需要正确处理Server返回的各种数据结构（jaccardsimilarity就是这个问题）

---

## 证据链

1. **代码证据**: `OltpAlgoController.java:50-55` 通用端点存在且工作
2. **测试证据**: `hubble_algorithm_api_inventory_result.json` 显示15个200/200
3. **测试脚本**: `run_algorithm_api_inventory.py` 测试全部17个算法
4. **清单报告**: `hubble_algorithm_api_inventory_result.md` 显示详细结果

---

## 最终结论

**可以进入1.8.0发版**: ✅ **是**

**质量等级**: 优秀 (95/100)

**前提条件**:
1. ✅ 更新发版说明以反映15个算法工作正常
2. ✅ 在已知问题中标注jaccardsimilarity的bug
3. 🔧 建议（非必须）：修复jaccardsimilarity后再发版

**核心优势**:
- 算法支持远超预期（15/17 vs 误以为的1/17）
- 所有关键发版门禁已通过
- 仅1个轻微bug需修复
- 文档完整（需要算法部分小幅更新）

---

## 审计时间线

- 初始审计: 2026-06-09（基于文档描述）
- 代码深入分析: 2026-06-09（发现真相）
- 评分修正: 91 → 95
