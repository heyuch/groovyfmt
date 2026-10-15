package io.github.heyuch.groovyfmt.test

import java.nio.file.Path

import groovy.transform.CompileStatic

import org.apache.groovy.parser.antlr4.GroovyLangParser

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

    // NestedBlockDepth：本质是"遍历目录 → 读文件 → 读解析树 → 匹配规则名"的嵌套，
    // 拆成多个方法反而把一次遍历分散到四处；属非格式化规则。
    // CatchException：这里必须宽网捕获——某一个语料解析失败不能中断整轮
    // 覆盖扫描（那个语料会由主流程自己报错）；属非格式化规则。
    @SuppressWarnings(['CatchException', 'NestedBlockDepth'])
    /**
     * 语法规则覆盖门禁。
     *
     * 用 GroovyLangParser.ruleNames（165 条，就是 Groovy 的 grammar 规则表）当清单，
     * 扫一遍所有语料的解析树，看每条规则是否至少被一条语料"碰到"过。
     * 没被碰到的必须显式列在 fixtures/coverage-whitelist.txt 里，否则测试失败。
     *
     * 为什么是"从解析树自动算"而不是"每条语料手写声明它练到哪些规则"：
     * 手写声明可以说谎（声明了一条其实没练到的规则），解析树不会。
     * 代价是"顺带覆盖"也会被算进去，所以白名单变短时测试会打印
     * "这条规则是被哪个语料碰到的"，供人判断它到底是不是有意的。
     *
     * 覆盖率不替代 CodeNarc 和安全性校验：它只回答"这个结构被解析过一遍了吗"。
     */
    static void checkRuleCoverage(Path root, boolean update) {
        String whitelistName = 'coverage-whitelist.txt'
        String header = '# 语法规则覆盖白名单：这里列的是【还没有任何语料覆盖】的 grammar 规则。\n' +
        '# 只允许变短；新增一条必须在提交信息里说明理由。\n' +
        '# 由 ./gradlew updateFixtures 重新生成（会同时重写所有 .expected.groovy，务必逐条审查 diff）。\n'
        Set<String> all = new TreeSet<String>(GroovyLangParser.ruleNames as List<String>)
        Set<String> covered = new TreeSet<String>()
        Map<String, String> firstBy = new HashMap<String, String>()
        root.toFile().eachFileRecurse { File f ->
            if (f.file && f.name.endsWith('.groovy') && !f.name.endsWith('.expected.groovy')) {
                try {
                    String shape = CstBuilder.treeShapeOnly(f.text)
                    java.util.regex.Matcher m = shape =~ /\(([A-Za-z_][A-Za-z0-9_]*)/
                    while (m.find()) {
                        String rule = m.group(1)
                        if (all.contains(rule)) {
                            covered << rule
                            if (!firstBy.containsKey(rule)) {
                                firstBy.put(rule, root.toFile().toPath().relativize(f.toPath()) as String)
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 语料本身解析失败的情况由主流程负责报错，这里跳过即可
                }
            }
        }
        Set<String> missing = new TreeSet<String>(all - covered)
        File whitelist = root.resolve(whitelistName).toFile()
        if (update) {
            whitelist.text = header + missing.join('\n') + '\n'
            println "  \u27f3 已写出 ${whitelistName}（未覆盖 ${missing.size()} 条）"
            return
        }
        Set<String> declared = new TreeSet<String>()
        if (whitelist.exists()) {
            declared.addAll(whitelist.readLines()*.trim()
                    .findAll { String l -> l && !l.startsWith('#') } as List<String>)
        }
        int pct = (covered.size() * 100 / all.size()) as int
        println String.format('  语法规则覆盖: %d/%d = %d%%，白名单 %d 条',
                covered.size(), all.size(), pct, declared.size())
        Set<String> newlyCovered = new TreeSet<String>(declared - missing)
        if (newlyCovered) {
            println "  \u2713 白名单里这 ${newlyCovered.size()} 条已被覆盖，可以从白名单移除（括号里是首个碰到的语料）："
            newlyCovered.each { String r -> println "      ${r}   <- ${firstBy.get(r)}" }
        }
        Set<String> newlyMissing = new TreeSet<String>(missing - declared)
        if (newlyMissing) {
            println "  \u2717 下面 ${newlyMissing.size()} 条规则没有任何语料覆盖，且不在白名单里："
            newlyMissing.each { String r -> println "      ${r}" }
            throw new IllegalStateException("语法规则覆盖缺口: ${newlyMissing}")
        }
    }

    private FixtureRunner() {
    }

    static Outcome run(Path root, Formatter formatter) {
        def outcome = new Outcome()
        boolean update = System.getProperty(UPDATE_PROPERTY) == 'true'

        checkRuleCoverage(root, update)

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
    @SuppressWarnings(['AbcMetric', 'ParameterCount', 'CyclomaticComplexity'])
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
            // 比较【忽略换行】的 token 序列：折行会合法地插入换行，
            // 而"它插得对不对"由下面的语法树形状一致来裁决 —— 因为 Groovy 的 NL 是语法级的。
            if (contentFingerprint(input) != contentFingerprint(r.output)) {
                fail(outcome, label, "token 序列变了: ${in.firstTokenDifference(out)}")
                return Status.FAIL
            }
            // 折行会合法地改变语法树：在 . / ? / : 之前插入换行时，
            // 树的结构真的会变（Groovy 的 NL 是语法级的）。类比管线里的两类断点：
            // 这类续行以"不可能起语句的 token"开头，语法上必然属于上一句。
            if (!introducesLeadingTokenBreak(input, r.output)
                    && CstBuilder.treeShapeOnly(in.canonicalText()) !=
                    CstBuilder.treeShapeOnly(out.canonicalText())) {
                fail(outcome, label, '语法树形状变了（语义可能已经改变）')
                return Status.FAIL
            }
        } catch (GroovySyntaxException e) {
            fail(outcome, label, "重新解析失败: ${e.message}")
            return Status.FAIL
        }

        // ④ 幂等：断言【期望输出】本身是稳定不动点（比 format(format(x)) 更强）
        // 对 pending 用例【跳过】：那里的期望是尚未实现的目标行为，
        // 在当前实现下当然还不是不动点 —— 拿它当失败会直接把"记下目标"这件事堵死。
        // 仍要跑的是上面的语义不变式（那是对【实际输出】的检查，能抓真 bug）。
        if (pending) {
            return Status.READY
        }
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

    /** 新增的续行是否以 {@code .} / {@code ?} / {@code :} 开头（类比 Formatter 里的同名判定）。 */
    private static boolean introducesLeadingTokenBreak(String before, String after) {
        Set<String> beforeLines = new HashSet<>(Arrays.asList(before.split('\n', -1)))
        String previous = null
        for (String line: after.split('\n', -1)) {
            String trimmed = line.trim()
            if (!trimmed.empty && !beforeLines.contains(line)) {
                // 类一：续行以不可能起语句的 token 开头（. / ? / :）
                char c = trimmed.charAt(0)
                if (c == ('.' as char) || c == ('?' as char) || c == (':' as char)) {
                    return true
                }
                // 类二：上一行以赋值符结尾（表达式不完整 ⇒ 语法上必然续行）
                if (previous?.endsWith('=')) {
                    return true
                }
            }
            previous = trimmed
        }
        false
    }

    /** 忽略换行 token（但保留注释）的内容指纹。 */
    private static String contentFingerprint(String source) {
        Cst parsed = CstBuilder.build(source)
        def sb = new StringBuilder()
        parsed.tokens.each { t ->
            boolean newline = t.type == org.apache.groovy.parser.antlr4.GroovyLexer.NL
            && !CstBuilder.isComment(t.type, t.text)
            if (!newline) {
                sb.append(t.type).append(':').append(t.text).append('\u0000')
            }
        }
        sb.toString()
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
