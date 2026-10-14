#!/usr/bin/env bash
#
# 在 saas-service 上验证格式化器：tools/verify-saas.sh [模块名] [仓库路径]
#
# 为什么要有这个脚本：这套流程（构建 CLI -> 还原 -> 测基线 -> 格式化 -> 复测 ->
# 对比 -> 校验安全契约）手工重放了很多轮，而每次 `./gradlew clean check` 都会删掉
# build/install，于是陷入「CLI 不存在 -> xargs 静默失败 -> 拿原始代码当结果」的坑
# （踩了三次）。固化成脚本后第一步就重建 CLI，这个坑在物理上不会再出现。
#
# 变量一律写 ${NAME}：脚本里有全角标点紧邻变量名，bash 在非 C locale 下会把全角
# 字符当成变量名的一部分（`$MODULE：` 会报 unbound variable）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="${1:-output}"
REPO="${2:-${SAAS_REPO:-/Users/humpy/Code/SaaS/saas-service-giis247}}"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
RULESET=/tmp/fmt-only-ruleset.xml
BASE="/tmp/cn-base-${MODULE}.txt"
AFTER="/tmp/cn-after-${MODULE}.txt"
FILES="/tmp/gf-files-${MODULE}.txt"

echo "=== 0. build CLI (clean check deletes build/install, so always rebuild)"
cd "${ROOT}"
./gradlew :groovyfmt-core:installDist -q --console=plain
CLI="${ROOT}/groovyfmt-core/build/install/groovyfmt/bin/groovyfmt"
[ -x "${CLI}" ] || { echo "  FAIL: CLI not built"; exit 1; }
echo "  ok ${CLI}"

echo "=== 1. CodeNarc runtime"
CN=$(find "${HOME}/.gradle/caches" -name "CodeNarc-3.7.0-groovy-4.0.jar" | head -1)
GM=$(find "${HOME}/.gradle/caches" -name "GMetrics-*.jar" | grep -v sources | head -1)
SL=$(find "${HOME}/.gradle/caches" -name "slf4j-api-*.jar" | grep -v sources | head -1)
G4=$(find "${HOME}/.gradle/caches" -path "*org.apache.groovy*4.0.30*" -name "*.jar" | grep -v sources | tr '\n' ':')
CP="/tmp:${CN}:${GM}:${SL}:${G4}"
[ -f "${RULESET}" ] || { echo "  FAIL: missing ${RULESET}"; exit 1; }

run_codenarc() {
  java -cp "${CP}" org.codenarc.CodeNarc -basedir="${REPO}/${MODULE}" \
    -includes='**/src/**/*.groovy' -rulesetfiles=fmt-only-ruleset.xml \
    -report=text:"$1" 2>&1 | grep -vE '^$|SLF4J|^CodeNarc|recognition' | head -2 || true
}

cd "${REPO}"
find "${MODULE}" -name "*.groovy" -path "*/src/*" -not -path "*/build/*" | sort > "${FILES}"
echo "  module ${MODULE}: $(wc -l < "${FILES}" | tr -d ' ') groovy files"

echo "=== 2. restore, then measure ORIGINAL baseline"
git restore .
run_codenarc "${BASE}"
grep -E '^Summary' "${BASE}" | sed 's/^/  baseline: /'

echo "=== 3. format"
xargs "${CLI}" -w < "${FILES}" > /tmp/gf-fmt.out 2> /tmp/gf-fmt.err
echo "  formatted $(grep -c '^已格式化' /tmp/gf-fmt.out || echo 0) / skipped-or-rejected $(wc -l < /tmp/gf-fmt.err | tr -d ' ')"
if [ -s /tmp/gf-fmt.err ]; then head -3 /tmp/gf-fmt.err | sed 's/^/    stderr: /'; fi
git diff --shortstat | sed 's/^/  /'

echo "=== 4. safety contract (identical after stripping whitespace)"
python3 - "${REPO}" <<'PYEOF'
import re, subprocess, sys
repo = sys.argv[1]
changed = subprocess.run(['git', 'diff', '--name-only'], cwd=repo,
                         capture_output=True, text=True).stdout.split()
bad = []
for f in changed:
    old = subprocess.run(['git', 'show', 'HEAD:' + f], cwd=repo,
                         capture_output=True, text=True).stdout
    new = open(repo + '/' + f, encoding='utf-8').read()
    if re.sub(r'\s+', '', old) != re.sub(r'\s+', '', new):
        bad.append(f)
status = 'OK' if not bad else 'FAIL'
print('  ' + str(len(changed)) + ' files, non-whitespace diffs ' + str(len(bad)) + '  ' + status)
for f in bad[:5]:
    print('   ' + f)
PYEOF

echo "=== 5. re-measure, compare with baseline"
run_codenarc "${AFTER}"
grep -E '^Summary' "${AFTER}" | sed 's/^/  now: /'
python3 - "${BASE}" "${AFTER}" <<'PYEOF'
import collections, re, sys
def agg(path):
    c = collections.Counter()
    for l in open(path, encoding='utf-8'):
        m = re.search(r'Rule=(\w+)', l)
        if 'Rule=' in l and m:
            c[m.group(1)] += 1
    return c
base, now = agg(sys.argv[1]), agg(sys.argv[2])
print('  ' + 'rule'.ljust(50) + 'base'.rjust(8) + 'now'.rjust(8))
for r in sorted(set(base) | set(now), key=lambda r: -max(base[r], now[r])):
    mark = ''
    if base[r] == 0 and now[r] > 0:
        mark = '  <== NEW'
    elif base[r] > 0 and now[r] == 0:
        mark = '  <== cleared'
    print('  ' + r.ljust(50) + str(base[r]).rjust(8) + str(now[r]).rjust(8) + mark)
PYEOF
