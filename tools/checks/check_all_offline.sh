#!/usr/bin/env bash
# check_all_offline.sh
# HBMZZ-Core 离线检查汇总器（由 standards 路提供）
#
# 行为：
#   - 探测并执行 reactor-core / self-dependency / cursed-compat 三路的离线检查脚本；
#   - 脚本不存在时报 SKIP（而非失败），保证本脚本可独立运行；
#   - 退出码：全部 PASS/SKIP -> 0；任一 FAIL -> 1。
# 约定：子脚本退出码 0=通过，非 0=失败；须可独立运行（不依赖 gradle/网络）。
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

# 各路候选脚本名（相对本脚本所在目录），按优先级取第一个存在的
REACTOR_CANDIDATES=(check_reactor_offline.sh check_reactor.sh)
DEPS_CANDIDATES=(check_dependency_offline.sh check_self_dependency_offline.sh check_self_dependency.sh)
COMPAT_CANDIDATES=(check_compat_offline.sh check_cursed_compat.sh check_cursed_compat_offline.sh check_compat.sh)

declare -a RESULTS=()   # 元素格式: "STATUS|lane|detail"
OVERALL=0

pass() { RESULTS+=("PASS|$1|$2"); printf '[PASS] %-18s %s\n' "$1" "$2"; }
fail() { RESULTS+=("FAIL|$1|$2"); printf '[FAIL] %-18s %s\n' "$1" "$2"; OVERALL=1; }
skip() { RESULTS+=("SKIP|$1|$2"); printf '[SKIP] %-18s %s\n' "$1" "$2"; }

run_lane() {
  local lane="$1"; shift
  local -a candidates=("$@")
  local found="" c rc
  for c in "${candidates[@]}"; do
    if [ -f "$SCRIPT_DIR/$c" ]; then found="$c"; break; fi
  done
  if [ -z "$found" ]; then
    skip "$lane" "未找到脚本（候选: ${candidates[*]}）"
    return 0
  fi
  printf -- '---- %s : %s ----\n' "$lane" "$found"
  bash "$SCRIPT_DIR/$found"
  rc=$?
  if [ "$rc" -eq 0 ]; then
    pass "$lane" "$found"
  else
    fail "$lane" "$found 退出码=$rc"
  fi
}

echo "======================================================"
echo " HBMZZ-Core 离线结构检查汇总 (check_all_offline.sh)"
echo " repo   : $REPO_ROOT"
echo " checks : $SCRIPT_DIR"
echo "======================================================"
echo

# 0) 本路自检：规范文档是否齐全（使脚本独立运行时亦有产出）
echo "== standards 自身交付物检查 =="
missing=""
for doc in MODULE_BOUNDARIES.md BRANCHING.md VALIDATION.md EXECUTION_PLAN.md; do
  [ -f "$REPO_ROOT/docs/$doc" ] || missing="$missing $doc"
done
if [ -n "$missing" ]; then
  fail "standards-docs" "缺失文档:$missing"
else
  pass "standards-docs" "4 份规范文档齐全"
fi
echo

# 1) 三路离线脚本（缺失报 SKIP，不失败）
echo "== 三路离线检查 =="
run_lane "reactor-core" "${REACTOR_CANDIDATES[@]}"
echo
run_lane "self-dependency" "${DEPS_CANDIDATES[@]}"
echo
run_lane "cursed-compat" "${COMPAT_CANDIDATES[@]}"
echo

# 2) 汇总
echo "==================== 汇总 ===================="
for r in "${RESULTS[@]}"; do
  printf '  %s\n' "$r"
done
echo "------------------------------------------------------"
if [ "$OVERALL" -eq 0 ]; then
  echo "RESULT: PASS（SKIP 项须在合并检查点逐条确认原因）"
else
  echo "RESULT: FAIL（存在未通过的离线检查，阻断合并）"
fi
exit "$OVERALL"
