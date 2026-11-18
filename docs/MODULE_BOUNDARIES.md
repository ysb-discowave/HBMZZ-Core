# HBMZZ-Core 模块边界规范

> 状态：**已按 hbmzz 分支实际落地代码回写（定稿）**。下列源码路径、包名、类名均对应 `hbmzz` 已合并代码，非规划约定。
> 适用分支：`feature/standards`（规范文档），最终并入 `hbmzz`。
> 关联文档：`BRANCHING.md`、`VALIDATION.md`、`EXECUTION_PLAN.md`、`DESIGN-*.md`。

## 1. 目标与适用范围

本规范锁定四件事：

1. 四个模块（`core` / `reactor` / `compat` / `hbmzz` 主模块）各自**只做什么**；
2. 模块之间**允许 / 禁止的依赖方向**；
3. 每个模块对外暴露的**公开接口**与不得外泄的**私有实现**；
4. `reactor` 与 `compat` 的边界，以及 **standalone / coexist** 两种部署形态下的数据流。

一切静态检查（grep、离线脚本）都以本文件为准。违反依赖方向的改动视为**编译前的硬失败**，不进入合并检查点。实际 API 签名以 `docs/DESIGN-reactor.md`、`docs/DESIGN-self-dependency.md`、`docs/DESIGN-cursed-compat.md` 为准。

---

## 2. 模块总览

实际采用**单一源集**：所有代码位于 `src/main/java/com/hbmzz/`，模块以 Java 包划分，不使用多源集分离。

```
                       ┌──────────────────────────────┐
                       │      com.hbmzz（集成层）      │
                       │  ModuleRegistry/Registry/...  │
                       └───┬──────────┬──────────┬──────┘
                           │ depends  │ depends  │ optional
                           ▼          ▼          ▼
                   ┌───────────┐ ┌──────────┐ ┌──────────────┐
                   │   core    │ │ reactor  │ │   compat      │
                   │ 装配/启动  │ │反应堆系统 │ │诅咒版反射桥接 │
                   └─────┬─────┘ └────┬─────┘ └──────┬───────┘
                         │            │              │ 纯反射（零 import）
                         └────────────┘              │
                      core 依赖 reactor（编译期）      ▼
                                            诅咒版 API（仅字符串常量 / 反射）
```

源码布局（相对仓库根，均为单源集 `src/main/java` 下的包）：

| 模块 | 包路径 | 主要落地类 |
|---|---|---|
| core | `com.hbmzz.core` | `ModuleRegistry`、`HbmzzModules`、`StandaloneBootstrap` |
| reactor | `com.hbmzz.reactor` | `ReactorRegistry`、`ReactorState`、`ReactorController`、`ReactorNetworkMessages`、`ReactorTier` |
| reactor（对外契约） | `com.hbmzz.reactor.api` | `ReactorGateway`、`ReactorView` |
| compat | `com.hbmzz.compat` | `CursedCompat`、`CursedBridge`、`CompatMode` |

> 全部为同一源集 `src/main/java/com/hbmzz/` 下的子包，**不存在** `src/reactor/java`、`src/compat/java`、`src/core/java` 之类的分离源集。资源路径：`src/main/resources/`。

---

## 3. 依赖规则（硬约束）

### 3.1 依赖矩阵

行 = 依赖方，列 = 被依赖方。`✓` 允许，`✗` 禁止，`△` 仅条件允许。

| 依赖方 ↓ / 被依赖方 → | core | reactor.api | reactor（实现） | compat | 诅咒版 API |
|------------------------|:----:|:-----------:|:---------------:|:------:|:----------:|
| **core**               |  —   |     ✓       |        ✓        |   ✗    |     ✗      |
| **reactor.api**        |  ✗   |     —       |        ✗        |   ✗    |     ✗      |
| **reactor（实现）**    |  ✗   |     ✓       |        —        |   ✗    |     ✗      |
| **compat**             |  ✗   |     ✗       |        ✗        |   —    |     ✗（仅反射，无 import） |
| **com.hbmzz 集成层**   |  ✓   |     ✓       |        ✗        |   △    |     ✗      |

符号说明：

- `△`：仅 coexist 形态下、由集成层经 `CursedCompat` 判定后间接使用 `compat`；主模块**不得**直接依赖实现细节。
- `reactor.api` 只依赖 `java.*`，是最底层对外契约。
- **实际依赖方向是 core → reactor**（见 `DESIGN-self-dependency.md`）：core 的 `HbmzzModules` / `StandaloneBootstrap` 编译期依赖 `ReactorRegistry` / `ReactorController`，而非相反。
- 诅咒版 API 一列全为 `✗`：全仓库**零编译期 `import com.hbm.*`**，这是 standalone 独立性的根本保证。

### 3.2 单向依赖链

合法依赖只能沿以下方向（箭头 = "可以依赖"）：

```
com.hbmzz (集成层) ──▶ core ──▶ reactor ──▶ reactor.api ──▶ java.*
                 └─▶ reactor.api（只读快照 ReactorView）
compat ──▶ （无编译期依赖；类名仅字符串常量，运行期纯反射）
```

由此推出的铁律：

- **铁律 1**：`reactor.api`（`ReactorGateway` / `ReactorView`）是最底层契约，只依赖 `java.*`；主模块只读访问必须收敛到此包。
- **铁律 2**：`core` 依赖**自有 reactor**（`ReactorRegistry` 单例 / `ReactorController`），不是可 `ServiceLoader` 发现的接口；`ReactorRegistry` 是 `final class` 单例，通过 `instance()` 获取、`install()` 绑定、`registerController()` / `controller(id)` 注册查询。
- **铁律 3**：`reactor` 与 `compat` **互不依赖**；compat 通过纯反射桥接，诅咒版类名仅以**字符串常量**出现，**全仓库零 `import com.hbm.*`**。
- **铁律 4**：主模块只能拿到 `ReactorView` 只读快照，拿不到 `ReactorController` / `ReactorState` / `ReactorTier` 等实现类型。

---

## 4. 各模块详解

### 4.1 core — 装配与启动层

**职责**：standalone 模式的装配与启动，是连接主模块与自有 reactor 的门面。

- `ModuleRegistry`：模块注册契约，定义模块 id 与初始化 / tick / 释放生命周期；
- `HbmzzModules`：模块加载门面，持有 `ReactorRegistry`，提供注册 / 查询 / 初始化 / tick / 释放入口；
- `StandaloneBootstrap`：standalone 启动入口，装配 `ReactorRegistry` 并构建 `HbmzzModules`。

**允许依赖**：`reactor`（`ReactorRegistry` / `ReactorController`）、`reactor.api`（`ReactorGateway` / `ReactorView`）、Minecraft / Forge 基础 API。

**禁止依赖**：`compat`、诅咒版 API（`import com.hbm.*`）。core 中禁止出现任何诅咒版包名或类名；对 reactor 的引用只落在白名单类型上。

**公开接口**：
- `com.hbmzz.core.ModuleRegistry`：模块注册契约；
- `com.hbmzz.core.HbmzzModules`：模块加载门面；
- `com.hbmzz.core.StandaloneBootstrap`：standalone 启动生命周期。

**私有实现**：core 自身即装配层，无对外隐藏的实现子包；具体 reactor 实现属于 `com.hbmzz.reactor`，不属于 core。

**合规要点**：所有源文件必须带 GPL 头（见 `VALIDATION.md` 2.3）；core 对 reactor 的引用白名单由 `check_self_dependency.sh` 校验。

---

### 4.2 reactor — 反应堆系统

**职责**：反应堆玩法本体，是 standalone 功能的唯一来源；对外仅暴露只读契约。

- `ReactorRegistry`：`final class` 单例，注册入口（`instance()` / `install()` / `registerController()` / `controller(id)`）；
- `ReactorState` / `ReactorTier` / `ReactorController`：状态、等级、控制器实现；
- `ReactorNetworkMessages`：网络包定义；
- `reactor.api.ReactorGateway` / `ReactorView`：对外接口与只读快照。

**允许依赖**：`reactor.api`（实现层实现契约）、Minecraft / Forge 基础 API。

**禁止依赖**：`core`、`compat`、诅咒版 API。`api` 包只依赖 `java.*`。

**公开接口**：
- `com.hbmzz.reactor.api.ReactorGateway`：对外网关；
- `com.hbmzz.reactor.api.ReactorView`：只读状态视图（只含基本类型）。

**私有实现**：
- `com.hbmzz.reactor.ReactorRegistry` / `ReactorState` / `ReactorController` / `ReactorTier` / `ReactorNetworkMessages`：主模块不得依赖这些具体类型。

**边界说明**：主模块只能经 `ReactorGateway` 拿到 `ReactorView` 只读快照；**不使用 `ServiceLoader` 发现机制**。reactor 侧不含任何 cursed 引用（由 `check_reactor.sh` 强校验）。

---

### 4.3 compat — 诅咒版兼容层

**职责**：以**纯反射**方式在 coexist 下桥接诅咒版能力；不产生任何编译期依赖。

- `CursedCompat`：模式判定入口，`Loader.isModLoaded("hbm")` 检测，返回 `CompatMode`；异常安全降级为 STANDALONE；
- `CursedBridge`：**唯一**与诅咒版交互的反射门面，类名仅以字符串常量出现；
- `CompatMode`：`STANDALONE` / `COEXIST` 枚举。

**允许依赖**：仅 Minecraft / Forge 基础 API（`Loader` 等）；诅咒版 API **仅运行期反射**，无 `import`。

**禁止依赖**：`core`、`reactor`、`reactor.api`。不得复制 reactor 玩法逻辑，只做"反射翻译"。

**公开接口**：
- `com.hbmzz.compat.CursedCompat.detect()` → `CompatMode`；
- `com.hbmzz.compat.CursedBridge`：反射桥接门面；
- `com.hbmzz.compat.CompatMode`：模式枚举。

**私有实现**：无 `compat.impl` 子包——所有反射细节封装在 `CursedBridge` 内部。**全仓库零 `import com.hbm.*`**，诅咒版类名仅以字符串常量出现（由 `check_cursed_compat.sh` 校验）。

**可排除性要求**：standalone（未装诅咒版）下 `CursedCompat.detect()` 返回 `STANDALONE`，桥接调用返回安全默认值（`false` / `null`）；Forge 侧使用 `after:hbm` 软依赖，绝不使用强制依赖前缀（见 `MODMANIFEST.md`）。

---

### 4.4 com.hbmzz — 集成层

**职责**：装配与调度，不含具体玩法实现。

- 主 Mod 类生命周期入口调用 `StandaloneBootstrap.start()` / `tick()` / `shutdown()`；
- side 相关注册经 `HbmzzModules` 门面完成；
- 注册校验调用 `HbmzzModules.reactor(id)`，只面向 reactor 只读接口；
- 经 `CursedCompat` 判定是否启用 compat 桥接。

**允许依赖**：`core`、`reactor.api`、条件性的 `compat`（coexist）。

**禁止依赖**：诅咒版 API（即使在 coexist 也一律经 `CursedBridge` 反射，不得直接 import）；不得直接依赖 `com.hbmzz.reactor` 实现类。

**公开接口**：主 Mod 类（外部唯一稳定入口）、经 `core` 门面暴露的方法。

**私有实现**：side 代理与装配细节，不对外暴露 reactor 实现类型。

---

## 5. reactor 与 compat 的边界（重点）

两者**绝不互相依赖，也不经过 core.contract 之类的中间层**（实际代码中不存在 `core.contract`）。真实边界如下：

```
com.hbmzz.core ──编译期依赖──▶ com.hbmzz.reactor (ReactorRegistry/ReactorController)
       │                                │
       └──────编译期依赖──▶ reactor.api ◀──────实现──── com.hbmzz.reactor

com.hbmzz.compat ──运行期纯反射──▶ 诅咒版（类名仅字符串常量，零 import）
com.hbmzz.combat 与 reactor 之间无任何依赖边
```

约定：

1. **契约在 reactor.api**：对外契约是 `com.hbmzz.reactor.api.ReactorGateway` / `ReactorView`，只依赖 `java.*`；主模块只读访问收敛到此包，拿不到实现类型。
2. **单例入口，非 ServiceLoader**：`ReactorRegistry` 是 `final class` 单例，core 通过 `instance()` 获取、`install()` 绑定为全局网关（幂等）、`registerController()` / `controller(id)` 注册查询。不存在 `ServiceLoader` 发现逻辑。
3. **禁止反向调用**：compat 不调用 reactor；reactor 不感知 compat 的存在。compat 是独立反射门面，不与 reactor 发生依赖边。
4. **单边外部依赖**：诅咒版 API 只从 compat 这一侧经反射进入；全仓库不得出现 `import com.hbm.*`，reactor 侧出现即违规。

校验：`VALIDATION.md` 的 grep 与四路脚本会扫描全仓库的 `com.hbm.*` import、reactor import 白名单、ServiceLoader 痕迹。

---

## 6. standalone 与 coexist 数据流

### 6.1 standalone（未安装诅咒版）

```
启动：主 Mod 类
  ├─ CursedCompat.detect() → Loader.isModLoaded("hbm")==false → CompatMode.STANDALONE
  ├─ StandaloneBootstrap：ReactorRegistry.instance().install() → 绑定全局网关
  ├─ HbmzzModules：注册 reactor 控制器，构建模块门面
  └─ 运行：全部功能由自有 reactor 原生提供；桥接调用返回安全默认值
```

**要求**：全仓库零 `import com.hbm.*`；启动日志无 cursed 相关报错；功能完整可用。

### 6.2 coexist（已安装诅咒版）

```
启动：主 Mod 类
  ├─ CursedCompat.detect() → Loader.isModLoaded("hbm")==true → CompatMode.COEXIST
  ├─ StandaloneBootstrap + HbmzzModules：自有 reactor 照常装配（主线不变）
  ├─ CursedBridge：反射探测桥接类；可用则增强，不可用则优雅降级为 STANDALONE 行为
  └─ 运行：自有 reactor 为主线，诅咒版能力仅作为可选增强
```

**双向数据流**：

- 诅咒版 → 独立核心：`CursedBridge` 反射读取诅咒版能力，供增强逻辑使用；不可用即返回安全默认值；
- 独立核心 → 诅咒版：经 `CursedBridge` 反射写回，主线不依赖其成功。

**关键约束**：compat 是唯一反射转换点；主模块只见 `ReactorView` 只读快照，不见 reactor 实现类型，更不见 cursed 类型；禁止在任何源码中直接 `import com.hbm.*`。

---

## 7. 违规判定速查

| 违规场景 | 判定方式 | 处理 |
|---|---|---|
| 任意源码出现 `import com.hbm.*` | `grep`（见 VALIDATION 2.1） | 编译前硬失败 |
| compat 包名 / 类名出现在字符串常量之外 | `grep`（见 VALIDATION 2.2） | 编译前硬失败 |
| core 对 reactor 的引用落在白名单之外 | `check_self_dependency.sh` | 编译前硬失败 |
| reactor.api 依赖 `java.*` 之外 | `check_reactor.sh` | 编译前硬失败 |
| 出现 `ServiceLoader` reactor 发现逻辑 | `check_self_dependency.sh` | 编译前硬失败 |
| 出现分离源集路径（如 `src/reactor/java`） | `grep` docs/ 与构建脚本 | 文档 / 构建检查失败 |
| Forge 清单出现强制依赖诅咒版前缀 | `check_cursed_compat.sh` | 构建检查点失败 |

> 具体命令与检查脚本见 `VALIDATION.md`；合并检查点见 `EXECUTION_PLAN.md`。
