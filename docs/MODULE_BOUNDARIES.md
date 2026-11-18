# HBMZZ-Core 模块边界规范

> 状态：约定稿。仓库当前为骨架，下列源码路径 / 包名为**约定**，代码落地后由对应负责人核对并回写本文件；检查脚本按这些约定实现。
> 适用分支：`feature/standards`（规范文档），最终并入 `hbmzz`。
> 关联文档：`BRANCHING.md`、`VALIDATION.md`、`EXECUTION_PLAN.md`。

## 1. 目标与适用范围

本规范锁定四件事：

1. 四个模块（`core` / `reactor` / `compat` / `hbmzz` 主模块）各自**只做什么**；
2. 模块之间**允许 / 禁止的依赖方向**；
3. 每个模块对外暴露的**公开接口**与不得外泄的**私有实现**；
4. `reactor` 与 `compat` 的边界，以及 **standalone / coexist** 两种部署形态下的数据流。

一切静态检查（grep、离线脚本）都以本文件的路径与包名为准。违反依赖方向的改动视为**编译前的硬失败**，不进入合并检查点。

---

## 2. 模块总览

```
                       ┌──────────────────────────────┐
                       │        hbmzz（主模块/入口）    │
                       │  主类、Proxy、注册装配、条件加载 │
                       └───┬──────────┬──────────┬──────┘
                           │ depends  │ depends  │ optional
                           ▼          ▼          ▼
                   ┌───────────┐ ┌──────────┐ ┌──────────────┐
                   │   core    │ │ reactor  │ │   compat      │
                   │ 公共基础库 │ │反应堆系统 │ │诅咒版兼容桥接 │
                   └─────┬─────┘ └────┬─────┘ └──────┬───────┘
                         ▲            │              │
                         └────────────┘              │ cursed API
                              depends on core         ▼（外部，仅 compat）
                                              ┌────────────────┐
                                              │ 诅咒版 API（外部）│
                                              └────────────────┘
```

源码根路径约定（相对仓库根）：

| 模块 | 源码路径 | Java 包根 | Maven/Gradle 子项目（约定） |
|---|---|---|---|
| core | `src/core/java/com/hbmzz/core/` | `com.hbmzz.core` | `:core` |
| reactor | `src/reactor/java/com/hbmzz/reactor/` | `com.hbmzz.reactor` | `:reactor` |
| compat | `src/compat/java/com/hbmzz/compat/` | `com.hbmzz.compat` | `:compat` |
| hbmzz | `src/main/java/com/hbmzz/` | `com.hbmzz` | `:hbmzz` |

> 资源路径：`src/<module>/resources/`，其中 `<module>` ∈ `{core,reactor,compat,main}`。

---

## 3. 依赖规则（硬约束）

### 3.1 依赖矩阵

行 = 依赖方，列 = 被依赖方。`✓` 允许，`✗` 禁止，`△` 仅条件允许。

| 依赖方 ↓ / 被依赖方 → | core | reactor | compat | hbmzz | 诅咒版 API（外部） |
|------------------------|:----:|:-------:|:------:|:-----:|:------------------:|
| **core**               |  —   |   ✗     |   ✗    |   ✗   |         ✗          |
| **reactor**            |  ✓   |   —     |   ✗    |   ✗   |         ✗          |
| **compat**             |  ✓   |   ✗     |   —    |   ✗   |      ✓（仅本模块）  |
| **hbmzz**              |  ✓   |   ✓     |   △    |   —   |         ✗          |

符号说明：

- `△`：仅 coexist 形态、且通过运行期条件加载引用 `compat`；standalone 产物中**不得包含** compat 类。
- `cursed API` 一列中除 `compat` 外全为 `✗`：这是 standalone 独立性的根本保证。

### 3.2 单向依赖链

合法依赖只能沿以下方向（箭头 = "可以依赖"）：

```
hbmzz ──▶ reactor ──▶ core
  └────▶ compat  ──▶ core
                     ▲
              compat ──▶ 诅咒版 API（外部）
```

由此推出的三条铁律：

- **铁律 1**：`core` 是最底层，不得反向依赖任何上层模块。
- **铁律 2**：`reactor` 与 `compat` **互不依赖**，二者只通过 `core` 中的抽象 / 契约间接协作。
- **铁律 3**：诅咒版 API 只能出现在 `compat` 模块的源码里；其它任何模块出现相关 import 即判定为违规。

---

## 4. 各模块详解

### 4.1 core — 公共基础库

**职责**：提供与具体玩法无关、与诅咒版无关的可复用基础设施。

- 通用数据结构、数学 / 方向 / 能量工具；
- NBT 读写辅助、注册辅助（方块 / 物品 / 实体 / 配方）；
- 配置与日志封装、公共常量；
- **跨模块契约接口**（供 reactor 与 compat 共同遵守，见第 5 节）。

**允许依赖**：仅 Minecraft / Forge（或 Fabric）基础 API，无本项目上层模块。

**禁止依赖**：`reactor`、`compat`、`hbmzz`、诅咒版 API。不得出现任何具体玩法实现。

**公开接口**：
- `com.hbmzz.core.api.*`：注册辅助、配置访问、NBT 帮助类；
- `com.hbmzz.core.contract.*`：跨模块契约（如 `IEnergyBridge`、`IRegistrySink`）；
- `com.hbmzz.core.util.*`：纯工具类。

**私有实现**：`com.hbmzz.core.internal.*`，仅供 core 内部使用，禁止被其它模块直接引用。

**合规要点**：所有源文件必须带 GPL 头（见 `VALIDATION.md` 3.2）。

---

### 4.2 reactor — 反应堆系统

**职责**：反应堆玩法本体，是 standalone 功能的唯一来源。

- 反应堆方块、TileEntity、multiblock 结构；
- GUI、容器、网络包（sync / 输入）；
- 反应堆渲染（TESR / 模型）；
- 通过 `core.contract.*` 暴露 / 消费抽象能力，**不关心底层是否接了诅咒版**。

**允许依赖**：`core`、Minecraft / Forge 基础 API。

**禁止依赖**：`compat`、诅咒版 API、`hbmzz`。reactor 源码中**不得出现**任何 cursed 包名 / 类名（静态 grep 强校验）。

**公开接口**：
- `com.hbmzz.reactor.api.IReactor`（反应堆只读视图）；
- `com.hbmzz.reactor.api.ReactorRegistry`（注册入口）；
- `com.hbmzz.reactor.api.ReactorNetwork`（网络包构造工厂）；
- `com.hbmzz.reactor.api.event.*`（供外部监听的扩展点）。

**私有实现**：
- `com.hbmzz.reactor.impl.*`（逻辑实现）；
- `com.hbmzz.reactor.client.*`（仅客户端，含全部渲染代码）；
- `com.hbmzz.reactor.network.internal.*`（包处理器实现）。

**边界说明**：reactor 定义"需要什么能力"，由 compat 在 coexist 下"提供实现"；reactor 侧禁止 `new` 任何 compat / cursed 类。

---

### 4.3 compat — 诅咒版兼容层

**职责**：**唯一**允许接触诅咒版 API 的模块，把诅咒版能力适配到 `core.contract.*`。

- 检测运行环境是否存在诅咒版；
- 将诅咒版的能量 / 物品 / 配方 / 方块接口映射到 core 契约；
- 把 reactor 产出写回诅咒版（双向桥接）；
- 在 standalone 下**整体缺席**（不编译进产物 / 不加载）。

**允许依赖**：`core`、诅咒版 API（仅本模块）、Minecraft / Forge 基础 API。

**禁止依赖**：`reactor`、`hbmzz`。compat 不得复制 reactor 的玩法逻辑，只做"翻译"。

**公开接口**：
- `com.hbmzz.compat.api.ICompatLayer`（适配层契约实现）；
- `com.hbmzz.compat.api.CompatManager`（供 hbmzz 查询 / 注册适配层）。

**私有实现**：
- `com.hbmzz.compat.impl.*`：**所有**诅咒版 import 与调用必须封装在此包内；
- `com.hbmzz.compat.internal.*`：环境探测、反射兜底。

**可排除性要求**：hbmzz 主模块必须能在构建期或运行期整体排除 compat，且 standalone 产物中不含 compat 类文件（由 `VALIDATION.md` 离线脚本校验）。

---

### 4.4 hbmzz — 主模块 / 入口

**职责**：装配与调度，不含具体玩法实现。

- Mod 主类与生命周期；
- Client / Server Proxy；
- 统一注册入口（汇总 core / reactor 的注册）；
- 条件加载 compat（coexist 才装载）。

**允许依赖**：`core`、`reactor`、条件性的 `compat`（coexist）。

**禁止依赖**：诅咒版 API（即使 coexist 也不得在主模块直接 import；必须经 compat 间接访问）。

**公开接口**：
- `com.hbmzz.HBMZZ`（主类，外部唯一稳定入口）。

**私有实现**：
- `com.hbmzz.proxy.*`；
- `com.hbmzz.internal.*`（装配逻辑、条件加载判定）。

---

## 5. reactor 与 compat 的边界（重点）

两者**绝不互相 import**。协作只通过 `core.contract.*` 这一层"窄腰"完成：

```
reactor ──uses──▶ core.contract.IEnergyBridge      ◀──implements── compat
reactor ──uses──▶ core.contract.IRegistrySink      ◀──implements── compat
```

约定：

1. **契约在 core**：任何需要跨 reactor / compat 的能力，接口定义放 `com.hbmzz.core.contract.*`，由 compat 实现，reactor 仅面向接口编程。
2. **发现机制**：hbmzz 在 coexist 启动时注册 compat 提供的适配层实现；reactor 通过 `core` 中的注册表查询可用实现，不做硬编码。
3. **禁止反向调用**：compat 不得调用 reactor 的内部类；reactor 不得感知 compat 的存在。
4. **单边依赖外部**：诅咒版 API 只从 compat 这一侧进入，reactor 侧 import 出现 cursed 即违规。

校验：`VALIDATION.md` 中的 `grep` 检查会扫描 `src/reactor/` 与 `src/core/`，命中 cursed 关键字即失败。

---

## 6. standalone 与 coexist 数据流

### 6.1 standalone（未安装诅咒版）

```
启动：hbmzz 主类
  ├─ 检测诅咒版：不存在 → 不加载 compat（compat 类不在 classpath 或被条件跳过）
  ├─ core 初始化：注册辅助 / 配置 / 契约注册表就绪
  ├─ reactor 初始化：注册方块、TE、网络包、渲染
  └─ 运行：全部功能由 reactor 原生提供
```

**要求**：产物中无 compat 类；启动日志无 "cursed" 相关报错；功能完整可用。

### 6.2 coexist（已安装诅咒版）

```
启动：hbmzz 主类
  ├─ 检测诅咒版：存在 → 加载 compat
  ├─ core 初始化：契约注册表就绪
  ├─ reactor 初始化：注册原生方块/TE/网络/渲染
  ├─ compat 初始化：注册 ICompatLayer 实现到 core 契约注册表
  └─ 运行：reactor 通过契约读写诅咒版能力（能量/物品/配方）
```

**双向数据流**：

- 诅咒版 → 独立核心：cursed 能量 / 物品 / 配方经 `compat.impl` 适配为 `core.contract` 对象，reactor 消费；
- 独立核心 → 诅咒版：reactor 输出经 `core.contract` 写回 `compat.impl`，由 compat 翻译为 cursed 调用。

**关键约束**：compat 是唯一转换点；reactor 永远只见 core 契约，不见 cursed 类型；禁止在 reactor / hbmzz / core 中直接调用 cursed API。

---

## 7. 违规判定速查

| 违规场景 | 判定方式 | 处理 |
|---|---|---|
| `src/core/` 出现 reactor/compat/hbmzz import | `grep`（见 VALIDATION 3.1） | 编译前硬失败 |
| `src/reactor/` 出现 cursed / compat import | `grep` | 编译前硬失败 |
| `src/hbmzz/` 或 `src/core/` 直接 import 诅咒版 API | `grep` | 编译前硬失败 |
| cursed import 出现在 compat 的 `api/`/`internal/` 之外 | `grep`（白名单校验） | 编译前硬失败 |
| compat 被 standalone 产物包含 | 离线结构脚本 | 构建检查点失败 |
| 契约接口未定义在 core 而散落在 reactor/compat | 离线结构脚本 | 合并检查点失败 |

> 具体命令与检查脚本见 `VALIDATION.md`；合并检查点见 `EXECUTION_PLAN.md`。
