#!/usr/bin/env bash
# compile_standalone.sh — standalone 交叉编译证明
#
# 目的：证明 core + reactor + compat 三套源码在"没有诅咒版类"的前提下
#       能一起通过 javac 编译。用最小 net.minecraft / forge 桩替代真实 MC 类，
#       桩只覆盖本项目实际 import 到的三个类型（NBTTagCompound / TileEntity / Loader）。
#
# 退出码：0 = 三套模块交叉编译通过；非 0 = 编译失败。
# 不依赖 gradle、网络、真实 Minecraft 环境。
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
STUB_ROOT="${TMPDIR:-/tmp}/hbmzz-compile-stub-$$"

cleanup() { rm -rf "$STUB_ROOT"; }
trap cleanup EXIT

mkdir -p "$STUB_ROOT/src/net/minecraft/nbt" \
         "$STUB_ROOT/src/net/minecraft/tileentity" \
         "$STUB_ROOT/src/net/minecraftforge/fml/common" \
         "$STUB_ROOT/out"

cat > "$STUB_ROOT/src/net/minecraft/nbt/NBTTagCompound.java" <<'JAVA'
package net.minecraft.nbt;
public class NBTTagCompound {
    public void setString(String k, String v) {}
    public String getString(String k) { return ""; }
    public void setDouble(String k, double v) {}
    public double getDouble(String k) { return 0.0; }
    public void setInteger(String k, int v) {}
    public int getInteger(String k) { return 0; }
    public void setBoolean(String k, boolean v) {}
    public boolean getBoolean(String k) { return false; }
    public boolean hasKey(String k) { return false; }
}
JAVA

cat > "$STUB_ROOT/src/net/minecraft/tileentity/TileEntity.java" <<'JAVA'
package net.minecraft.tileentity;
import net.minecraft.nbt.NBTTagCompound;
public class TileEntity {
    public void readFromNBT(NBTTagCompound nbt) {}
    public void writeToNBT(NBTTagCompound nbt) {}
    public void updateEntity() {}
    public void invalidate() {}
    public void markDirty() {}
    public int xCoord, yCoord, zCoord;
}
JAVA

cat > "$STUB_ROOT/src/net/minecraftforge/fml/common/Loader.java" <<'JAVA'
package net.minecraftforge.fml.common;
public class Loader {
    public static boolean isModLoaded(String id) { return false; }
}
JAVA

# 选 JDK8 javac：项目工具链优先，其次 JAVA_HOME，最后 PATH
JAVAC=""
for c in "D:/DSH/toolchain/jdk8/jdk8u512-b09/bin/javac" \
         "${JAVA_HOME:-}/bin/javac" \
         "javac"; do
  if command -v "$c" >/dev/null 2>&1 || [ -x "$c" ]; then JAVAC="$c"; break; fi
done
if [ -z "$JAVAC" ]; then
  echo "[FAIL] 找不到 javac，请配置 JDK8 或 JAVA_HOME"
  exit 1
fi

# 收集桩 + 项目全部源码
find "$STUB_ROOT/src" "$REPO_ROOT/src/main/java" -name '*.java' > "$STUB_ROOT/sources.txt"
SRC_COUNT=$(wc -l < "$STUB_ROOT/sources.txt")

echo "== standalone 交叉编译 =="
echo " javac   : $JAVAC"
echo " sources : $SRC_COUNT java files"

# -encoding UTF-8 必需：JDK8 在中文 Windows 默认 GBK 读源码会报"不可映射字符"
if ! "$JAVAC" -encoding UTF-8 -nowarn -d "$STUB_ROOT/out" @"$STUB_ROOT/sources.txt"; then
  echo "[FAIL] 交叉编译失败"
  exit 1
fi

CLASS_COUNT=$(find "$STUB_ROOT/out/com/hbmzz" -name '*.class' | wc -l)
echo "[PASS] 交叉编译通过：生成 $CLASS_COUNT 个 com.hbmzz class，全程无诅咒版类型"
exit 0
