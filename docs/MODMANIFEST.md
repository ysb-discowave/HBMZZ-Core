# HBMZZ 依赖声明（mcmod.info 推荐片段）

## 推荐 mcmod.info

```json
{
  "modid": "hbmzz",
  "name": "HBMZZ",
  "version": "${version}",
  "mcversion": "1.12.2",
  "acceptedMinecraftVersions": "[1.12.2]",
  "dependencies": "after:hbm"
}
```

## Forge 主类注解（等价声明）

```java
@Mod(modid = "hbmzz", name = "HBMZZ", version = "${version}", dependencies = "after:hbm")
```

## 说明

- `modid` 固定为 `hbmzz`。
- `dependencies` 使用 `after:hbm`：**软依赖**，只声明加载顺序，不要求 hbm 必须存在。
  - 安装诅咒版：hbm 先加载、hbmzz 后加载，便于 `CursedCompat` 检测与 `CursedBridge` 反射桥接拿到稳定类状态。
  - 未安装诅咒版：Forge 忽略该顺序约束，hbmzz 正常加载并进入 STANDALONE 模式。
- **禁止**把 `dependencies` 写成强制依赖形式（即给 `after:hbm` 加上 `required-` 前缀）。
  那会让未安装诅咒版的用户无法启动，违反 standalone 目标。
