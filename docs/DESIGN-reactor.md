# HBMZZ 自有反应堆体系（reactor-core）设计

版本：1.0  
坐标：`com.hbmzz.reactor`（实现）、`com.hbmzz.reactor.api`（对外接口）  
目标分支：`feature/reactor-core`

## 1. 目标与边界

本模块是 HBMZZ 在 standalone 模式下的**独立反应堆核心**。它与“诅咒版”的关系只有一条：

> **没有任何编译期或运行期依赖。**

具体不变量（由 `tools/checks/check_reactor.sh` 离线强制）：

- 不 `import com.hbm.*`，源码中也不出现 `com.hbm.` 这个包前缀（`com.hbmzz.` 不算）。
- 不引用诅咒版类名、资源名、NBT 键、事件或注册名。
- 除 `java.*` 与 `net.minecraft.*` 外不 import 任何第三方 mod / 库（网络缓冲区用自己的 `ReactorBuffer` 抽象，不引 netty）。
- 每个 `.java` 文件带 GPL-3.0 头。

借鉴的只是 RBMK 的**工程思想**（纯函数状态机、逻辑簇识别、读数驱动），不是它的类或常量；本模块的类名、数值、协议全部独立。

## 2. 包结构与分层

```
com.hbmzz.reactor.api       对外接口层（主模块唯一可见的东西）
    ReactorView             只读快照：id / tierId / phase / heat / integrity / running
    ReactorGateway          服务定位接口 + 静态 bind/get（含 EMPTY 空实现）
com.hbmzz.reactor           实现层（主模块不得依赖本包）
    ReactorTier             等级/规格（enum，纯数据）
    ReactorState            状态机（纯函数，无世界/TE/客户端）
    ReactorRegistry         自有注册表（等级 + 控制器），实现 ReactorGateway
    ReactorController       方块实体基类，实现 ReactorView
    ReactorNetworkMessages  网络包 + 注册配对 + 自有缓冲抽象
```

依赖方向严格单向：

```
主模块 (com.hbmzz.*)  ──依赖──▶  com.hbmzz.reactor.api
com.hbmzz.reactor     ──依赖──▶  com.hbmzz.reactor.api
```

`api` 包只依赖 `java.*`。主模块**只能**通过 `ReactorGateway` 拿到 `ReactorView` 只读快照，拿不到 `ReactorController`、`ReactorState`、`ReactorTier` 或任何具体实现类型。反向 import（`api` 或主模块 import `com.hbmzz.reactor.*`）属于设计违规。

## 3. 状态机（`ReactorState`）

状态机是**纯函数**：输入仅 ticks 与读数，无 world、无 tile entity、无 client。这样离线检查脚本和单元测试可以不启动游戏就跑上千次。

```
   ┌──────── fuelValid && coolingOK && heat < trip
   ▼
OFFLINE ──▶ STARTING ──▶ RUNNING ──▶ SCRAM ──▶ SPENT
                │            │          │
                └────────────┴──────────┘
                         integrity <= 0 或 heat >= CRITICAL
                                  ▼
                              MELTDOWN （终态）
```

| 状态 | 含义 | 进入条件 | 离开条件 |
|---|---|---|---|
| `OFFLINE` | 停机，未装料/无冷却 | 初始；或 `STARTING` 时失去燃料/冷却 | 燃料与冷却均就绪且未过热 → `STARTING` |
| `STARTING` | 提升功率，斜坡计时 | `OFFLINE` 就绪 | 满 `RAMP_TICKS` → `RUNNING`；失去条件→`OFFLINE`；过热→`SCRAM` |
| `RUNNING` | 稳态运行，对外输出 | `STARTING` 完成 | 过热或冷却失效 → `SCRAM` |
| `SCRAM` | 紧急停堆，余热排出 | `RUNNING` 触发 | 满 `COOLDOWN_TICKS` → `SPENT` |
| `MELTDOWN` | 堆芯熔毁，终态 | integrity ≤ 0 或 heat ≥ `CRITICAL_HEAT` | 无 |
| `SPENT` | 安全停运，终态 | `SCRAM` 冷却完成 | 无 |

不变量：

- 相位只能**向前**移动；`MELTDOWN` / `SPENT` 无出口。一次事故不能在冷却后又从头倒计时。
- `ticksInPhase` 由控制器维护，相位变化时归零；状态机只用 `>=` 判断，不依赖恰好相等。
- 热量斜坡 `rampHeat()` 用平方根曲线：开头是抖动，结尾是重击，避免线性“机械感”。
- 输出系数 `outputFactor()` 仅 `RUNNING` 为 1，`STARTING` 按比例爬升，其余为 0。

## 4. 方块 / TE / 网络 / 渲染分层

- **方块层**：只负责形状、放置、红石/交互输入。方块把请求转成对 `ReactorController` 的调用，不持有状态。
- **TE 层（`ReactorController`）**：唯一的状态权威。持有 `tier`、`phase`、`ticksInPhase`、`heat`、`integrity`，在每个 tick 调用 `ReactorState.advance()`，并在相位变化时回调 `onPhaseChanged()`。`readFromNBT` / `writeToNBT` 负责持久化，`invalidate()` 时从 `ReactorRegistry` 反注册。
- **网络层（`ReactorNetworkMessages`）**：只传**快照与事件**，不传逻辑。每个包实现 `ReactorPacket.id()/encode()/decode()`，缓冲区用自有 `ReactorBuffer`。包命名 `ReactorPacketXxx` 与 `register(ReactorPacketXxx.class, id)` 一一配对，由检查脚本校验；id 唯一、协议版本 `PROTOCOL_VERSION` 变更需同步双方。
- **渲染层**：本模块**不含任何渲染实现**（不 import `net.minecraft.client.*`）。渲染只能消费 `api.ReactorView` 的只读快照；需要动画 Extension 时通过第 6 节扩展点注入，保证 standalone 下无客户端类被服务端加载。

分层收益：逻辑可在无游戏环境验证；网络不可信输入只能改快照，改不了相位推进。

## 5. 与主模块的接口（只允许暴露接口）

对外仅暴露 `com.hbmzz.reactor.api`，且接口只返回基本类型：

- `ReactorView`：只读快照（`getId/getTierId/getPhase/getHeat/getIntegrity/isRunning`）。
- `ReactorGateway`：查询入口 `get(id)` / `snapshot()`；静态 `bind(gateway)` / `get()` 提供服务定位。
  - 空实现 `EMPTY` 保证主模块在核心未加载时安全降级（不 NPE）。

接线方式：实现层 `ReactorRegistry` 实现 `ReactorGateway`，在初始化时 `ReactorGateway.bind(registry)`。主模块代码形如：

```java
ReactorView v = ReactorGateway.get().get(controllerId);
if (v != null && v.isRunning()) { /* 只读使用 */ }
```

主模块永远拿不到具体实现类型，因此无法反向依赖；实现层替换、共存模式桥接都不需要改主模块。

## 6. 扩展点

1. **等级扩展**：`ReactorTier` enum 内置 4 档；需更多规格时通过 `ReactorRegistry.registerTier()` 注册（保留 id 唯一约束），不改动状态机。
2. **控制器扩展**：继承 `ReactorController` 覆写 `onPhaseChanged()` / `computeHeatDelta()` 接入不同燃料/冷却模型；注册进 `ReactorRegistry` 后自动对 `ReactorGateway` 可见。
3. **网络扩展**：新增包必须命名 `ReactorPacketXxx` 并在静态块 `register(..., id)` 配对，否则离线检查失败。
4. **渲染/特效扩展**：不写进核心；主模块监听 `ReactorView` 快照变化自行渲染。核心不持有 `net.minecraft.client` 引用。
5. **协议版本**：`ReactorNetworkMessages.PROTOCOL_VERSION` 是握手契约；不兼容变更必须升版本并同时更新检查脚本中的期望值。

## 7. 验证

```
bash tools/checks/check_reactor.sh
```

检查项：无 `com.hbm.` 依赖、无诅咒版类名、import 白名单、GPL 头、网络包命名与注册配对、文件清单完整。脚本无外部依赖，`grep`/`sed` 即可运行。
