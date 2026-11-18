# 自有反应堆依赖设计（self-dependency）

## 1. 目标

让 HBMZZ 主模块（core）依赖**自有反应堆体系（reactor-core）**，而不是诅咒版（cursed）的反应堆实现。
standalone 模式下，core 必须能独立注册并调用反应堆能力；共存模式下仍可与诅咒版同环境运行，但耦合点收敛到适配层。

## 2. 依赖方向（编译期）

```
com.hbmzz.core  ──(编译期依赖)──▶  com.hbmzz.reactor 的接口
                                      │
                                      ├── ReactorRegistry   （接口）
                                      └── ReactorController （接口）
                                             ▲
                                             │ 实现（可插拔）
                                      reactor-core 提供具体实现
                                      （standalone 默认实现）
```

规则：

1. `com.hbmzz.core` **只能**依赖 `com.hbmzz.reactor` 中的**接口**，不得依赖任何具体实现类。
2. `com.hbmzz.core` 中**禁止**出现 `import com.hbm.*`（诅咒版包名），禁止直接引用诅咒版反应堆类。
3. reactor 的**实现可插拔**：core 不 `new` 具体实现，只通过接口 + 注册门面调用；实现由 standalone 启动时经 `ServiceLoader` 发现或显式注入。
4. 依赖箭头单向指向接口，具体实现在编译期对 core 不可见；替换实现不需要改动 core。

## 3. 核心组件

| 类 | 职责 |
| --- | --- |
| `com.hbmzz.core.ModuleRegistry` | 模块注册接口。定义模块 id 与初始化/ tick / 释放契约，供各功能模块实现并注册进来。 |
| `com.hbmzz.core.HbmzzModules` | 模块加载门面。持有模块注册表与 `ReactorRegistry`（仅接口类型），提供注册、查询、初始化、tick、释放入口。 |
| `com.hbmzz.core.StandaloneBootstrap` | standalone 启动入口。负责装配 `ReactorRegistry`（接口发现/注入），构建 `HbmzzModules`，对外暴露启动、tick、关闭生命周期。 |

## 4. 接入点

core 不直接触碰诅咒版类，以下为既有 HBMZZ 结构向 core 的接入约定（名字为约定，主代理合并时按实际类核对）：

- **ModContent**（内容注册类）：方块/物品/反应堆内容的注册统一走 `HbmzzModules` 门面；反应堆控制器以 `ReactorController` 接口注册进 `ReactorRegistry`，不直接引用具体反应堆类。
- **HBMZZ**（主 Mod 类）：生命周期入口调用 `StandaloneBootstrap.start()` / `tick()` / `shutdown()`；standalone 模式只走自有 reactor，共存模式由适配层桥接。
- **proxy**（客户端/服务端代理）：side 相关的注册通过 `HbmzzModules` 门面对外暴露的方法完成；core 不感知具体 proxy 实现，仅依赖其传入的注册上下文（接口/抽象）。
- **regcheck**（注册校验）：standalone 校验入口调用 `HbmzzModules` 的查询接口确认反应堆控制器已注册；校验逻辑只面向接口，不引用诅咒版类。

## 5. 对 reactor 接口的假设（待主代理合并核对）

编写 core 时 reactor 接口尚未落地，按 team-lead 约定假设如下签名：

```java
package com.hbmzz.reactor;

public interface ReactorRegistry {
    void register(String id, ReactorController controller);
    ReactorController get(String id);
    boolean has(String id);
}

public interface ReactorController {
    String id();
    void initialize();
    void tick();
    void dispose();
}
```

发现机制：standalone 通过 `java.util.ServiceLoader.load(ReactorRegistry.class)` 发现实现（实现方在 `META-INF/services/com.hbmzz.reactor.ReactorRegistry` 注册），同时支持 `withReactorRegistry(...)` 显式注入以覆盖发现结果。

若 reactor-core 实际接口签名/类名不同，core 调用代码需同步调整；本设计文档与离线检查脚本需一并复核。

## 6. 离线检查

`tools/checks/check_self_dependency.sh` 为纯静态检查（不运行 gradle）：

1. 断言 `src/main/java/com/hbmzz/core` 下不出现 `import com.hbm.*`；
2. 断言 core 中不出现诅咒版包名（如 `com.hbm.*` 任何形式），确保未间接依赖诅咒版；
3. 断言 core 对 reactor 的引用只通过接口/门面（具体实现类是否 import 由主代理合并时复核，脚本先做包名级断言）；
4. 断言核心注册入口（`HbmzzModules` / `ModuleRegistry` / `StandaloneBootstrap`）与 standalone 入口存在。
