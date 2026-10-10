package io.github.heyuch.groovyfmt.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstBuilder
import io.github.heyuch.groovyfmt.cst.GroovySyntaxException
import io.github.heyuch.groovyfmt.model.FormatStyle
import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.pipeline.FinalNewlineStep
import io.github.heyuch.groovyfmt.pipeline.FormatResult
import io.github.heyuch.groovyfmt.pipeline.Formatter
import io.github.heyuch.groovyfmt.rule.IndentRule
import io.github.heyuch.groovyfmt.rule.RuleRegistry
import io.github.heyuch.groovyfmt.rule.SpacingRule

/**
 * 命令行入口。
 *
 * <p>动作本身也是 Command：{@code --check} / {@code -w} / {@code --diff} / stdin
 * 只是同一份格式化逻辑的不同"执行器"，互不干扰。
 */
@CompileStatic
class Main {

    private static final String USAGE = '''\
groovyfmt —— 轻量级 Groovy 格式化工具（v0.1，基于 token 重排 + 安全闸门）

用法:
  groovyfmt [选项] [文件...]
  groovyfmt - < 文件.groovy > 文件.groovy      # stdin → stdout

选项:
  -w, --write          就地写回
      --check          只检查，有文件需要改动则退出码 1
      --diff           打印差异（不写回）
      --stats          打印覆盖率统计
      --indent N       块缩进宽度（默认 4）
      --continuation N 续行缩进宽度（默认 8）
      --no-verify      关闭语法树二次校验（默认开启）
      --no-blank-lines 关闭空行调整（回到"绝不增删 token"的保守行为）
      --rules          列出已注册的规则
      --slots          列出还没被任何规则覆盖的 Slot
  -h, --help           显示帮助

安全契约:
  语法不正确 → 不动。格式化后 token 序列或语法树发生变化 → 回退并原样返回。
  本工具绝不新增或删除换行，只在行内空白与行首缩进上做文章。
'''

    private Main() {
    }

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'MethodSize'])   // 命令行参数解析天然是一长串分支
    static void main(String[] args) {
        boolean write = false
        boolean check = false
        boolean diff = false
        boolean stats = false
        int indent = 4
        int continuation = 8
        boolean verify = true
        boolean noBlankLines = false
        boolean stdin = false
        def files = new ArrayList<String>()

        for (int i = 0; i < args.length; i++) {
            String a = args[i]
            if (a == '-h' || a == '--help') {
                println USAGE
                return
            }
            if (a == '-w' || a == '--write') {
                write = true
                continue
            }
            if (a == '--check') {
                check = true
                continue
            }
            if (a == '--diff') {
                diff = true
                continue
            }
            if (a == '--stats') {
                stats = true
                continue
            }
            if (a == '--no-verify') {
                verify = false
                continue
            }
            if (a == '--no-blank-lines') {
                noBlankLines = true
                continue
            }
            if (a == '--rules') {
                printRules()
                return
            }
            if (a == '--slots') {
                printSlots()
                return
            }
            if (a == '--indent') {
                indent = Integer.parseInt(args[++i])
                continue
            }
            if (a == '--continuation') {
                continuation = Integer.parseInt(args[++i])
                continue
            }
            if (a == '-') {
                stdin = true
                continue
            }
            if (a.startsWith('-')) {
                System.err.println("未知选项: $a")
                System.err.print(USAGE)
                System.exit(2)
            }
            files << a
        }

        def style = FormatStyle.DEFAULT
        .withIndentWidth(indent)
        .withContinuationIndent(continuation)
        .withVerifyParseTree(verify)
        if (noBlankLines) {
            style = style.withoutBlankLineAdjustment()
        }
        def formatter = new Formatter(style, RuleRegistry.defaults(), [new FinalNewlineStep()])

        if (stdin) {
            runStdin(formatter, stats)
        }

        if (files.empty) {
            print USAGE
            System.exit(2)
        }

        boolean anyChanged = false
        boolean anyError = false
        for (String f: files) {
            Path path = Paths.get(f)
            String input = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            FormatResult r = formatter.format(input)

            if (r.skipped) {
                System.err.println("跳过 $f —— ${r.skipReason}")
                anyError = true
                continue
            }
            if (r.changed) {
                anyChanged = true
            }
            if (stats) {
                println "$f: ${r.stats.summary()}"
            }
            if (diff && r.changed) {
                printDiff(f, input, r.output)
            }
            if (write && r.changed) {
                Files.write(path, r.output.getBytes(StandardCharsets.UTF_8))
                println "已格式化 $f"
            }
            // 默认行为（gofmt 风格）：没有任何写/检/差异选项时，把结果打到 stdout
            if (!write && !check && !diff) {
                print r.output
            }
        }

        if (check) {
            if (anyChanged) {
                println '以上文件需要格式化（--check 失败）'
                System.exit(1)
            }
            println '所有文件都已格式化 ✓'
        }
        System.exit(anyError ? 2 : 0)
    }

    private static void runStdin(Formatter formatter, boolean stats) {
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
        FormatResult r = formatter.format(input)
        if (r.skipped) {
            System.err.println("groovyfmt: ${r.skipReason}")
            System.exit(2)
        }
        if (stats) {
            System.err.println(r.stats.summary())
        }
        print r.output
        System.exit(0)
    }

    private static void printRules() {
        def registry = RuleRegistry.defaults()
        println '水平空白规则（每条规则独占它认领的 Slot，注册期校验冲突）:'
        registry.spacingRules().each { SpacingRule r ->
            printf('  %-34s %s%n', r.id, r.slots)
        }
        println ''
        println '缩进规则（列数增量求和）:'
        registry.indentRules().each { IndentRule r ->
            printf('  %-34s%n', r.id)
        }
        println ''
        println "共 ${registry.spacingRules().size()} 条水平规则 + ${registry.indentRules().size()} 条缩进规则，" +
        "覆盖 ${registry.coveredSlots().size()}/${Slot.values().length} 个 Slot。"
    }

    private static void printSlots() {
        def registry = RuleRegistry.defaults()
        println '尚未被任何规则覆盖的 Slot（这些位置一律保留原文）:'
        registry.uncoveredSlots().each { Slot s ->
            println "  $s"
        }
    }

    /** 极简 LCS 差异输出。行数很大时退化为只报第一处差异，避免 O(n*m) 内存。 */
    @SuppressWarnings('NestedForLoop')   // LCS 动态规划，嵌套循环是算法本身
    private static void printDiff(String name, String a, String b) {
        println "--- $name"
        println "+++ $name (格式化后)"
        String[] x = a.split('\n', -1)
        String[] y = b.split('\n', -1)
        if ((long) x.length * y.length > 4_000_000L) {
            println '(文件较大，省略逐行差异)'
            return
        }
        int n = x.length
        int m = y.length
        int[][] dp = new int[n + 1][m + 1]
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = x[i] == y[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
            }
        }
        int i = 0
        int j = 0
        while (i < n && j < m) {
            if (x[i] == y[j]) {
                i++
                j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                println "- ${x[i++]}"
            } else {
                println "+ ${y[j++]}"
            }
        }
        while (i < n) {
            println "- ${x[i++]}"
        }
        while (j < m) {
            println "+ ${y[j++]}"
        }
    }

    /** 供测试复用的语法检查。 */
    static boolean parses(String source) {
        try {
            Cst cst = CstBuilder.build(source)
            cst.tokenCount >= 0
        } catch (GroovySyntaxException ignored) {
            false
        }
    }

}
