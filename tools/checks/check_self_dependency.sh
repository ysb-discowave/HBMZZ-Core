#!/usr/bin/env bash
# HBMZZ-Core - 离线静态检查：core -> reactor 接口，禁止诅咒版依赖。
# 不运行 gradle，不做编译，仅做文本/结构断言。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CORE_DIR="${REPO_ROOT}/src/main/java/com/hbmzz/core"

fail() { echo "[FAIL] $*" >&2; exit 1; }
pass() { echo "[PASS] $*"; }

[ -d "$CORE_DIR" ] || fail "core 目录不存在: $CORE_DIR"

CORE_JAVA="$(find "$CORE_DIR" -type f -name '*.java')"
[ -n "$CORE_JAVA" ] || fail "core 目录下没有 .java 文件"

# ---------------------------------------------------------------------------
# 1. core 不得 import 诅咒版包 com.hbm.*
# ---------------------------------------------------------------------------
if grep -RIn --include='*.java' -E '^[[:space:]]*import[[:space:]]+com\.hbm\.' "$CORE_DIR"; then
  fail "core 中出现诅咒版 import: com.hbm.*"
fi
pass "core 未 import com.hbm.*"

# ---------------------------------------------------------------------------
# 2. core 中不得出现任何诅咒版包名（含非 import 形式的间接引用）
# ---------------------------------------------------------------------------
if grep -RIn --include='*.java' -E 'com\.hbm\.' "$CORE_DIR"; then
  fail "core 中出现诅咒版包名: com.hbm.*"
fi
pass "core 未出现诅咒版包名"

# ---------------------------------------------------------------------------
# 3. core 对 reactor 的引用只落在白名单类型（ReactorRegistry / ReactorController）。
#    reactor 是 final class + api 接口的组合，不是服务可发现的接口。
#    具体是否误 import 其他实现类，主代理合并时复核。
# ---------------------------------------------------------------------------
REACTOR_IMPORTS="$(grep -RIn --include='*.java' -E '^[[:space:]]*import[[:space:]]+com\.hbmzz\.reactor\.' "$CORE_DIR" || true)"
if [ -n "$REACTOR_IMPORTS" ]; then
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    cls="$(printf '%s\n' "$line" | sed -E 's/.*com\.hbmzz\.reactor\.([A-Za-z0-9_]+).*/\1/')"
    case "$cls" in
      ReactorRegistry|ReactorController) ;;
      *) fail "core 直接 import 了白名单外的 reactor 类型: ${cls} （${line}）—— 主代理合并时请复核" ;;
    esac
  done <<< "$REACTOR_IMPORTS"
fi
pass "core 对 reactor 的引用仅落在 ReactorRegistry/ReactorController 白名单"

# 不得残留旧 API 假设：ServiceLoader 发现 / register(String, ReactorController) 双参数
if grep -RIn --include='*.java' -E 'ServiceLoader|registerReactor[[:space:]]*\([[:space:]]*String|reactorRegistry\.register\(' "$CORE_DIR"; then
  fail "core 残留旧 reactor API 假设（ServiceLoader 发现或双参数 register）"
fi
pass "core 未残留旧 reactor API 假设"

# ---------------------------------------------------------------------------
# 4. 注册/启动入口存在
# ---------------------------------------------------------------------------
for f in HbmzzModules.java ModuleRegistry.java StandaloneBootstrap.java; do
  [ -f "${CORE_DIR}/${f}" ] || fail "缺少入口文件: ${f}"
done
grep -q 'interface ModuleRegistry' "${CORE_DIR}/ModuleRegistry.java" || fail "ModuleRegistry 接口声明缺失"
grep -q 'class HbmzzModules' "${CORE_DIR}/HbmzzModules.java" || fail "HbmzzModules 门面类缺失"
grep -q 'implements ModuleRegistry' "${CORE_DIR}/HbmzzModules.java" || fail "HbmzzModules 未实现 ModuleRegistry 注册接口"
grep -q 'class StandaloneBootstrap' "${CORE_DIR}/StandaloneBootstrap.java" || fail "StandaloneBootstrap 缺失"
grep -q 'StandaloneBootstrap start' "${CORE_DIR}/StandaloneBootstrap.java" || fail "StandaloneBootstrap 启动入口缺失"
pass "模块注册门面与 standalone 启动入口存在"

# 关键调用签名就位（防止回退到旧假设）
grep -q 'ReactorRegistry.instance()' "${CORE_DIR}/HbmzzModules.java" || fail "createStandalone 未使用 ReactorRegistry.instance()"
grep -q 'install()' "${CORE_DIR}/HbmzzModules.java" || fail "createStandalone 未调用 install()"
grep -q 'registerController(controller)' "${CORE_DIR}/HbmzzModules.java" || fail "registerReactor 未转调 registerController"
grep -q 'reactorRegistry.controller(id)' "${CORE_DIR}/HbmzzModules.java" || fail "按 id 查询未使用 reactorRegistry.controller(id)"
pass "core 调用真实 reactor 签名的关键入口就位"

echo "[OK] self-dependency 离线检查全部通过"
