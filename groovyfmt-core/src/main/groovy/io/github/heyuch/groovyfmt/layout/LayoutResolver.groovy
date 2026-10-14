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
        // 按行顺序把算好的缩进记下来 —— 锚点模型要看"锚点行"的缩进，
        // 而 resolve 本来就是按 token/行顺序走的，所以边算边记即可。
        int[] lineIndent = new int[annotated.lineCount()]
        for (int g = 0; g <= n; g++) {
            out << resolveGap(cst, annotated, registry, style, g, stats, lineIndent)
        }
        out
    }

    @SuppressWarnings('ParameterCount')
    private static String resolveGap(Cst cst, Annotated annotated, RuleRegistry registry,
            FormatStyle style, int g, Stats stats, int[] lineIndent) {
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
            String indent = indentFor(cst, annotated, registry, style, g, lineIndent)
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

    @SuppressWarnings('ParameterCount')
    private static String indentFor(Cst cst, Annotated annotated, RuleRegistry registry,
            FormatStyle style, int lineStartToken, int[] lineIndent) {
        if (lineStartToken >= cst.tokenCount) {
            return ''
        }
        def ctx = new IndentContext(cst, annotated, lineStartToken, style)
        int line = annotated.lineOf(lineStartToken)

        Integer anchored = anchoredIndent(annotated, style, lineStartToken, lineIndent)
        int columns
        if (anchored != null) {
            columns = anchored
        } else {
            columns = 0
            registry.indentRules().each { IndentRule rule ->
                columns += rule.delta(ctx)
            }
        }
        if (columns <= 0) {
            // 安全兜底：顶层续行（没有任何括号包着，例如 "a +\n    b"）保留原缩进，
            // 否则会把它拍平到第 0 列。后续可以把它提升成一条正式的 IndentRule。
            if (isTopLevelContinuation(annotated, lineStartToken)) {
                String original = ctx.originalIndent()
                lineIndent[line] = original.length()
                return original
            }
            lineIndent[line] = 0
            return ''
        }
        lineIndent[line] = columns
        ' '.repeat(columns)
    }

    /**
     * 锚点模型：本行缩进 = 锚点行的缩进 + 一次增量。返回 {@code null} 表示没有锚点，
     * 交给原来的"规则增量求和"逻辑（顶层、以及还没建模的形态）。
     */
    private static Integer anchoredIndent(Annotated annotated, FormatStyle style,
            int lineStartToken, int[] lineIndent) {
        boolean closer = startsWithCloser(annotated, lineStartToken)
        int opener = annotated.openerBefore(lineStartToken)

        // ① 闭合行：对齐"打开被闭合括号的那一行"。
        //    必须排在最前 —— 否则 case 规则会把 switch 的 } 也当成 case 体。
        if (closer) {
            if (opener < 0) {
                return null
            }
            boolean braceOpener = annotated.topBefore(lineStartToken) == ('{' as char)
            int closeAnchor = braceOpener ? bodyAnchorLine(annotated, opener) : annotated.lineOf(opener)
            return lineIndent[closeAnchor]
        }

        // ② 续行：以 . / 二元运算符 / 三元 ? : 开头的行是"语句级续行"，
        //    锚点应是语句首行，而不是外面那个块 —— 否则折行的三元、&& 链、
        //    点链都会只拿到块缩进（= 语句自身的缩进），续行缩进整个丢失。
        if (isContinuationStart(annotated, lineStartToken)) {
            int line = annotated.lineOf(lineStartToken)
            int k = line - 1
            while (k >= 0 && isContinuationStart(annotated, firstCodeOfLine(annotated, k))) {
                k--
            }
            return k >= 0 ? lineIndent[k] + style.continuationIndent : null
        }

        // ③ case 体：标签自身【不】走这条（标签走下面的括号锚点，正好是 switch 行 +4），
        //    否则 case 标签会互相链式递增。
        int line = annotated.lineOf(lineStartToken)
        if (!isCaseLabelLine(annotated, line)) {
            int caseLine = caseBodyAnchor(annotated, lineStartToken)
            if (caseLine >= 0) {
                return lineIndent[caseLine] + style.indentWidth
            }
        }

        // ④ 括号锚点：最内层未闭合括号的打开行 + 一次增量
        if (opener >= 0) {
            boolean brace = annotated.topBefore(lineStartToken) == ('{' as char)
            int anchorLine = annotated.lineOf(opener)
            // 花括号体的锚点校正：若"锚点行开头处未闭合的那个括号"在 { 之前就已经闭合了，
            // 说明这个 { 是【跨行声明的体】（典型：多行参数列表的方法），
            // 锚点必须回到打开那个括号的行（= 声明起始行），而不是 { 所在的续行 ——
            // 否则方法体会比声明起始行深两级（CodeNarc 的 Indentation 会判它深 8 列）。
            // 反例：闭包做 map 值时，{ 之前那个 [ 仍然开着，此时锚点就该留在 { 所在行。
            if (brace) {
                anchorLine = bodyAnchorLine(annotated, opener)
            }
            return lineIndent[anchorLine] + (brace ? style.indentWidth : style.continuationIndent)
        }
        null
    }

    /**
     * 花括号体的真正锚点行。
     *
     * <p>通常就是 {@code {} 所在行。但如果那一行的<b>行首括号在 {@code {} 之前就已经被弹出</b>
     * （即 {@code {} 处的最内层括号比行首那个更早），说明这是一个【跨行声明的体】——
     * 典型是多行参数列表的方法声明。此时锚点必须回到打开那个括号的行（= 声明起始行），
     * 否则方法体会比声明起始行深两级（CodeNarc 的 Indentation 会判它深 8 列）。
     *
     * <p>判据必须是“更早”而不是“不同”：{@code f(new Runnable() {} 里，
     * {@code {} 处的最内层是 {@code f(}，它比行首的方法体括号更【晚】，此时不该回退。
     */
    private static int bodyAnchorLine(Annotated annotated, int braceToken) {
        int braceLine = annotated.lineOf(braceToken)
        int lineStartOpener = annotated.openerBefore(annotated.firstTokenOfLine(braceLine))
        if (lineStartOpener >= 0 && annotated.openerBefore(braceToken) < lineStartOpener) {
            return annotated.lineOf(lineStartOpener)
        }
        braceLine
    }

    /**
     * 该 token 是否开始"上一行表达式的续行"。
     *
     * <p>包括点链（{@code .foo()}）、二元运算符（{@code && x}、{@code + 1}），
     * 以及折行三元里的 {@code ?} 与 {@code :}。这些行都不是新语句，
     * 缩进应当是"语句首行 + 一次续行缩进"，而不是外面那个块的缩进。
     */
    private static boolean isContinuationStart(Annotated annotated, int token) {
        TokenRole role = annotated.role(token)
        role == TokenRole.DOT_LIKE || role == TokenRole.BINARY_OP
        || role == TokenRole.COLON_TERNARY || role == TokenRole.UNARY_PREFIX
    }

    /** 该行的第一个代码 token（跳过空行与纯注释行）。 */
    private static int firstCodeOfLine(Annotated annotated, int line) {
        int i = annotated.firstTokenOfLine(line)
        while (i < annotated.size() && (annotated.isComment(i) || annotated.isNewline(i))) {
            i++
        }
        i
    }

    /** 该行是否是 case / default 标签行。 */
    private static boolean isCaseLabelLine(Annotated annotated, int line) {
        int t = firstCodeOfLine(annotated, line)
        if (t >= annotated.size()) {
            return false
        }
        int type = annotated.type(t)
        type == GroovyLexer.CASE || type == GroovyLexer.DEFAULT
    }

    /** 本行是否是某个 case/default 体的行；是则返回该标签所在行，否则 -1。 */
    private static int caseBodyAnchor(Annotated annotated, int lineStartToken) {
        int line = annotated.lineOf(lineStartToken)
        int depth = annotated.braceDepthBefore(lineStartToken)
        for (int k = line - 1; k >= 0; k--) {
            int first = annotated.firstTokenOfLine(k)
            int d = annotated.braceDepthBefore(first)
            if (d < depth) {
                return -1                                     // 已经走出这个块
            }
            if (d > depth) {
                continue                                      // 更深层的行，跳过
            }
            int type = annotated.type(first)
            if (type == GroovyLexer.CASE || type == GroovyLexer.DEFAULT) {
                return k
            }
        }
        -1
    }

    private static boolean startsWithCloser(Annotated annotated, int lineStartToken) {
        int type = annotated.type(lineStartToken)
        type == GroovyLexer.RBRACE || type == GroovyLexer.RPAREN || type == GroovyLexer.RBRACK
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
