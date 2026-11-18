# HBMZZ-Core 四路执行计划

> 适用分支：`feature/standards`（本文件），最终并入 `hbmzz`。
> 关联文档：`MODULE_BOUNDARIES.md`、`BRANCHING.md`、`VALIDATION.md`。
> 说明：本计划以**阶段与依赖**描述顺序，不预设绝对工期；每个阶段的进入条件由检查点（CP）判定。合并动作统一由 team-lead 执行。

## 1. 目标与范围

把四路并行工作串成一条可回滚、可验证的集成链：

- 四路在各自 `feature/*` 分支开发并提交；
- 按固定顺序并入 `hbmzz`，每步过 CP；
- 全部通过后集成验证并发布到 `main`；
- 任一 CP 失败即回滚该步，不带病进入下一步。

---

## 2. 四路任务、交付物与依赖

| 路 | 分支 | 核心职责 | 交付物 | 前置依赖 |
|---|---|---|---|---|
| R1 reactor-core | `feature/reactor-core` | 反应堆本体 | reactor 模块源码（方块/TE/网络/渲染）、`tools/checks/check_reactor_offline.sh`、commit | 无（基于 `hbmzz` 基线） |
| R2 self-dependency | `feature/self-dependency` | standalone 独立性 / 合规 | 去诅咒版依赖改动、GPL 头补全、`registry_manifest.txt`、`check_dependency_offline.sh`、commit | 逻辑上依赖 R1 源码以完成合规扫描；合并顺序在 R1 之后 |
| R3 cursed-compat | `feature/cursed-compat` | 诅咒版兼容层 | compat 模块源码、`check_compat_offline.sh`、commit | 依赖 R1 定义的 core 契约；合并顺序在 R2 之后 |
| R4 standards | `feature/standards` | 规范与汇总检查 | 本套 4 份文档 + `tools/checks/check_all_offline.sh`、commit | 依赖前三路脚本存在（否则汇总为 SKIP）；**最后并入** |

依赖关系图：

```
   hbmzz 基线
      │
      ├─▶ R1 reactor-core ──▶ [CP1]
      │        │ (提供 core 契约使用方)
      │        ▼
      ├─▶ R2 self-dependency ──▶ [CP2]
      │
      └─▶ R3 cursed-compat ◀── 依赖 R1 的 core 契约 ──▶ [CP3]

   R4 standards（文档+汇总脚本，依赖 R1/R2/R3 脚本就位）──▶ [CP4]
                                                        │
                                                        ▼
                                                [CP5] 集成验证 / 发布
```

---

## 3. 阶段化执行时间表

| 阶段 | 动作 | 进入条件 | 产出 | 检查点 |
|---|---|---|---|---|
| S0 基线对齐 | team-lead 从 `hbmzz` 拉出四路 feature；各代理确认分支 | `hbmzz` 工作区干净 | 四个 feature 分支就绪 | CP0 |
| S1 反应堆本体 | R1 实现 reactor 模块与离线脚本 | CP0 通过 | R1 commit | CP1 |
| S2 独立性合规 | R2 完成去依赖、GPL 头、注册清单与脚本 | CP1 通过（reactor 源码已并入） | R2 commit | CP2 |
| S3 兼容层 | R3 实现 compat 模块与脚本 | CP2 通过（独立基线锁定） | R3 commit | CP3 |
| S4 规范文档 | R4 写 4 文档 + 汇总脚本 | R1/R2/R3 脚本已就位（或明确 SKIP） | R4 commit | CP4 |
| S5 集成发布 | L4 构建 + L5 真机；打 tag 合入 main | CP4 通过且真机全绿 | release tag + main 合并 | CP5 |

> 四路**编码**可并行推进，但**并入 hbmzz** 严格按 S1→S4 顺序；R4 文档最后并入。

---

## 4. 各路交付物清单

### R1 reactor-core

- [ ] reactor 模块源码置于 `src/reactor/java/com/hbmzz/reactor/`
- [ ] 方块 / TE / 网络包 / 渲染实现完整
- [ ] 无 cursed / compat import（L1 校验）
- [ ] 使用 `core.contract.*` 而非自定义契约
- [ ] `tools/checks/check_reactor_offline.sh`（退出码 0/非0，可独立运行）
- [ ] commit：`feat(reactor): ...`

### R2 self-dependency

- [ ] standalone 源码树无诅咒版引用
- [ ] 所有 Java 文件带 GPL 头
- [ ] `tools/checks/registry_manifest.txt` 覆盖全部注册项
- [ ] 无重复注册
- [ ] `tools/checks/check_dependency_offline.sh`
- [ ] commit：`fix(deps): ...` / `chore(deps): ...`

### R3 cursed-compat

- [ ] compat 模块置于 `src/compat/java/com/hbmzz/compat/`
- [ ] 诅咒版 import 仅在 `compat.impl.*`
- [ ] 提供 `CompatManager` 条件加载入口
- [ ] 可被 standalone 排除（源集隔离 / 条件加载）
- [ ] `tools/checks/check_compat_offline.sh`
- [ ] commit：`feat(compat): ...`

### R4 standards（本路）

- [ ] `docs/MODULE_BOUNDARIES.md`
- [ ] `docs/BRANCHING.md`
- [ ] `docs/VALIDATION.md`
- [ ] `docs/EXECUTION_PLAN.md`
- [ ] `tools/checks/check_all_offline.sh`（可独立运行，缺脚本报 SKIP）
- [ ] commit：`docs(standards): ...`

---

## 5. 合并检查点（CP）

| CP | 合并动作 | 通过标准 | 失败处理 |
|---|---|---|---|
| CP0 | 四路从 hbmzz 拉出 | 四分支存在，基线一致 | 重新同步基线 |
| CP1 | reactor-core → hbmzz | reactor 离线脚本 PASS；L1 无 cursed / 依赖违规 | revert 合并，R1 修复重提 |
| CP2 | self-dependency → hbmzz | 独立性脚本 PASS；注册清单完整；GPL 头齐全 | revert 合并，R2 修复重提 |
| CP3 | cursed-compat → hbmzz | compat 脚本 PASS；诅咒版引用仅在 impl；可排除 | revert 合并，R3 修复重提 |
| CP4 | standards → hbmzz | `check_all_offline.sh` 无 FAIL（SKIP 已说明）；L3 清单通过 | revert 合并，R4 修复重提 |
| CP5 | hbmzz → main + tag | L4 构建通过；L5 六项真机全绿 | 不发版，按回滚方案处理 |

每个 CP 通过后记录合并 commit sha，作为回滚锚点。

---

## 6. 回滚方案

### 6.1 原则

- **优先 `git revert` 合并提交**，保留历史，不改写已共享分支。
- **禁止** `reset --hard`、force push、删除已合并提交作为常规回滚手段。
- 回滚后必须重跑对应 CP 的检查，确认回到"上一个通过状态"。

### 6.2 操作步骤

```bash
# team-lead 执行；<merge-sha> 为该 CP 的合并提交
git checkout hbmzz
git revert -m 1 <merge-sha>
git push origin hbmzz

# 对应子代理在本人 feature 分支修复后重新提交，再触发重新合并
```

### 6.3 分阶段回滚

- **CP1–CP4 失败**：revert 该步合并；对应 feature 保留，修复后重走该 CP，后续 CP 顺延。
- **CP5 失败（已发 main）**：
  1. 立即停止发布，回退 main 到上一个稳定 tag；
  2. 在 hbmzz 上修复并重跑 L4/L5；
  3. 重新走 CP5 发布流程。
- **仅 compat 出问题**：可在 hbmzz 上临时排除 compat 回到 standalone 形态（compat 设计为可整体排除），先保证 standalone 可发布，compat 单独修复。

### 6.4 回滚验证清单

- [ ] revert 后 `git status` 干净；
- [ ] `check_all_offline.sh` 回到无 FAIL 状态；
- [ ] 对应模块缺席性 / 独立性恢复；
- [ ] 已通知相关路负责人与 team-lead。

---

## 7. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 合并冲突（reactor ↔ compat 同改 core 契约） | 阻断 CP | 两路协同解决，契约变更先在 core 达成共识 |
| 三路检查脚本命名 / 接口不一致 | 汇总脚本全部 SKIP，失去保护 | 已通过消息约定候选文件名与退出码；汇总脚本支持多候选探测 |
| 真机环境 / 诅咒版不可用 | L5 无法执行，发布 BLOCKER | 如实标记 SKIP/BLOCKER，禁止带病发布；待环境就绪补测 |
| 诅咒版 API 变更 | compat 适配失效 | 所有 cursed 调用封装在 `compat.impl`，变更隔离在单层 |
| regsiter 清单遗漏 | 注册项未登记 | R2 维护清单，L1 每步校验完整性 |
| 文档描述与最终代码不符 | 规范失真 | R4 最后并入，代码落地后由负责人回写路径 / 包名 |

---

## 8. 完成定义（DoD）

本次 standalone 化工作视为完成，当且仅当：

1. 四路 commit 均已按 `BRANCHING.md` 规范提交；
2. CP1–CP4 全部通过，合并记录与 sha 已留档；
3. `check_all_offline.sh` 无 FAIL；
4. L4 构建通过且 standalone 产物无 compat 类；
5. L5 六项真机检查在 A / B 两环境全部通过；
6. CP5 完成，发布 tag 与 main 合并已记录。
