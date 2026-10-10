package io.github.heyuch.groovyfmt.layout

import groovy.transform.CompileStatic

import org.apache.groovy.parser.antlr4.GroovyLexer

import io.github.heyuch.groovyfmt.annotate.SlotClassifier
import io.github.heyuch.groovyfmt.annotate.TokenAnnotator.Annotated
import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.Gap
import io.github.heyuch.groovyfmt.model.FormatStyle
import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.model.TokenRole
import io.github.heyuch.groovyfmt.rule.GapContext
import io.github.heyuch.groovyfmt.rule.IndentContext
import io.github.heyuch.groovyfmt.rule.IndentRule
import io.github.heyuch.groovyfmt.rule.RuleRegistry
import io.github.heyuch.groovyfmt.rule.SpacingRule

/**
 * 布局仲裁者（Mediator）：唯一知道"最终每个 gap 长什么样"的地方。
 *
 * <p>它做两件事，互不干扰：
 * <ul>
 *   <li><b>垂直</b>（行首缩进）：把所有 {@link IndentRule} 的列数增量求和；</li>
 *   <li><b>水平</b>（行内空白）：把 gap 分类到 Slot，交给该 Slot 的唯一 owner 规则。</li>
 * </ul>
 *
 * <p>重要：本工具<b>绝不新增或删除换行</b> —— 换行是语法级信息（Groovy 的 NL token），
 * 动它就可能改变语义。所以这里只重写"水平空白"和"行首缩进"。
 */
@CompileStatic
class LayoutResolver {

    private LayoutResolver() {
    }

    static List<String> resolve(Cst cst, Annotated annotated, RuleRegistry registry,
            FormatStyle style, Stats stats) {
        int n = cst.tokenCount
        def out = new ArrayList<String>(n + 1)
        for (int g = 0; g <= n; g++) {
            out << resolveGap(cst, annotated, registry, style, g, stats)
        }
        out
    }

    @SuppressWarnings('ParameterCount')
    private static String resolveGap(Cst cst, Annotated annotated, RuleRegistry registry,
            FormatStyle style, int g, Stats stats) {
        Gap gap = cst.gaps[g]
        String original = gap.original

        // 只处理纯水平空白。含行续接符 \ 的 gap、以及 shebang，一律原样保留。
        if (!gap.horizontal) {
            return original
        }

        int n = cst.tokenCount
        if (g == 0 || g == n) {
            return ''                          // 文件首尾的空白：清掉
        }

        // 行尾空白优先于缩进：空行（连续两个换行 token 夹着的空白）也走这里，
        // 否则空行会被当成"某行的缩进"而被填上空格。
        if (annotated.isNewline(g)) {
            stats.horizontalGaps++
            return applySlot(cst, annotated, registry, style, g, Slot.TRAILING_WHITESPACE, original, stats)
        }

        if (annotated.isNewline(g - 1)) {
            stats.verticalGaps++
            String indent = indentFor(cst, annotated, registry, style, g)
            if (indent != original) {
                stats.changed++
            }
            return indent
        }

        stats.horizontalGaps++
        Slot slot = SlotClassifier.classify(cst, annotated, g)
        if (slot == null) {
            stats.unclassified++
            return original                    // fail-closed：没看懂就不动
        }
        applySlot(cst, annotated, registry, style, g, slot, original, stats)
    }

    @SuppressWarnings('ParameterCount')
    private static String applySlot(Cst cst, Annotated annotated, RuleRegistry registry,
            FormatStyle style, int g, Slot slot,
            String original, Stats stats) {
        SpacingRule rule = registry.owner(slot)
        if (rule == null) {
            stats.unclassified++
            return original                    // 没有规则认领这个 Slot
        }
        stats.classified++

        Spacing decision = rule.decide(slot, new GapContext(cst, annotated, g, style))
        String result = switch (decision) {
            case Spacing.NONE -> ''
            case Spacing.SPACE -> ' '
            case Spacing.PRESERVE -> original
        }
        if (result != original) {
            stats.changed++
        }
        result
    }

    private static String indentFor(Cst cst, Annotated annotated, RuleRegistry registry,
            FormatStyle style, int lineStartToken) {
        if (lineStartToken >= cst.tokenCount) {
            return ''
        }
        def ctx = new IndentContext(cst, annotated, lineStartToken, style)
        int columns = 0
        registry.indentRules().each { IndentRule rule ->
            columns += rule.delta(ctx)
        }
        if (columns <= 0) {
            // 安全兜底：顶层续行（没有任何括号包着，例如 "a +\n    b"）保留原缩进，
            // 否则会把它拍平到第 0 列。后续可以把它提升成一条正式的 IndentRule。
            if (isTopLevelContinuation(annotated, lineStartToken)) {
                return ctx.originalIndent()
            }
            return ''
        }
        ' '.repeat(columns)
    }

    /** 该行是不是上一行表达式的续行（而不是一条新语句）。 */
    private static boolean isTopLevelContinuation(Annotated annotated, int lineStartToken) {
        int prev = annotated.prevCode(lineStartToken - 1)
        if (prev >= 0) {
            TokenRole role = annotated.role(prev)
            if (role == TokenRole.BINARY_OP || role == TokenRole.UNARY_PREFIX
                    || role == TokenRole.COMMA || role == TokenRole.DOT_LIKE
                    || role == TokenRole.COLON_TERNARY || role == TokenRole.COLON_NAMED_OR_MAP) {
                return true
            }
            int type = annotated.type(prev)
            if (type == GroovyLexer.LPAREN || type == GroovyLexer.LBRACK) {
                return true
            }
        }
        TokenRole first = annotated.role(lineStartToken)
        first == TokenRole.DOT_LIKE || first == TokenRole.BINARY_OP
    }

}
