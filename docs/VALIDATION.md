# HBMZZ-Core 验证标准

> 适用分支：`feature/standards`（本文件），最终并入 `hbmzz`。
> 关联文档：`MODULE_BOUNDARIES.md`、`BRANCHING.md`、`EXECUTION_PLAN.md`。
> 说明：**已按实际落地代码回写**。源码为单源集 `src/main/java/com/hbmzz/`；检查脚本位于 `tools/checks/`，实际名称为 `check_reactor.sh`、`check_self_dependency.sh`、`check_cursed_compat.sh`、`check_all_offline.sh`（另附编译证明 `compile_standalone.sh`）。所有命令从**仓库根目录**执行。

## 1. 验证分层总览

| 层 | 名称 | 内容 | 执行者 | 覆盖方式 |
|---|---|---|---|---|
| L1 | 静态检查 | 零 `import com.hbm.*`、GPL 头、冲突标记 | 脚本 / 任意人 | **静态可覆盖** |
| L2 | 离线结构检查 | 四路 check 脚本 + 汇总脚本、编译证明、模块边界 | 脚本（无需构建） | **静态可覆盖** |
| L3 | 构建前检查 | 分支 / 工作区 / 合并顺序 / 脚本就位 | 脚本 + 人工确认 | 静态为主 + 人工 |
| L4 | 构建检查 | Gradle 编译、standalone 交叉编译证明、产物结构 | 构建环境 | 半静态（需构建产物 / javac） |
| L5 | 真机检查 | 启动、方块、TE NBT、网络、渲染、卸载兼容 | 人工，双环境 | **必须真机** |

判定等级：

- **PASS**：检查通过；
- **FAIL**：检查未通过，阻断合并；
- **SKIP**：前置条件不满足（如脚本不存在、环境不具备），需记录原因，**不阻断但须说明**；
- **BLOCKER**：必须真机验证且未验证，禁止发布。

---

## 2. L1 静态检查

### 2.1 禁止诅咒版 import（核心检查）

实际约束：compat 采用纯反射，**全仓库零编译期 `import com.hbm.*`**，诅咒版类名仅以字符串常量出现在 `CursedBridge` 中。无白名单例外。

```bash
echo "== 全仓库禁止诅咒版 import =="
grep -RInE '^import[[:space:]]+com\.hbm\.' src/ --include='*.java' \
  && { echo "FAIL: 出现 import com.hbm.*"; exit 1; } || echo "PASS: 全仓库零 import com.hbm.*"
```

**规则**：任何源码命中 `import com.hbm.*` 即失败；compat 的诅咒版类名只能是字符串常量，不得成为 import。

### 2.2 模块边界检查

```bash
echo "== core 对 reactor 的引用仅在白名单类型 =="
grep -RInE 'import com\.hbmzz\.reactor\.' src/main/java/com/hbmzz/core --include='*.java' \
  | grep -vE 'ReactorRegistry|ReactorController|ReactorGateway|ReactorView' \
  && { echo "FAIL: core 引用了 reactor 白名单之外的类型"; exit 1; } \
  || echo "PASS: core 对 reactor 引用在白名单内"

echo "== reactor.api 仅依赖 java.* =="
grep -RInE '^import[[:space:]]+' src/main/java/com/hbmzz/reactor/api --include='*.java' \
  | grep -vE '^[^:]+:[0-9]+:import[[:space:]]+java\.' \
  && { echo "FAIL: reactor.api 依赖了 java.* 之外"; exit 1; } \
  || echo "PASS: reactor.api 仅依赖 java.*"

echo "== 禁止 ServiceLoader reactor 发现逻辑 =="
grep -RIn 'ServiceLoader' src/ --include='*.java' \
  && { echo "FAIL: 出现 ServiceLoader 发现逻辑"; exit 1; } || echo "PASS: 无 ServiceLoader"
```

> 说明：主模块经 `ReactorRegistry.instance()` 单例访问 reactor，不使用也不允许使用 `ServiceLoader`。reactor 对外只暴露 `com.hbmzz.reactor.api.ReactorGateway` / `ReactorView`。

### 2.3 GPL 头检查

```bash
echo "== GPL 许可证头检查 =="
missing=0
while IFS= read -r f; do
  head -n 30 "$f" | grep -qiE 'GNU General Public License|GPL' || {
    echo "MISSING GPL HEADER: $f"; missing=1;
  }
done < <(find src -name '*.java')
[ "$missing" -eq 0 ] && echo "PASS: 所有源文件含 GPL 头" || { echo "FAIL"; exit 1; }
```

### 2.4 冲突标记检查

```bash
echo "== 未解决冲突标记检查 =="
grep -RInE '^(<<<<<<<|=======|>>>>>>>)' src/ docs/ tools/ --include='*' \
  && { echo "FAIL: 存在冲突标记"; exit 1; } || echo "PASS: 无冲突标记"
```

### 2.5 单源集结构检查

```bash
echo "== 源码须位于单源集 src/main/java =="
[ -d src/reactor ] || [ -d src/core ] || [ -d src/compat ] \
  && { echo "FAIL: 出现分离源集目录"; exit 1; } || echo "PASS: 单源集结构"
```

> 关于注册清单：规划期曾约定 `registry_manifest.txt` 作为注册项对账清单，**当前骨架未实现，作为建议项，发布前补**。实现前相关检查记 SKIP，不作为阻断项；实现后按"清单每项在源码有对应实现、无重复注册"校验。

---

## 3. L2 离线结构检查

### 3.1 四路检查脚本（实际名称）

| 路 | 分支 | 脚本（位于 `tools/checks/`） | 退出码 |
|---|---|---|---|
| reactor-core | `feature/reactor-core` | `check_reactor.sh` | 0=PASS, 非0=FAIL |
| self-dependency | `feature/self-dependency` | `check_self_dependency.sh` | 0=PASS, 非0=FAIL |
| cursed-compat | `feature/cursed-compat` | `check_cursed_compat.sh` | 0=PASS, 非0=FAIL |
| standards（汇总） | `feature/standards` | `check_all_offline.sh` | 0=PASS(含SKIP), 非0=FAIL |
| 附加：编译证明 | `feature/standards` | `compile_standalone.sh` | 0=交叉编译通过, 非0=失败 |

各路脚本实际职责：

- **reactor**（`check_reactor.sh`）：reactor 无 `com.hbm.*` 依赖、无诅咒版类名、import 白名单、GPL 头、网络包命名与注册配对、文件清单完整；`api` 包只依赖 `java.*`。
- **self-dependency**（`check_self_dependency.sh`）：core 无 `import com.hbm.*`、无诅咒版包名、对 reactor 引用仅 `ReactorRegistry` / `ReactorController` 白名单、无旧签名 `register(String, ReactorController)`、无 `ServiceLoader` 发现逻辑；模块注册门面与 standalone 启动入口存在。
- **cursed-compat**（`check_cursed_compat.sh`）：compat 无 `import com.hbm.*`、源码与清单无强制依赖声明、存在 `after:hbm` 软依赖、`CursedCompat` / `CursedBridge` / `CompatMode` 存在且类名仅字符串常量。

### 3.2 汇总脚本

`tools/checks/check_all_offline.sh` 负责探测并执行上述三路脚本：

- 找到脚本 → 执行并按退出码记 PASS / FAIL；
- 未找到 → 记 **SKIP**（不失败），保证自身可独立运行；
- 三路全 SKIP 时仍返回 0（PASS），但输出中标注 SKIP 原因；
- 任一 FAIL 则整体退出码非 0。

```bash
bash tools/checks/check_all_offline.sh
```

### 3.3 离线结构检查项（不依赖构建）

- 四路脚本就位且可独立运行；
- 模块以单源集包划分，reactor 对外仅 `reactor.api`；
- 全仓库零 `import com.hbm.*`；compat 仅字符串常量 + 反射；
- `compile_standalone.sh` 证明三套源码在无诅咒版类时可交叉编译通过。

---

## 4. L3 构建前检查

合并到 `hbmzz` 前、以及在集成分支上打 tag 前执行：

```bash
echo "== 当前分支 =="
git rev-parse --abbrev-ref HEAD     # 必须为 hbmzz（team-lead 执行）

echo "== 工作区干净 =="
git status --porcelain              # 必须为空

echo "== L1 静态检查 =="
bash tools/checks/check_all_offline.sh   # L1+L2 汇总

echo "== 元数据文件 =="
ls mods.toml mcmod.info pack.mcmeta 2>/dev/null || echo "检查元数据是否齐全"
```

人工确认清单：

- [ ] 四路 feature 均已 commit 且 push，无未合并改动；
- [ ] 合并顺序符合 `BRANCHING.md` 第 3 节；
- [ ] 新增 Java 文件已带 GPL 头；
- [ ] 未出现 `import com.hbm.*` 或 `ServiceLoader` 发现逻辑；
- [ ] （建议项，未实现）注册清单就绪后再启用对账检查；
- [ ] `check_all_offline.sh` 无 FAIL（SKIP 项已逐条确认原因）。

---

## 5. L4 构建检查

> 由 team-lead 在构建环境执行；本代理不运行 gradle。

- [ ] Gradle 编译通过（无编译错误）；
- [ ] `bash tools/checks/compile_standalone.sh` 通过：core + reactor + compat 三套源码在无诅咒版类（最小 MC/Forge 桩）前提下交叉编译成功；
- [ ] 产物元数据（mod id / 版本 / `after:hbm` 软依赖声明）正确，无强制依赖诅咒版；
- [ ] 资源文件（模型、纹理、lang）完整打包。

判定：`compile_standalone.sh` 失败或产物出现强制诅咒版依赖 = **FAIL**，阻断发布。

---

## 6. L5 真机检查清单（必须真机）

两种环境矩阵：

- **环境 A（standalone）**：仅安装本 mod，**不装**诅咒版；
- **环境 B（coexist）**：同时安装本 mod 与诅咒版。

| # | 检查项 | 环境 A | 环境 B | 通过标准 | 覆盖方式 |
|---|---|:---:|:---:|---|---|
| 1 | 启动 | 必测 | 必测 | 无崩溃，日志无 cursed 相关报错；A 为 `STANDALONE`，B 为 `COEXIST`，桥接不可用时优雅降级 | 真机 |
| 2 | 方块放下 | 必测 | 必测 | 反应堆方块可放置、可破坏，掉落物正确 | 真机 |
| 3 | TE NBT 持久化 | 必测 | 必测 | 放置后设置状态 → 保存退出 → 重进，NBT 状态保持；无丢数据 | 真机 |
| 4 | 网络包同步 | 必测 | 必测 | GUI 打开、能量/状态显示客户端与服务端一致；无 desync（`ReactorNetworkMessages` 配对正确） | 真机 |
| 5 | 渲染 | 必测 | 必测 | TESR / 模型正确显示，无缺失纹理、无异常闪烁 | 真机 |
| 6 | 卸载兼容性 | 必测 | 必测 | 存档含本 mod 方块时移除 mod，游戏可启动不崩溃；B 中单独移除诅咒版或单独移除本 mod 均可启动 | 真机 |

### 6.1 操作步骤要点

- **启动**：分别在 A / B 环境启动客户端与专用服务端，检查日志关键字 `ERROR` / `cursed` / `CursedCompat`；确认 B 环境 `CursedCompat.detect()` 返回 `COEXIST`，且 `CursedBridge` 反射桥接在可用时增强、不可用时降级。
- **方块放下**：生存 / 创造模式各放一次，确认方块实体（TE）创建。
- **TE NBT**：写入非默认状态（如能量值 / 配方），退出并重载，对比值一致。
- **网络包**：多人或单人开 GUI，修改服务端状态观察客户端是否同步；检查服务端日志无 `Packet` 异常。
- **渲染**：近距离与远距离观察反应堆模型，检查透明层、动态部件。
- **卸载兼容性**：备份存档 → 移除 mod → 启动 → 恢复 mod → 再次启动，确认存档可用。

### 6.2 静态不可覆盖项说明

以下**无法**由静态 / 离线脚本判定，必须真机：

- 运行时崩溃、TE NBT 实际持久化、网络同步时序、渲染表现、跨 mod 交互（卸载兼容）、反射桥接在真实诅咒版类上的成功 / 降级行为。
- 脚本只能保证"结构上具备条件"（零 import、契约就位、交叉编译通过），不能保证"运行期行为正确"。

---

## 7. 覆盖矩阵（汇总）

| 检查项 | L1 静态 | L2 离线 | L3 构建前 | L4 构建 | L5 真机 |
|---|:---:|:---:|:---:|:---:|:---:|
| 零 `import com.hbm.*` | ✓ | ✓ | ✓ | — | — |
| GPL 头 | ✓ | ✓ | ✓ | — | — |
| 模块边界 / reactor.api 契约 | ✓ | ✓ | ✓ | — | — |
| 冲突标记 / 单源集结构 | ✓ | — | ✓ | — | — |
| 四路脚本汇总 | — | ✓ | ✓ | — | — |
| standalone 交叉编译证明 | — | ✓ | — | ✓ | — |
| 产物无强制诅咒版依赖 | — | — | — | ✓ | — |
| 启动 / 崩溃 | — | — | — | — | ✓ |
| 方块放下 / TE NBT / 网络 / 渲染 | — | — | — | — | ✓ |
| 卸载兼容性 / 反射桥接行为 | — | — | — | — | ✓ |

**结论**：L1–L4 可自动化覆盖结构与合规；L5 六项必须真机，任一未通过则发布状态为 **BLOCKER**。
