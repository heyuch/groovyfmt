#!/usr/bin/env bash
# groovyfmt —— 统一构建入口（薄封装，实际工作交给 Gradle）
#
#   ./build.sh                    编译 + 完整质量闸门（CodeNarc + Spotless 自举 + 测试）
#   ./build.sh publishFormatter   把 formatter 发布到本地仓库（= mvn install）
#                                 ※ 冷启动（本地仓库还没有该版本）时加 -PbootstrapFormatter=true
#   ./build.sh runTests           跑两个 module 的测试
#   ./build.sh formatApply        用 groovyfmt 格式化全部源码（Spotless 驱动）
#   ./build.sh :groovyfmt-core:run --args="--stats examples/Messy.groovy"
#   ./build.sh :groovyfmt-core:installDist
#                                 → groovyfmt-core/build/install/groovyfmt/bin/groovyfmt
#
# 首次运行会由 Gradle wrapper 自动准备 Gradle 发行版。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# Gradle 需要能定位到 JDK 17+
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  export JAVA_HOME
fi

if [ "$#" -eq 0 ]; then
  exec ./gradlew check
fi
exec ./gradlew "$@"
