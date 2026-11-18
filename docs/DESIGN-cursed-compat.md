# HBMZZ × 诅咒版（hbm）共存设计

## 目标

同一个 Minecraft 1.12.2 + Forge 环境中，HBMZZ（modid `hbmzz`）与 HBM 诅咒版（modid `hbm`）共存：

- **standalone**：未安装诅咒版时，HBMZZ 完全独立运行，使用自有 reactor 实现，功能不缺失。
- **coexist**：安装诅咒版时，HBMZZ 通过**可选桥接**复用其能力作为增强，但**不硬依赖**诅咒版。

## 双模式

| 模式 | 判定 | 行为 |
| --- | --- | --- |
| `STANDALONE` | `Loader.isModLoaded("hbm") == false` | 走自有 reactor 实现；桥接调用全部返回安全默认值（`false` / `null`） |
| `COEXIST` | `Loader.isModLoaded("hbm") == true` | 反射探测桥接类；可用则增强，不可用则优雅降级为 STANDALONE 行为 |

模式由 `com.hbmzz.compat.CursedCompat` 统一判定，返回 `CompatMode.STANDALONE` / `CompatMode.COEXIST`。
判定过程捕获所有 `Throwable`：Forge 未就绪或任何异常一律按 STANDALONE 处理，保证「没装能跑、装了也能跑」。

## 硬约束

1. **禁止编译期引用诅咒版类型**：`com.hbmzz.compat` 包（以及整个 hbmzz 源码）不得 `import com.hbm.*`。
   诅咒版类名仅以**字符串常量**出现在反射代码中，不产生编译期依赖。
2. **检测入口唯一**：使用 Forge 的 `net.minecraftforge.fml.common.Loader.isModLoaded("hbm")`，
   位于 `CursedCompat.detect()`。
3. **反射门面**：`CursedBridge` 是唯一与诅咒版交互的入口。
   - 方法签名只暴露 `boolean` / `Object` / `String` 等 JDK 类型，绝不把诅咒版类型泄漏到 API 上；
   - 所有失败路径（hbm 未加载、类缺失、方法缺失、调用异常）返回 `boolean false` 或 `null` 占位对象；
   - 使用 `Class.forName(name, false, loader)` 且捕获 `Throwable`，不向调用方抛异常。
4. **软依赖声明**：Forge 依赖用 `after:hbm`（只约束加载顺序，不要求必须存在），
   **绝不使用强制依赖前缀**。详见 `docs/MODMANIFEST.md`。

## 加载顺序说明

`after:hbm` 表示：若 hbm 存在，则 hbm 先加载、hbmzz 后加载，以便 `CursedCompat` 检测与 `CursedBridge`
反射桥接拿到稳定的类状态；若 hbm 不存在，Forge 忽略该约束，hbmzz 正常加载并进入 STANDALONE 模式。

## 离线校验

`tools/checks/check_cursed_compat.sh` 对上述约束做静态检查，不依赖 gradle 构建：

- compat 包无 `import com.hbm.*`；
- 源码与清单中无强制依赖声明；
- 存在 `after:hbm` 软依赖声明；
- 存在 `Loader.isModLoaded("hbm")` 检测。
