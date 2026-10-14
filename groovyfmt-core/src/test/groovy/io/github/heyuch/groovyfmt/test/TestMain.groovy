package io.github.heyuch.groovyfmt.test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.layout.Stats
import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.pipeline.FormatResult
import io.github.heyuch.groovyfmt.pipeline.Formatter
import io.github.heyuch.groovyfmt.rule.GapContext
import io.github.heyuch.groovyfmt.rule.RuleRegistry
import io.github.heyuch.groovyfmt.rule.SpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.BinaryOperatorSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.BlockIndentRule

/**
 * 测试入口。
 *
 * <p>策略：<b>以 E2E 黄金样例为主</b>（见 {@link FixtureRunner}），
 * 只保留少量 E2E 表达不了的 API 契约测试与 CLI 退出码测试。
 *
 * <p>为什么不做大量单元测试：formatter 的契约就是"文本 → 文本"，
 * 而真正危险的 bug（GString 的 $、lexer 选型、RollBackOne）都出现在<b>跨层交互</b>上，
 * 单元测试根本测不到。所以资源应该投在"更多、更真实的语法片段"上，
 * 而不是"更细的方法拆分"。
 *
 * <p>但两类东西 E2E 测不了，必须留在这一层：
 * <ul>
 *   <li><b>注册期契约</b>：Slot 冲突检测发生在构造 RuleRegistry 时，没有文本输入输出；</li>
 *   <li><b>指标口径</b>：覆盖率统计是否在说谎，golden 测试永远发现不了（它曾经恒等于 100%）；</li>
 *   <li><b>退出码</b>：CI 的契约，必须以子进程方式验证。</li>
 * </ul>
 */
@CompileStatic
class TestMain {

    private static final Formatter FMT = Formatter.defaults()

    private static int passed = 0
    private static int failed = 0
    private static final List<String> FAILURES = []

    private TestMain() {
    }

    static void main(String[] args) {
        FixtureRunner.Outcome fixtures = fixtureSuite()
        contractTests()
        cliTests()
        exampleSmoke()
        report(fixtures)
    }

    // ─────────────────────── E2E 黄金样例（主策略）───────────────────────

    private static FixtureRunner.Outcome fixtureSuite() {
        Path root = fixturesRoot()
        section("E2E 黄金样例（fixtures @ ${root}）")
        FixtureRunner.Outcome outcome = FixtureRunner.run(root, FMT)
        outcome.perGroup.sort { it.key }.each { String group, int[] counter ->
            String mark = counter[1] == 0 ? '✓' : '✗'
            String line = "  $mark $group".padRight(30) + "通过 ${counter[0]}" +
            (counter[1] ? "  失败 ${counter[1]}" : '')
            println line
        }
        println '  ─────'
        println "  正式 fixture ${outcome.fixtureCount} 个：通过 ${outcome.passed}，失败 ${outcome.failed}"
        if (outcome.pendingCount > 0) {
            println "  待实现 fixture ${outcome.pendingCount} 个：已通过 ${outcome.pendingReady}" +
            '（目标行为已固化，不参与失败判定）'
        }
        passed += outcome.passed
        failed += outcome.failed
        FAILURES.addAll(outcome.failures)
        if (outcome.updated > 0) {
            println "  ⚠ 已更新 ${outcome.updated} 个 .expected.groovy —— 请审查 diff"
        }
        outcome
    }

    private static Path fixturesRoot() {
        Path p = Paths.get('src/test/resources/fixtures')
        if (!Files.isDirectory(p)) {
            p = Paths.get('groovy-fmt/src/test/resources/fixtures')
        }
        if (!Files.isDirectory(p)) {
            throw new IllegalStateException('找不到 fixtures 目录（请在 groovy-fmt/ 目录下运行）')
        }
        p
    }

    // ──────────────── E2E 表达不了的 API 契约（保持精简）────────────────

    private static void contractTests() {
        section('API 契约（E2E 覆盖不到的部分）')

        // 注册期冲突检测：发生在构造 RuleRegistry 时，没有文本输入输出
        SpacingRule duplicate = new SpacingRule() {

            @Override
            String getId() {
                'test.duplicate'
            }

            @Override
            Set<Slot> getSlots() {
                EnumSet.of(Slot.BEFORE_BINARY_OP) as Set<Slot>
            }

            @Override
            Spacing decide(Slot slot, GapContext ctx) {
                Spacing.SPACE
            }

        }
        try {
            new RuleRegistry().register(new BinaryOperatorSpacingRule()).register(duplicate)
            fail('Slot 冲突必须在注册期报错', '居然没有抛异常')
        } catch (IllegalStateException e) {
            pass("Slot 冲突在注册期被拦截: ${e.message}".toString())
        }
        try {
            new RuleRegistry().register(new BinaryOperatorSpacingRule())
            .register(new BinaryOperatorSpacingRule())
            fail('同一规则注册两次也必须报错', '居然没有抛异常')
        } catch (IllegalStateException ignored) {
            pass('重复注册同一规则被拦截')
        }
        check('未覆盖的 Slot 可枚举',
                new RuleRegistry().register(new BlockIndentRule())
                .uncoveredSlots().contains(Slot.BEFORE_BINARY_OP), '')

        // 指标口径：golden 测试永远发现不了"覆盖率恒等于 100%"这种谎报
        Stats stats = FMT.format('def x=1\n').stats
        check('覆盖率口径自洽（分母包含未分类的 gap）',
                stats.horizontalGaps == stats.classified + stats.unclassified,
                "horizontalGaps=${stats.horizontalGaps} classified=${stats.classified} " +
                "unclassified=${stats.unclassified}")

        // 错误信息质量：CI 里要靠它定位
        FormatResult bad = FMT.format('def x = (\n')
        check('语法错误信息带行列号',
                bad.skipped && bad.skipReason ==~ /(?s).*line \d+:\d+.*/,
                "skipReason=${bad.skipReason}")
        check('非法 GString 被拒绝', FMT.format('def s = "a\$ { b }"\n').skipped, '')
    }

    // ─────────────────── CLI 退出码（CI 的契约）───────────────────

    private static void cliTests() {
        section('CLI 退出码契约（子进程端到端）')

        Path tmp = Files.createTempDirectory('groovyfmt-cli')
        try {
            Path unformatted = tmp.resolve('unformatted.groovy')
            Files.write(unformatted, 'def x=1\n'.getBytes(StandardCharsets.UTF_8))
            Path formatted = tmp.resolve('formatted.groovy')
            Files.write(formatted, 'def x = 1\n'.getBytes(StandardCharsets.UTF_8))
            Path broken = tmp.resolve('broken.groovy')
            Files.write(broken, 'def x = (\n'.getBytes(StandardCharsets.UTF_8))

            check('--check 有差异 → 退出码 1',
                    cli(['--check', unformatted.toString()]).code == 1, '')
            check('--check 无差异 → 退出码 0',
                    cli(['--check', formatted.toString()]).code == 0, '')
            check('语法错误 → 退出码 2',
                    cli(['--check', broken.toString()]).code == 2, '')

            def piped = cli(['-'], 'def x=1\n')
            check('stdin → stdout 管道', piped.code == 0 && piped.out == 'def x = 1\n',
                    "code=${piped.code} out=[${piped.out}]")
        } finally {
            tmp.toFile().deleteDir()
        }
    }

    /** 以子进程跑真正的 CLI —— 退出码是 CI 契约，必须端到端验证。 */
    private static Map<String, Object> cli(List<String> args, String stdinText = null) {
        String java = System.getProperty('java.home') + File.separator + 'bin' + File.separator + 'java'
        def cmd = [java, '-cp', System.getProperty('java.class.path'), 'io.github.heyuch.groovyfmt.cli.Main'] + args
        def p = new ProcessBuilder(cmd).redirectErrorStream(true).start()
        if (stdinText != null) {
            p.outputStream.withWriter('UTF-8') { it.write(stdinText) }
        } else {
            p.outputStream.close()
        }
        String out = p.inputStream.getText('UTF-8')
        [code: p.waitFor(), out: out]
    }

    // ─────────────────────────── 真实样例冒烟 ───────────────────────────

    private static void exampleSmoke() {
        section('真实样例冒烟')
        Path path = Paths.get('examples/Messy.groovy')
        if (!Files.exists(path)) {
            fail('读取 examples/Messy.groovy', "找不到文件（当前目录 ${Paths.get('').toAbsolutePath()}）")
            return
        }
        String src = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        FormatResult r = FMT.format(src)
        if (r.skipped) {
            fail('格式化 Messy.groovy', "被跳过: ${r.skipReason}")
            return
        }
        pass('格式化 Messy.groovy（未触发任何安全闸门）')
        check('Messy.groovy 幂等', FMT.format(r.output).output == r.output, '')
    }

    // ─────────────────────────── 基础设施 ───────────────────────────

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass(name)
        } else {
            fail(name, detail)
        }
    }

    private static void section(String title) {
        println ''
        println '── ' + title + ' ' + '─'.repeat(Math.max(0, 58 - title.length()))
    }

    private static void pass(String name) {
        passed++
        println "  ✓ $name"
    }

    private static void fail(String name, String detail) {
        failed++
        println '  ✗ ' + name + (detail.empty ? '' : '  —— ' + detail)
        FAILURES << (name + '  —— ' + detail)
    }

    private static void report(FixtureRunner.Outcome fixtures) {
        println ''
        println '══════════════════════════════════════════════════════════'
        println "  通过 $passed，失败 $failed"
        println "  （其中 E2E 正式 fixture ${fixtures.passed}/${fixtures.fixtureCount}" +
        (fixtures.pendingCount ? "，待实现 ${fixtures.pendingReady}/${fixtures.pendingCount}" : '') + '）'
        if (FAILURES) {
            println '  失败明细:'
            FAILURES.each { String f -> println "    - $f" }
        }
        println '══════════════════════════════════════════════════════════'

        def registry = RuleRegistry.defaults()
        Stats agg = fixtures.aggregate
        println ''
        println '  规则框架状态:'
        println "    水平规则 ${registry.spacingRules().size()} 条，" +
        "缩进规则 ${registry.indentRules().size()} 条"
        println "    已覆盖 Slot ${registry.coveredSlots().size()}/${Slot.values().length}，" +
        "未覆盖 ${registry.uncoveredSlots().size()} 个（这些位置一律保留原文）"
        println '  fixture 语料上的覆盖率:'
        println "    ${agg.summary()}"
        println ''

        System.exit(failed == 0 ? 0 : 1)
    }

}
