# 自有反应堆依赖设计（self-dependency）

## 1. 目标

让 HBMZZ 主模块（core）依赖**自有反应堆体系（reactor-core）**，而不是诅咒版（cursed）的反应堆实现。
standalone 模式下，core 必须能独立注册并调用反应堆能力；共存模式下仍可与诅咒版同环境运行，但耦合点收敛到适配层。

## 2. 依赖方向（编译期）

```
com.hbmzz.core  ──(编译期依赖)──▶  com.hbmzz.reactor（自有实现）
                                      │
                                      ├── ReactorRegistry   （final class + 单例，注册入口）
                                      ├── ReactorController （abstract class，控制器/状态权威）
                                      └── com.hbmzz.reactor.api
                                            ├── ReactorGateway（接口，读取边界，EMPTY/bind/get）
                                            └── ReactorView   （接口，只读视图）
```

规则：

1. `com.hbmzz.core` 依赖**自有 reactor 体系**：注册侧使用 `ReactorRegistry` 单例与 `ReactorController` 具体基类，
   其余模块对反应堆的只读访问收敛到 `com.hbmzz.reactor.api.ReactorGateway` / `ReactorView`。
2. `com.hbmzz.core` 中**禁止**出现 `import com.hbm.*`（诅咒版包名），禁止直接引用诅咒版反应堆类。
3. reactor 侧是 **final class + api 接口** 的组合，不是可 `ServiceLoader` 发现的接口；
   core 通过 `ReactorRegistry.instance()` 获取单例，调用 `install()` 把它绑定为全局网关（幂等）；
   测试/共存适配可通过 `withReactorRegistry(...)` 显式注入覆盖。
4. 依赖箭头单向指向自有 reactor 体系；core 不 `new` reactor 实现，也不反向依赖 core 的调用方。
   诅咒版不进入依赖图，共存只在适配层桥接。

## 3. 核心组件

| 类 | 职责 |
| --- | --- |
| `com.hbmzz.core.ModuleRegistry` | 模块注册接口。定义模块 id 与初始化/ tick / 释放契约，供各功能模块实现并注册进来。 |
| `com.hbmzz.core.HbmzzModules` | 模块加载门面。持有模块注册表与 `ReactorRegistry`（单例），提供注册、查询、初始化、tick、释放入口；`createStandalone()` 取单例并 `install()`。 |
| `com.hbmzz.core.StandaloneBootstrap` | standalone 启动入口。装配 `ReactorRegistry`（单例或显式注入），构建 `HbmzzModules`，对外暴露启动、tick、关闭生命周期。 |

HbmzzModules 与 reactor 的交互面：

```java
ReactorRegistry registry = ReactorRegistry.instance(); // 单例，final class
registry.install();                                     // 绑定为 ReactorGateway，幂等
registry.registerController(controller);               // 注册控制器（控制器自带 id）
ReactorController c = registry.controller(id);          // 按 id 查询
```

## 4. 接入点

core 不直接触碰诅咒版类，以下为既有 HBMZZ 结构向 core 的接入约定（名字为约定，主代理合并时按实际类核对）：

- **ModContent**（内容注册类）：方块/物品/反应堆内容的注册统一走 `HbmzzModules` 门面；反应堆控制器经
  `HbmzzModules.registerReactor(ReactorController)` 注册（内部调用 `registerController`），不直接引用诅咒版类。
- **HBMZZ**（主 Mod 类）：生命周期入口调用 `StandaloneBootstrap.start()` / `tick()` / `shutdown()`；standalone 模式只走自有 reactor，共存模式由适配层桥接。
- **proxy**（客户端/服务端代理）：side 相关的注册通过 `HbmzzModules` 门面对外暴露的方法完成；core 不感知具体 proxy 实现，仅依赖其传入的注册上下文（接口/抽象）。
- **regcheck**（注册校验）：standalone 校验入口调用 `HbmzzModules.reactor(id)` 确认反应堆控制器已注册；校验逻辑只面向 reactor 的只读接口，不引用诅咒版类。

## 5. reactor-core 实际签名（已定稿，core 依此调用）

reactor-core 已落地，以下为定稿签名（core 侧按此调用，**不再**假设为可 ServiceLoader 发现的接口）：

```java
package com.hbmzz.reactor;

public final class ReactorRegistry implements ReactorGateway {
    public static ReactorRegistry instance();                       // 单例
    public void install();                                          // 绑定全局网关，幂等
    public synchronized void registerController(ReactorController c);
    public synchronized ReactorController controller(String id);
    public synchronized void registerTier(ReactorTier t);
    public synchronized ReactorView get(String id);
    public synchronized java.util.List<ReactorView> snapshot();
}

public abstract class ReactorController extends TileEntity implements ReactorView {
    public final void bindId(String id);   // 绑定稳定 id
    public final String getId();
    public final void register();          // 自注册（内部 registerController(this)），幂等
    public final void tick();
}

package com.hbmzz.reactor.api;

public interface ReactorGateway {
    ReactorView get(String id);
    java.util.List<ReactorView> snapshot();
    ReactorGateway EMPTY = ...;
    static void bind(ReactorGateway gateway);
    static ReactorGateway get();
}
```

说明：控制器自带 id 并支持 `register()` 自注册；core 的 `registerReactor(ReactorController)` 转调
`registerController(controller)`，按 id 查询走 `controller(id)`。无 `META-INF/services` 机制。

## 6. 离线检查

`tools/checks/check_self_dependency.sh` 为纯静态检查（不运行 gradle）：

1. 断言 `src/main/java/com/hbmzz/core` 下不出现 `import com.hbm.*`；
2. 断言 core 中不出现诅咒版包名（如 `com.hbm.*` 任何形式），确保未间接依赖诅咒版；
3. 断言 core 对 reactor 的引用只落在白名单类型（`ReactorRegistry` / `ReactorController`），
   不出现旧签名 `register(String, ReactorController)` 或 `ServiceLoader` 发现逻辑；
   具体实现类是否误 import 由主代理合并时复核；
4. 断言核心注册入口（`HbmzzModules` / `ModuleRegistry` / `StandaloneBootstrap`）与 standalone 入口存在。
