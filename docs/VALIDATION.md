# HBMZZ-Core 验证标准

> 适用分支：`feature/standards`（本文件），最终并入 `hbmzz`。
> 关联文档：`MODULE_BOUNDARIES.md`、`BRANCHING.md`、`EXECUTION_PLAN.md`。
> 说明：仓库当前为骨架，下列命令中的源码路径按 `MODULE_BOUNDARIES.md` 约定；代码落地后启用。所有命令从**仓库根目录**执行。

## 1. 验证分层总览

| 层 | 名称 | 内容 | 执行者 | 覆盖方式 |
|---|---|---|---|---|
| L1 | 静态检查 | 禁止 import、GPL 头、注册清单、冲突标记 | 脚本 / 任意人 | **静态可覆盖** |
| L2 | 离线结构检查 | 四路 check 脚本 + 汇总脚本、模块缺席性、契约位置 | 脚本（无需构建） | **静态可覆盖** |
| L3 | 构建前检查 | 分支 / 工作区 / 合并顺序 / 清单就绪 | 脚本 + 人工确认 | 静态为主 + 人工 |
| L4 | 构建检查 | Gradle 编译、产物结构（standalone 无 compat） | 构建环境 | 半静态（需构建产物） |
| L5 | 真机检查 | 启动、方块、TE NBT、网络、渲染、卸载兼容 | 人工，双环境 | **必须真机** |

判定等级：

- **PASS**：检查通过；
- **FAIL**：检查未通过，阻断合并；
- **SKIP**：前置条件不满足（如脚本不存在、环境不具备），需记录原因，**不阻断但须说明**；
- **BLOCKER**：必须真机验证且未验证，禁止发布。

---

## 2. L1 静态检查

### 2.1 禁止诅咒版 import（核心检查）

诅咒版包名匹配模式（约定，可按实际调整）：

```
CURS_PATTERN='com\.hbm\.cursed|com\.cursed|hbmcursed|cursedhbm|com\.hbm\.ntm\.cursed'
```

**规则**：除 `src/compat/` 外，任何源码不得命中 `CURS_PATTERN`。

```bash
CURS_PATTERN='com\.hbm\.cursed|com\.cursed|hbmcursed|cursedhbm|com\.hbm\.ntm\.cursed'

echo "== 非 compat 模块禁止诅咒版 import =="
grep -RInE "$CURS_PATTERN" \
  src/core src/reactor src/main \
  --include='*.java' && { echo "FAIL: 非 compat 模块出现诅咒版引用"; exit 1; } || \
  echo "PASS: 非 compat 模块无诅咒版引用"
```

**规则**：compat 中诅咒版 import 只能出现在 `com.hbmzz.compat.impl.*` 白名单包内。

```bash
echo "== compat 诅咒版引用白名单校验 =="
violations=$(grep -RlE "$CURS_PATTERN" src/compat --include='*.java' | \
  grep -v '/compat/impl/')
[ -n "$violations" ] && { echo "FAIL: 以下文件在 compat.impl 之外引用诅咒版:"; echo "$violations"; exit 1; } \
  || echo "PASS: 诅咒版引用仅限 compat.impl"
```

### 2.2 模块依赖方向检查

```bash
echo "== core 不得依赖上层模块 =="
grep -RInE 'import com\.hbmzz\.(reactor|compat)|import com\.hbmzz\.HBMZZ' \
  src/core --include='*.java' && { echo "FAIL"; exit 1; } || echo "PASS: core 依赖方向正确"

echo "== reactor 不得依赖 compat =="
grep -RInE 'import com\.hbmzz\.compat' src/reactor --include='*.java' \
  && { echo "FAIL"; exit 1; } || echo "PASS: reactor 未依赖 compat"

echo "== hbmzz 主模块不得直接引用 compat 实现类 =="
grep -RInE 'import com\.hbmzz\.compat\.impl' src/main --include='*.java' \
  && { echo "FAIL"; exit 1; } || echo "PASS: 主模块未直接引用 compat 实现"
```

> 说明：hbmzz 对 `compat.api.CompatManager` 的条件性引用是允许的，仅 `compat.impl` 禁止直接引用。

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

### 2.4 注册清单检查

约定清单文件：`tools/checks/registry_manifest.txt`，每行一个注册 ID（方块 / 物品 / 实体 / 配方）。

```bash
MANIFEST=tools/checks/registry_manifest.txt
[ ! -f "$MANIFEST" ] && { echo "SKIP: 注册清单不存在 ($MANIFEST)"; exit 0; }

echo "== 注册清单完整性 =="
miss=0
while IFS= read -r id; do
  [ -z "$id" ] && continue
  case "$id" in \#*) continue;; esac
  grep -Rqs -- "$id" src/ || { echo "MISSING REGISTRATION: $id"; miss=1; }
done < "$MANIFEST"
[ "$miss" -eq 0 ] && echo "PASS: 注册清单完整" || { echo "FAIL"; exit 1; }

echo "== 重复注册检查 =="
dup=$(grep -RhoE '(register|registerObject)\([^)]*"[^"]+"' src/ --include='*.java' \
  | sort | uniq -d)
[ -n "$dup" ] && { echo "FAIL: 重复注册:"; echo "$dup"; exit 1; } || echo "PASS: 无重复注册"
```

### 2.5 冲突标记检查

```bash
echo "== 未解决冲突标记检查 =="
grep -RInE '^(<<<<<<<|=======|>>>>>>>)' src/ docs/ tools/ --include='*' \
  && { echo "FAIL: 存在冲突标记"; exit 1; } || echo "PASS: 无冲突标记"
```

### 2.6 契约接口位置检查

```bash
echo "== 跨模块契约必须定义在 core.contract =="
grep -RInE 'interface I(EnergyBridge|RegistrySink)' src/reactor src/compat --include='*.java' \
  && { echo "FAIL: 契约接口定义在 core 之外"; exit 1; } \
  || echo "PASS: 契约接口集中在 core.contract"
```

---

## 3. L2 离线结构检查

### 3.1 四路检查脚本约定

| 路 | 分支 | 脚本候选（位于 `tools/checks/`） | 退出码 |
|---|---|---|---|
| reactor-core | `feature/reactor-core` | `check_reactor_offline.sh`, `check_reactor.sh` | 0=PASS, 非0=FAIL |
| self-dependency | `feature/self-dependency` | `check_dependency_offline.sh`, `check_self_dependency_offline.sh`, `check_self_dependency.sh` | 0=PASS, 非0=FAIL |
| cursed-compat | `feature/cursed-compat` | `check_compat_offline.sh`, `check_cursed_compat_offline.sh`, `check_compat.sh` | 0=PASS, 非0=FAIL |
| standards（汇总） | `feature/standards` | `check_all_offline.sh` | 0=PASS(含SKIP), 非0=FAIL |

各路脚本职责建议：

- **reactor**：reactor 模块源码存在、无 cursed / compat 引用、契约接口在 core、注册清单覆盖 reactor 方块。
- **self-dependency**：standalone 源码树无 cursed 引用、GPL 头完整、无 compat 编译依赖声明。
- **cursed-compat**：compat 源码存在、cursed 引用仅在 impl、提供 CompatManager 入口、可被条件排除。

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

- 四模块源码目录存在且非空；
- `compat` 可从 standalone 构建中整体排除（构建脚本含条件或源集隔离，由 self-dependency / cursed-compat 脚本校验）；
- 契约接口位于 `core.contract`，reactor / compat 均面向契约；
- 注册清单存在且每项在源码有对应实现。

---

## 4. L3 构建前检查

合并到 `hbmzz` 前、以及在集成分支上打 tag 前执行：

```bash
echo "== 当前分支 =="
git rev-parse --abbrev-ref HEAD     # 必须为 hbmzz（team-lead 执行）

echo "== 工作区干净 =="
git status --porcelain              # 必须为空

echo "== L1 静态检查 =="
bash tools/checks/check_all_offline.sh   # 本质为 L1+L2 汇总

echo "== 元数据文件 =="
ls mods.toml mcmod.info pack.mcmeta 2>/dev/null || echo "检查元数据是否齐全"
```

人工确认清单：

- [ ] 四路 feature 均已 commit 且 push，无未合并改动；
- [ ] 合并顺序符合 `BRANCHING.md` 第 3 节；
- [ ] 本次变更新增 / 删除的注册项已同步到 `registry_manifest.txt`；
- [ ] 新增 Java 文件已带 GPL 头；
- [ ] `check_all_offline.sh` 无 FAIL（SKIP 项已逐条确认原因）。

---

## 5. L4 构建检查

> 由 team-lead 在构建环境执行；本代理不运行 gradle。

- [ ] Gradle 编译通过（无编译错误）；
- [ ] standalone 产物中**不含** compat 类（解包 jar 检查 `com/hbmzz/compat/` 不存在）；
- [ ] coexist 产物包含 compat 且能检测诅咒版；
- [ ] 产物元数据（mod id / 版本 / 依赖声明）正确；
- [ ] 资源文件（模型、纹理、lang）完整打包。

判定：standalone 产物出现 compat 类 = **FAIL**，阻断发布。

---

## 6. L5 真机检查清单（必须真机）

两种环境矩阵：

- **环境 A（standalone）**：仅安装本 mod，**不装**诅咒版；
- **环境 B（coexist）**：同时安装本 mod 与诅咒版。

| # | 检查项 | 环境 A | 环境 B | 通过标准 | 覆盖方式 |
|---|---|:---:|:---:|---|---|
| 1 | 启动 | 必测 | 必测 | 无崩溃，日志无 cursed 相关报错；A 中不加载 compat，B 中加载 compat | 真机 |
| 2 | 方块放下 | 必测 | 必测 | 反应堆方块可放置、可破坏，掉落物正确 | 真机 |
| 3 | TE NBT 持久化 | 必测 | 必测 | 放置后设置状态 → 保存退出 → 重进，NBT 状态保持；无丢数据 | 真机 |
| 4 | 网络包同步 | 必测 | 必测 | GUI 打开、能量/状态显示客户端与服务端一致；无 desync | 真机 |
| 5 | 渲染 | 必测 | 必测 | TESR / 模型正确显示，无缺失纹理、无异常闪烁 | 真机 |
| 6 | 卸载兼容性 | 必测 | 必测 | 存档含本 mod 方块时移除 mod，游戏可启动不崩溃；B 中单独移除诅咒版或单独移除本 mod 均可启动 | 真机 |

### 6.1 操作步骤要点

- **启动**：分别在 A / B 环境启动客户端与专用服务端，检查日志关键字 `ERROR` / `cursed` / `CompatManager`。
- **方块放下**：生存 / 创造模式各放一次，确认方块实体（TE）创建。
- **TE NBT**：写入非默认状态（如能量值 / 配方），退出并重载，对比值一致。
- **网络包**：多人或单人开 GUI，修改服务端状态观察客户端是否同步；检查服务端日志无 `Packet` 异常。
- **渲染**：近距离与远距离观察反应堆模型，检查透明层、动态部件。
- **卸载兼容性**：备份存档 → 移除 mod → 启动 → 恢复 mod → 再次启动，确认存档可用。

### 6.2 静态不可覆盖项说明

以下**无法**由静态 / 离线脚本判定，必须真机：

- 运行时崩溃、TE NBT 实际持久化、网络同步时序、渲染表现、跨 mod 交互（卸载兼容）。
- 脚本只能保证"结构上具备条件"（如 compat 缺席、契约存在），不能保证"运行期行为正确"。

---

## 7. 覆盖矩阵（汇总）

| 检查项 | L1 静态 | L2 离线 | L3 构建前 | L4 构建 | L5 真机 |
|---|:---:|:---:|:---:|:---:|:---:|
| 禁止诅咒版 import | ✓ | ✓ | ✓ | — | — |
| GPL 头 | ✓ | ✓ | ✓ | — | — |
| 注册清单 | ✓ | ✓ | ✓ | — | — |
| 依赖方向 / 契约位置 | ✓ | ✓ | ✓ | — | — |
| 冲突标记 | ✓ | — | ✓ | — | — |
| 四路脚本汇总 | — | ✓ | ✓ | — | — |
| 产物无 compat 类 | — | — | — | ✓ | — |
| 启动 / 崩溃 | — | — | — | — | ✓ |
| 方块放下 / TE NBT / 网络 / 渲染 | — | — | — | — | ✓ |
| 卸载兼容性 | — | — | — | — | ✓ |

**结论**：L1–L4 可自动化覆盖结构与合规；L5 六项必须真机，任一未通过则发布状态为 **BLOCKER**。
