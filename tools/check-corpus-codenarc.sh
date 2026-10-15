#!/usr/bin/env bash
#
# 用 CodeNarc（只含 formatting 规则）复核语料的【期望输出】本身。
#
# 为什么：.expected.groovy 是我们给自己定的目标，所以目标本身也必须过 CodeNarc。
# 人审 + CodeNarc 复核，两道都过，才允许冻结成规范（见 tools/lock-corpus.sh）。
#
# 用法:
#   tools/check-corpus-codenarc.sh             # 未登记的违规 -> 非零退出
#   tools/check-corpus-codenarc.sh --list      # 只打印全部违规，不判失败
#
# 已知且可接受的违规登记在 config/codenarc/corpus-allow.txt：
#     <Rule> <路径模式>        例如  SpaceAroundMapEntryColon *   （全部文件）
#                                   LineLength wrapping/xxx.expected.groovy
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIX="$ROOT/groovyfmt-core/src/test/resources/fixtures"
RULESDIR="$ROOT/config/codenarc"
RULESET_NAME=formatting-only.xml
ALLOW="$RULESDIR/corpus-allow.txt"
LIST_ONLY=0
[ "${1:-}" = "--list" ] && LIST_ONLY=1

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
CN=$(find "$HOME/.gradle/caches" -name "CodeNarc-3.7.0-groovy-4.0.jar" | head -1)
GM=$(find "$HOME/.gradle/caches" -name "GMetrics-*.jar" | grep -v sources | head -1)
SL=$(find "$HOME/.gradle/caches" -name "slf4j-api-*.jar" | grep -v sources | head -1)
G4=$(find "$HOME/.gradle/caches" -path "*org.apache.groovy*4.0.30*" -name "*.jar" | grep -v sources | tr '\n' ':')
[ -f "$RULESDIR/$RULESET_NAME" ] || { echo "缺少 ruleset: $RULESDIR/$RULESET_NAME"; exit 2; }

REPORT=/tmp/cn-corpus.txt
# 注意：CodeNarc 的 -rulesetfiles 是从 classpath 解析的，所以规则目录要进 -cp
java -cp "$RULESDIR:/tmp:$CN:$GM:$SL:$G4" org.codenarc.CodeNarc -basedir="$FIX" \
  -includes='**/*.expected.groovy' -rulesetfiles="$RULESET_NAME" \
  -report=text:"$REPORT" > /dev/null 2>&1 || true

python3 - "$REPORT" "$ALLOW" "$LIST_ONLY" <<'PY'
import collections
import re
import sys

report, allow_path, list_only = sys.argv[1], sys.argv[2], sys.argv[3] == '1'

allowed = []
try:
    for line in open(allow_path, encoding='utf-8'):
        line = line.strip()
        if line and not line.startswith('#'):
            parts = line.split(None, 1)
            allowed.append((parts[0], parts[1].strip() if len(parts) > 1 else '*'))
except FileNotFoundError:
    pass

def is_allowed(rule, path):
    for r, pat in allowed:
        if r == rule and (pat == '*' or path.endswith(pat) or pat in path):
            return True
    return False

cur = None
found = []
for line in open(report, encoding='utf-8'):
    m = re.match(r'^File: (.+)$', line.strip())
    if m:
        cur = m.group(1)
        continue
    v = re.search(r'Rule=(\w+) P=\d+ Line=(\d+)', line)
    if v and cur:
        found.append((v.group(1), cur, int(v.group(2))))

by_rule = collections.Counter(r for r, _, _ in found)
accepted = [(r, f, l) for r, f, l in found if is_allowed(r, f)]
pending = [(r, f, l) for r, f, l in found if not is_allowed(r, f)]

print(f'  有违规的期望文件数: {len({f for _, f, _ in found})}   违规总数: {len(found)}')
if by_rule:
    print(f'  {"规则":44}{"数量":>6}{"已登记":>8}')
    for rule, n in by_rule.most_common():
        acc = sum(1 for r, _, _ in accepted if r == rule)
        print(f'  {rule:44}{n:>6}{acc:>8}')

if accepted:
    print(f'\n  ── 已登记为可接受（{len(accepted)} 条，来自 {allow_path}）──')
    seen = set()
    for r, f, _ in accepted:
        if (r, f) not in seen:
            seen.add((r, f))
            print(f'      {r:40} {f}')

if pending:
    print(f'\n  ── 未登记（{len(pending)} 条）—— 需要人或改期望、或登记理由 ──')
    for r, f, l in pending:
        print(f'      {r:40} {f}:{l}')
    if not list_only:
        print('\n  ✗ 期望输出里有未登记的 CodeNarc 违规：目标本身不合规，先解决它。')
        sys.exit(1)
else:
    print('\n  ✓ 期望输出没有未登记的 CodeNarc 违规。')
PY
