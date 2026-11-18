# HBMZZ-Core 分支策略与合并规范

> 适用分支：`feature/standards`（本文件），最终并入 `hbmzz`。
> 关联文档：`MODULE_BOUNDARIES.md`、`VALIDATION.md`、`EXECUTION_PLAN.md`。

## 1. 分支模型总览

```
  feature/reactor-core ─┐
  feature/self-dependency ─┤
  feature/cursed-compat ─┤   四路并行（各自独立 commit）
  feature/standards ─────┘
             │  按固定顺序合并（见第 3 节）
             ▼
          hbmzz（集成分支）
             │  集成验证通过后发布
             ▼
          main（稳定分支，只接受来自 hbmzz 的合并）
```

共三层：**稳定层 `main`** ← **集成层 `hbmzz`** ← **开发层 `feature/*`**。
任何 feature 都**不直接**进入 main。

---

## 2. 分支角色定义

### 2.1 main — 稳定分支

- 存放对外发布的稳定版本，永远可构建、可运行。
- **只接受**来自 `hbmzz` 的合并（release / hotfix 除外，hotfix 需 team-lead 批准）。
- 子代理**禁止**向 main 直接 push、强制 push 或提交。
- 每次合入 main 必须打 tag：`vX.Y.Z`。

### 2.2 hbmzz — 集成分支

- 四路 feature 的统一集成点；合并顺序由本文件第 3 节锁定。
- 集成后必须通过 `VALIDATION.md` 的**全套静态 + 离线 + 构建前检查**。
- 子代理**禁止**直接 push；仅 team-lead 执行合并与发布。
- 集成过程中若某路未就绪，保留其 feature 分支不并入，不得跳过检查点。

### 2.3 feature/* — 四路并行开发分支

| 分支 | 负责人（agent） | 职责范围 |
|---|---|---|
| `feature/reactor-core` | reactor-core | reactor 模块实现：反应堆方块 / TE / 网络 / 渲染 |
| `feature/self-dependency` | self-dependency | 去诅咒版依赖：standalone 独立性、GPL 头、注册清单合规 |
| `feature/cursed-compat` | cursed-compat | compat 兼容层：诅咒版 API 适配、coexist 支持 |
| `feature/standards` | standards | 本套规范文档与汇总检查脚本（**最后并入**） |

规则：

- 每个子代理**只在自己的 feature 分支提交**，不得切到他人分支改动。
- 从 `hbmzz` 拉取最新基线后再开工；合并前先 rebase / merge 最新 `hbmzz` 以消除冲突。
- 分支命名固定，不新建 `feature/xxx-extra` 等计划外分支（如确需，先报 team-lead）。

---

## 3. 合并顺序（强制）

四路文档/代码并入 `hbmzz` 的顺序**固定**：

```
1) feature/reactor-core      → hbmzz   （先落地反应堆本体）
2) feature/self-dependency   → hbmzz   （再确保独立性与合规）
3) feature/cursed-compat     → hbmzz   （再接入兼容层）
4) feature/standards         → hbmzz   （最后并入规范文档与汇总脚本）
```

原则与理由：

- **reactor-core 先行**：其它路都以 reactor 的存在为前提（compat 要适配 reactor 契约，合规检查要扫描 reactor 源码）。
- **self-dependency 次之**：在 compat 进入前先锁定"standalone 不含诅咒版依赖"的基线，避免兼容层污染独立性。
- **cursed-compat 再次**：在独立基线稳定后接入诅咒版适配，降低回滚成本。
- **standards 最后**：规范文档描述最终形态，且汇总脚本依赖前三路的检查脚本都已存在，故最后并入。

每一步合并后必须过 `EXECUTION_PLAN.md` 对应的**合并检查点**；未通过不得进入下一路。合并由 team-lead 执行，子代理只提交自己的 feature 分支。

---

## 4. 权限与禁止事项

| 操作 | 子代理 | team-lead |
|---|:---:|:---:|
| 在自己的 feature 分支 commit | ✓ | ✓ |
| push 自己的 feature 分支 | ✓ | ✓ |
| 合并 feature → hbmzz | ✗ | ✓ |
| 推送 / 合并 hbmzz → main | ✗ | ✓ |
| force push 任意共享分支 | ✗ | ✗（需 extraordinary 批准） |
| 删除 / 重写他人分支历史 | ✗ | ✗ |

**红线**：

1. 子代理**禁止**直接向 `main` 或 `hbmzz` push 任何提交。
2. 子代理**禁止** checkout 到非本人 feature 分支进行写操作。
3. 任何 force push、`reset --hard`、`rebase` 已合并提交，必须先经 team-lead 确认。
4. 合并冲突由相关两路负责人协同解决，不得单方丢弃对方改动。

---

## 5. Commit Message 规范

采用 **Conventional Commits** 风格：

```
<type>(<scope>): <subject>

[可选 body：说明动机与实现]
[可选 footer：BREAKING CHANGE / 关联项]
```

### 5.1 type 取值

| type | 含义 | 示例 |
|---|---|---|
| `feat` | 新功能 | `feat(reactor): add multiblock reactor registration` |
| `fix` | 缺陷修复 | `fix(compat): handle missing cursed API at runtime` |
| `docs` | 文档 | `docs(standards): add module boundary specification` |
| `refactor` | 不改行为的重构 | `refactor(core): move contract interfaces to core.contract` |
| `test` | 测试 / 检查脚本 | `test(standards): add offline check aggregator` |
| `chore` | 构建 / 配置 / 杂项 | `chore: align check script exit codes` |

### 5.2 scope 取值

与模块对应：`core` / `reactor` / `compat` / `hbmzz` / `standards` / `deps`。

### 5.3 各路推荐前缀

- reactor-core：`feat(reactor):` / `fix(reactor):`
- self-dependency：`fix(deps):` / `chore(deps):` / `test(deps):`
- cursed-compat：`feat(compat):` / `fix(compat):`
- standards：`docs(standards):` / `test(standards):`（本路提交统一以 `docs(standards):` 开头）

### 5.4 规则

- subject 用**祈使句**，不超过 72 字符，首字母小写，句尾无句号。
- 一个 commit 只做一件事；文档与代码分开提交。
- 禁止 `update`、`wip`、`tmp`、`asdf` 等无意义 message。
- 合并提交由 team-lead 生成，格式为 `Merge branch 'feature/xxx' into hbmzz`。

---

## 6. 典型工作流（子代理视角）

```bash
git checkout feature/standards       # 仅在本人分支
git fetch origin && git rebase origin/hbmzz   # 同步基线（如需要）
# ... 修改文件 ...
git add <具体文件>
git commit -m "docs(standards): add branching policy"
git push origin feature/standards
# 合并交给 team-lead，不自行合并到 hbmzz/main
```

> 注意：本仓库当前为骨架，合并动作由 team-lead 统一执行；子代理交付以"本人 feature 分支上的 commit sha"为准。
