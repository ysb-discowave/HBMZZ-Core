#!/usr/bin/env bash
# HBMZZ × 诅咒版（hbm）共存约束的离线静态检查。
# 不运行 gradle 构建，只用 grep 校验源码与清单。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

COMPAT_DIR="src/main/java/com/hbmzz/compat"

fail() { echo "[FAIL] $*" >&2; exit 1; }
pass() { echo "[PASS] $*"; }

echo "== HBMZZ cursed-compat 离线检查 =="

# 1. compat 包不得编译期 import com.hbm.*
if grep -R -n --include='*.java' -E '^[[:space:]]*import[[:space:]]+com\.hbm\.' "$COMPAT_DIR" 2>/dev/null; then
    fail "compat 包禁止 import com.hbm.*"
fi
pass "compat 包未 import com.hbm.*"

# 2. 源码与清单不得声明强制依赖（文档中的说明性文字不算声明）
#    扫描范围：Java 源码与 mod 清单文件，排除 .git 与说明文档。
if grep -R -n --exclude-dir=.git \
        --include='*.java' --include='mcmod.info' --include='*.json' \
        --include='*.toml' --include='*.gradle' \
        -F 'required-after:hbm' . 2>/dev/null; then
    fail "禁止声明 required-after:hbm（强制依赖）"
fi
pass "未声明 required-after:hbm"

# 3. 必须存在 after:hbm 软依赖声明。
#    用前缀排除 required-after:hbm 中的子串，确保检查的是真正的软依赖。
if ! grep -R -n --exclude-dir=.git \
        --include='*.md' --include='mcmod.info' --include='*.json' \
        -E '(^|[^[:alnum:]_-])after:hbm' . 2>/dev/null | grep -q .; then
    fail "未找到 after:hbm 软依赖声明"
fi
pass "存在 after:hbm 软依赖声明"

# 4. 必须存在 Loader.isModLoaded("hbm") 检测入口
if ! grep -R -n --include='*.java' -F 'Loader.isModLoaded("hbm")' src 2>/dev/null | grep -q .; then
    fail '未找到 Loader.isModLoaded("hbm") 检测'
fi
pass '存在 Loader.isModLoaded("hbm") 检测'

echo "== 全部检查通过 =="
