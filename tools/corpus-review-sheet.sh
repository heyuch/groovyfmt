#!/usr/bin/env bash
#
# 生成一份【可读的语料复核清单】docs/corpus-review.md。
#
# 目的：让你能一次过完所有语料目标，而不是翻 git diff。
# 每一条给出：用例名 / 所在分组 / 输入 / 期望输出（并排给出）。
#
# 复核时请逐条判断两件事：
#   1. 期望输出这个【形状】是人能接受的吗（尤其折行断在哪里）？
#   2. 它过 CodeNarc 吗？（tools/check-corpus-codenarc.sh）
# 两道都过了，才用 tools/lock-corpus.sh 登记（= 签署"我审过了"）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIX="$ROOT/groovyfmt-core/src/test/resources/fixtures"
OUT="$ROOT/docs/corpus-review.md"

python3 - "$FIX" "$OUT" <<'PY'
import os
import sys

fix, out_path = sys.argv[1], sys.argv[2]
cases = []
for dirpath, _dirnames, filenames in os.walk(fix):
    for name in sorted(filenames):
        if name.endswith('.expected.groovy') or not name.endswith('.groovy'):
            continue
        path = os.path.join(dirpath, name)
        rel = os.path.relpath(path, fix)
        group = os.path.dirname(rel) or '(root)'
        expected = path[:-len('.groovy')] + '.expected.groovy'
        cases.append((group, rel, path, expected if os.path.exists(expected) else None))
cases.sort()

lines = []
lines.append('# 语料复核清单（自动生成，勿手改）')
lines.append('')
lines.append('生成：`tools/corpus-review-sheet.sh`　CodeNarc 复核：`tools/check-corpus-codenarc.sh`　冻结登记：`tools/lock-corpus.sh`')
lines.append('')
lines.append(f'共 **{len(cases)}** 个用例，其中 **{sum(1 for c in cases if c[3])}** 个带期望输出。')
lines.append('')
lines.append('复核每一条时问两件事：**形状可接受吗**（尤其断行断在哪）、**过 CodeNarc 吗**。')
lines.append('')
lines.append('| # | 分组 | 用例 | 有期望 |')
lines.append('| --- | --- | --- | --- |')
for i, (group, rel, _p, exp) in enumerate(cases, 1):
    lines.append(f'| {i} | `{group}` | `{rel}` | {"✓" if exp else "—（要求保持不变）"} |')
lines.append('')

for i, (group, rel, path, exp) in enumerate(cases, 1):
    lines.append(f'## {i}. `{rel}`')
    lines.append('')
    lines.append(f'分组：`{group}`　　期望文件：{"有" if exp else "无 —— 这条要求【原样不变】"}')
    lines.append('')
    lines.append('**输入**')
    lines.append('')
    lines.append('```groovy')
    lines.append(open(path, encoding='utf-8').read().rstrip('\n'))
    lines.append('```')
    lines.append('')
    if exp:
        lines.append('**期望输出**')
        lines.append('')
        lines.append('```groovy')
        lines.append(open(exp, encoding='utf-8').read().rstrip('\n'))
        lines.append('```')
        lines.append('')
    else:
        lines.append('**期望输出**：与输入完全相同。')
        lines.append('')

os.makedirs(os.path.dirname(out_path), exist_ok=True)
open(out_path, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print(f'  ✓ 已写出 {out_path}（{len(cases)} 个用例）')
PY
