package io.github.heyuch.groovyfmt.test

import java.nio.file.Path

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstBuilder
import io.github.heyuch.groovyfmt.cst.GroovySyntaxException
import io.github.heyuch.groovyfmt.layout.Stats
import io.github.heyuch.groovyfmt.pipeline.FormatResult
import io.github.heyuch.groovyfmt.pipeline.Formatter

/**
 * 基于 fixture 的 E2E 测试跑器。
 *
 * <h3>约定</h3>
 * <pre>
 *   fixtures/&lt;group&gt;/&lt;name&gt;.groovy                 输入
 *   fixtures/&lt;group&gt;/&lt;name&gt;.expected.groovy          期望输出（可省）
 *   fixtures/&lt;group&gt;/&lt;name&gt;.invalid.groovy           必须被安全闸门拒绝
 *
 *   fixtures/pending/&lt;name&gt;.groovy                  同上，但属于【目标行为尚未实现】
 * </pre>
 *
 * <ul>
 *   <li>有 {@code .expected.groovy} → 断言 {@code format(输入) == 期望}</li>
 *   <li>没有 → 断言 {@code format(输入) == 输入}（"必须保持不变"的用例，占一半以上）</li>
 *   <li>{@code .invalid.groovy} → 断言被拒绝、且原样返回</li>
 *   <li>{@code pending/} → 期望值固化的是<b>目标行为</b>；不一致<b>不算失败</b>，
 *       只报告进度。语义不变式（无损 / token / 语法树 / 幂等）<b>仍然强制</b> ——
 *       真 bug 不会被 "pending" 掩盖。</li>
 * </ul>
 *
 * <h3>为什么需要 pending 目录</h3>
 * 先固化目标行为、再决定实现，比反过来安全。但直接把它写进正式 fixture 会让构建一直红着，
 * 于是 CI 失去意义；写成"当前行为"又会把 bug 固化成期望。
 * pending 就是这两种坏选择的第三条路：<b>期望是目标，构建保持绿，差距被计数并打印</b>。
 * 某个 pending 用例通过后，把它移到正式目录即可。
 *
 * <h3>为什么每个 fixture 都要额外跑一组不变式</h3>
 * 因为 golden 测试单独用有两个隐蔽的失效模式：
 *
 * <ol>
 *   <li><b>fail-closed 会让 golden 静默失效</b>：如果 formatter 拒绝了这个文件，
 *       {@code output == input}，那么"输入 = 期望"的用例会<b>假通过</b> ——
 *       你以为是"正确地没改"，其实是"根本没处理"。所以必须先断言 {@code !skipped}。</li>
 *   <li><b>golden 记录的是"当前行为"，不是"正确行为"</b>：它无法区分"正确"和"一致地错"。
 *       所以每个 fixture 都要独立地过一遍语义不变式（无损 / token 序列 / 语法树 / 幂等），
 *       这些是与 golden 相互独立的判据。</li>
 * </ol>
 *
 * <h3>更新期望</h3>
 * 用 {@code ./gradlew updateFixtures}（= {@code -Dgroovyfmt.updateFixtures=true}）。
 * 注意：<b>只更新已存在的 .expected 文件</b>；本来没有期望文件却发生了变化，
 * 一律报错而不是自动生成 —— 否则一次盲目的 bless 就会把 bug 固化成"期望"。
 * 也<b>不会</b>碰 pending 目录：那里的期望是手写的目标，不该被当前实现覆盖。
 */
@CompileStatic
class FixtureRunner {

    static final String UPDATE_PROPERTY = 'groovyfmt.updateFixtures'

    /** 目录名：这里的 fixture 固化的是目标行为，尚未实现，因此不参与失败判定。 */
    static final String PENDING_DIR = 'pending'

    /** 每个 fixture 最多展示几行差异，避免刷屏。 */
    private static final int MAX_DIFF_LINES = 8

    private enum Status {

        PASS, PENDING, READY, FAIL

    }

    static final class Outcome {

        int passed
        int failed
        int updated
        /** 正式（非 pending）fixture 数 */
        int fixtureCount
        /** pending fixture 数 */
        int pendingCount
        /** pending 里已经能通过的（可以提升为正式 fixture） */
        int pendingReady
        final List<String> failures = []
        final Stats aggregate = new Stats()
        final Map<String, int[]> perGroup = new LinkedHashMap<String, int[]>()

        int[] group(String name) {
            int[] counter = perGroup[name]
            if (counter == null) {
                counter = new int[2]
                perGroup[name] = counter
            }
            counter
        }

    }

    private FixtureRunner() {
    }

    static Outcome run(Path root, Formatter formatter) {
        def outcome = new Outcome()
        boolean update = System.getProperty(UPDATE_PROPERTY) == 'true'

        List<File> fixtures = []
        root.toFile().eachFileRecurse { File f ->
            if (f.file && f.name.endsWith('.groovy') && !f.name.endsWith('.expected.groovy')) {
                fixtures << f
            }
        }
        fixtures.sort { it.path }

        boolean pendingMode = false
        fixtures.each { File fixture ->
            String relative = root.toFile().toPath().relativize(fixture.toPath()) as String
            boolean pending = relative.startsWith(PENDING_DIR + File.separator)
            if (pending) {
                if (!pendingMode) {
                    println '  ── 待实现（固化目标行为，不参与失败判定）' + '─'.repeat(10)
                    pendingMode = true
                }
                outcome.pendingCount++
            } else {
                outcome.fixtureCount++
            }

            String group = pending ? PENDING_DIR :
            (relative.contains(File.separator) ?
                    relative.substring(0, relative.indexOf(File.separator)) : '(root)')

            Status status = fixture.name.endsWith('.invalid.groovy') ?
            (checkInvalid(fixture, relative, formatter) ? Status.PASS : Status.FAIL) :
            checkGolden(fixture, relative, formatter, outcome, update, pending)

            if (pending) {
                if (status == Status.READY) {
                    outcome.pendingReady++
                    println "  ★ $relative 已通过 —— 可以移到正式目录了"
                }
                // pending 用例的金色比对不算失败；但语义不变式违规仍然算
                if (status == Status.FAIL) {
                    outcome.failed++
                    outcome.group(group)[1]++
                }
            } else if (status == Status.FAIL) {
                outcome.failed++
                outcome.group(group)[1]++
            } else {
                outcome.passed++
                outcome.group(group)[0]++
            }
        }
        outcome
    }

    // ─────────────────────────── 正常 fixture ───────────────────────────

    // AbcMetric/ParameterCount：这个方法是"一个 fixture 的完整判定流程"，
    // 四道检查 + pending 分支堆在一起，拆开反而看不出顺序与依赖。
    @SuppressWarnings(['AbcMetric', 'ParameterCount'])
    private static Status checkGolden(File fixture, String label, Formatter formatter,
            Outcome outcome, boolean update, boolean pending) {
        String input = fixture.getText('UTF-8')
        File expectedFile = expectedFileFor(fixture)
        boolean hasExpected = expectedFile.file
        String expected = hasExpected ? expectedFile.getText('UTF-8') : input

        FormatResult r = formatter.format(input)

        // ① 最重要的一条：必须是"真的格式化了"，而不是"被安全闸门拒绝了"
        if (r.skipped) {
            fail(outcome, label, "被安全闸门跳过（golden 测试会因此静默失效）: ${r.skipReason}")
            return Status.FAIL
        }
        outcome.aggregate.horizontalGaps += r.stats.horizontalGaps
        outcome.aggregate.classified += r.stats.classified
        outcome.aggregate.unclassified += r.stats.unclassified
        outcome.aggregate.verticalGaps += r.stats.verticalGaps

        // ② golden 比对
        if (r.output != expected) {
            if (pending) {
                // 目标行为尚未实现：只报告进度，不判失败
                println "  ⏳ $label"
                lineDiff(expected, r.output).readLines().each { String line ->
                    println "      $line"
                }
                return Status.PENDING
            }
            String problem = hasExpected ?
            '输出与 .expected.groovy 不一致' :
            '输出发生了变化，但这个 fixture 声称"必须保持不变"'
            if (update && hasExpected) {
                expectedFile.setText(r.output, 'UTF-8')
                outcome.updated++
                return Status.PASS
            }
            String hint = hasExpected ?
            '（若这是预期内的规则变更：./gradlew updateFixtures 后审查 diff）' :
            '（若这个变化是对的：给它补一个 .expected.groovy）'
            fail(outcome, label, "$problem $hint\n${lineDiff(expected, r.output)}")
            return Status.FAIL
        }

        // ③ 语义不变式（与 golden 相互独立的判据）——对 pending 同样强制，真 bug 不该被掩盖
        try {
            Cst in = CstBuilder.build(input)
            Cst out = CstBuilder.build(r.output)
            if (!in.lossless || !out.lossless) {
                fail(outcome, label, '无损断言失败')
                return Status.FAIL
            }
            // 用【规范化】形式比对：空行规范化会增删 NL token，
            // 所以唯一允许的 token 级差异就是空行的个数（见 Cst#canonicalTokenFingerprint）。
            if (in.canonicalTokenFingerprint() != out.canonicalTokenFingerprint()) {
                fail(outcome, label, "token 序列变了: ${in.firstTokenDifference(out)}")
                return Status.FAIL
            }
            if (CstBuilder.treeShapeOnly(in.canonicalText()) !=
                    CstBuilder.treeShapeOnly(out.canonicalText())) {
                fail(outcome, label, '语法树形状变了（语义可能已经改变）')
                return Status.FAIL
            }
        } catch (GroovySyntaxException e) {
            fail(outcome, label, "重新解析失败: ${e.message}")
            return Status.FAIL
        }

        // ④ 幂等：断言【期望输出】本身是稳定不动点（比 format(format(x)) 更强）
        FormatResult again = formatter.format(expected)
        if (again.skipped || again.output != expected) {
            if (pending) {
                // 期望输出自己不是不动点 —— 说明期望写错了，这要报出来
                fail(outcome, label,
                        "期望输出不是幂等不动点${again.skipped ? '（被跳过）' : ''}\n${lineDiff(expected, again.output)}")
                return Status.FAIL
            }
            fail(outcome, label,
                    "期望输出不是幂等不动点${again.skipped ? '（被跳过）' : ''}\n${lineDiff(expected, again.output)}")
            return Status.FAIL
        }
        pending ? Status.READY : Status.PASS
    }

    // ─────────────────────────── 必须被拒绝 ───────────────────────────

    private static boolean checkInvalid(File fixture, String label, Formatter formatter) {
        String input = fixture.getText('UTF-8')
        FormatResult r = formatter.format(input)
        if (!r.skipped) {
            return fail(null, label, formatResultReport(r))
        }
        if (r.output != input) {
            return fail(null, label, '被拒绝了，但原样返回输入')
        }
        true
    }

    private static String formatResultReport(FormatResult r) {
        "本该被安全闸门拒绝，实际却格式化了。\n  实际输出: [${r.output.replace('\n', '\\n')}]"
    }

    // ─────────────────────────── 基础设施 ───────────────────────────

    private static File expectedFileFor(File fixture) {
        String name = fixture.name
        String base = name.substring(0, name.length() - '.groovy'.length())
        new File(fixture.parentFile, base + '.expected.groovy')
    }

    private static boolean fail(Outcome outcome, String label, String detail) {
        if (outcome != null) {
            outcome.failures << (label + '  —— ' + detail)
        }
        println "  ✗ $label"
        detail.readLines().each { String line -> println "      $line" }
        false
    }

    /** 逐行差异：只展示前若干处不同，够定位就行。 */
    static String lineDiff(String expected, String actual) {
        String[] e = expected.split('\n', -1)
        String[] a = actual.split('\n', -1)
        def sb = new StringBuilder('期望 <> 实际 差异：')
        int shown = 0
        int n = Math.max(e.length, a.length)
        for (int i = 0; i < n && shown < MAX_DIFF_LINES; i++) {
            String le = i < e.length ? e[i] : '<缺失>'
            String la = i < a.length ? a[i] : '<缺失>'
            if (le != la) {
                sb.append("\n    第 ${i + 1} 行: 期望 [${le}]  实际 [${la}]")
                shown++
            }
        }
        if (shown == 0) {
            sb.append('（逐行比较无差异？请检查行尾空白）')
        }
        sb.toString()
    }

}
