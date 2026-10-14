package io.github.heyuch.groovyfmt.pipeline

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.annotate.ParseContextIndex
import io.github.heyuch.groovyfmt.annotate.TokenAnnotator
import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstBuilder
import io.github.heyuch.groovyfmt.cst.GroovySyntaxException
import io.github.heyuch.groovyfmt.layout.BlankLineNormalizer
import io.github.heyuch.groovyfmt.layout.LayoutResolver
import io.github.heyuch.groovyfmt.layout.Stats
import io.github.heyuch.groovyfmt.model.FormatStyle
import io.github.heyuch.groovyfmt.rule.RuleRegistry

/**
 * 完整链路 + 三道安全闸门。
 *
 * <pre>
 *   输入 → [管道前置步骤] → CstBuilder(词法监听器 + ANTLR 语法树 + Groovy 编译器 + 无损断言)
 *        → TokenAnnotator → LayoutResolver(规则) → Cst.render
 *        → [闸门1] 重新词法，token 序列必须完全一致
 *        → [闸门2] 重新解析，语法树形状必须完全一致
 *        → 输出
 * </pre>
 *
 * <p><b>为什么"token 序列不变"还不是完备保证</b>：ANTLR 的词法错误恢复会静默跳过无法识别的
 * 字符，所以两份文本可以有相同的 token 序列而其中一份非法。必须<b>对输入和输出各自</b>
 * 独立做完整语法检查（含真正的 Groovy 编译器），再叠上 token 序列比对。
 *
 * <p>任何一道闸门不过 → <b>原样返回输入</b>并标记 {@code skipped}。
 * 也就是说这个工具宁可什么都不做，也绝不改坏代码。
 */
@CompileStatic
class Formatter {

    private final FormatStyle style
    private final RuleRegistry registry
    private final List<FormatterStep> preSteps

    Formatter(FormatStyle style, RuleRegistry registry, List<FormatterStep> preSteps) {
        this.style = style
        this.registry = registry
        this.preSteps = List.copyOf(preSteps)
    }

    static Formatter defaults() {
        new Formatter(FormatStyle.DEFAULT, RuleRegistry.defaults(), [new FinalNewlineStep()])
    }

    FormatResult format(String input) {
        def stats = new Stats()
        int ruleCount = registry.spacingRules().size()

        String pre = input
        preSteps.each { FormatterStep step ->
            pre = step.apply(pre)
        }

        Cst cst
        try {
            cst = CstBuilder.build(pre)
        } catch (GroovySyntaxException e) {
            return fallback(input, "语法不通过，原样返回: ${first(e.errors)}".toString(), stats, ruleCount)
        }

        if (cst.tokenCount == 0) {
            return new FormatResult(pre, pre != input, false, null, stats, ruleCount)
        }

        // 语法上下文索引（parse tree）：按字符偏移回答"这里在哪个构造里"。
        // 必须在注解之前建好 —— 角色判定（如泛型通配符）也要用它。
        // 构建失败返回 null，各处会自动退回词法判据。
        ParseContextIndex index = ParseContextIndex.build(pre)
        def annotated = TokenAnnotator.annotate(cst, index)
        List<String> gaps = LayoutResolver.resolve(cst, annotated, registry, style, stats, index)

        // 空行规范化：这是唯一会增删 token 的环节（插/删 NL token）。
        // 关掉这个能力（FormatStyle.withoutBlankLineAdjustment）就回到"绝不增删 token"的保守行为。
        String out
        if (style.blankLineAdjustmentEnabled) {
            def normalized = BlankLineNormalizer.normalize(cst, annotated, gaps, style, index)
            out = cst.render(normalized.tokens, normalized.gaps)
        } else {
            out = cst.render(gaps)
        }

        Cst outCst
        try {
            outCst = CstBuilder.build(out)
        } catch (GroovySyntaxException e) {
            return fallback(input, "格式化后语法不通过，已回退: ${first(e.errors)}".toString(), stats, ruleCount)
        }

        // 闸门①：规范化 token 序列必须完全一致
        // （规范化 = 把连续换行折叠成一个；所以唯一允许的差异就是空行数量）
        if (cst.canonicalTokenFingerprint() != outCst.canonicalTokenFingerprint()) {
            String diff = cst.firstTokenDifference(outCst)
            return fallback(input, "格式化改变了 token 序列，已回退: $diff".toString(), stats, ruleCount)
        }

        // 闸门②：忽略空行之后的语法树必须一致
        if (style.verifyParseTree) {
            String inShape = CstBuilder.treeShapeOnly(cst.canonicalText())
            String outShape = CstBuilder.treeShapeOnly(outCst.canonicalText())
            if (inShape != outShape) {
                return fallback(input, '格式化改变了语法树，已回退', stats, ruleCount)
            }
        }

        new FormatResult(out, out != input, false, null, stats, ruleCount)
    }

    private static FormatResult fallback(String input, String reason, Stats stats, int ruleCount) {
        new FormatResult(input, false, true, reason, stats, ruleCount)
    }

    private static String first(List<String> errors) {
        errors ? errors[0] : '(未知)'
    }

}
