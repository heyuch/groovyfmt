#!/usr/bin/env bash
#
# 登记所有语料期望文件（.expected.groovy）的指纹到 corpus-review.txt。
#
# ⚠️ 这不是"把测试改绿"的工具，而是"我审查过了"的签署动作。
#    运行前请先 git diff，逐个确认新的期望形状是人能接受的、且 CodeNarc 无违规。
#
#   tools/lock-corpus.sh            # 重新登记
#   tools/lock-corpus.sh --list     # 只列出当前指纹，不改文件
set -euo pipefail
cd "$(dirname "$0")/../groovyfmt-core/src/test/resources/fixtures"
if [ "${1:-}" = "--list" ]; then
  find . -name '*.expected.groovy' | sort | while read -r f; do
    printf '%s %s\n' "${f#./}" "$(shasum -a 256 "$f" | cut -d' ' -f1)"
  done
  exit 0
fi
{
  echo "# 语料期望输出指纹锁。由 tools/lock-corpus.sh 生成。"
  echo "#"
  echo "# 这里登记的是每个 .expected.groovy 的 SHA-256。期望文件被新增/改动/删除，"
  echo "# 而本文件没跟着变，FixtureRunner 就会失败并列出具体是哪些 —— 目的是让"
  echo "# \"规范被实现悄悄改写\" 这件事无法悄悄发生（annotation 折行那次就是这么漏的）。"
  echo "#"
  echo "# 变更流程：git diff 逐个审查 -> 确认形状可接受且 CodeNarc 无违规 -> 重新运行本脚本。"
  find . -name '*.expected.groovy' | sort | while read -r f; do
    printf '%s %s\n' "${f#./}" "$(shasum -a 256 "$f" | cut -d' ' -f1)"
  done
} > corpus-review.txt
echo "  ✓ 已登记 $(grep -vc '^#' corpus-review.txt) 个期望文件到 corpus-review.txt"
