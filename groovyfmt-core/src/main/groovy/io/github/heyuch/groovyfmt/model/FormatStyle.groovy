package io.github.heyuch.groovyfmt.model

import groovy.transform.CompileStatic

/**
 * 格式风格。刻意只有很少的旋钮 —— gofmt 的成功很大程度上来自"几乎没有配置"。
 *
 * <p>不可变，用 {@code withXxx} 派生出新实例，方便规则只读消费。
 * （不用 {@code @Immutable}：它不允许自定义构造器，而这里需要构造期校验。）
 *
 * <p>空行相关的三个旋钮不是审美偏好，而是<b>为了和 CodeNarc 的格式化规则保持一致</b>：
 * <ul>
 *   <li>{@code maxBlankLines} → {@code ConsecutiveBlankLines}</li>
 *   <li>{@code blankLinesAfterClassOpen} → {@code ClassStartsWithBlankLine}</li>
 *   <li>{@code blankLinesBeforeClassClose} → {@code ClassEndsWithBlankLine}</li>
 * </ul>
 * 设成 0 就退回"不碰空行"的行为。
 */
@CompileStatic
class FormatStyle {

    static final FormatStyle DEFAULT = new FormatStyle(4, 8, 1, 1, 1, true)

    /** 块缩进宽度。 */
    int indentWidth
    /** 括号/方括号续行的缩进宽度。 */
    int continuationIndent
    /** 任意位置允许的最大连续空行数。 */
    int maxBlankLines
    /** 类体左花括号之后至少留几个空行。 */
    int blankLinesAfterClassOpen
    /** 类体右花括号之前至少留几个空行。 */
    int blankLinesBeforeClassClose
    /** 是否做额外的语法树等价比对（在规范化 token 序列之上，保险，可关）。 */
    boolean verifyParseTree

    @SuppressWarnings('ParameterCount')   // 不可变值对象，字段与参数一一对应
    FormatStyle(int indentWidth, int continuationIndent, int maxBlankLines,
            int blankLinesAfterClassOpen, int blankLinesBeforeClassClose,
            boolean verifyParseTree) {
        [indentWidth, continuationIndent, maxBlankLines,
                blankLinesAfterClassOpen, blankLinesBeforeClassClose].each { int v ->
            if (v < 0) {
                throw new IllegalArgumentException('缩进宽度与空行数不能为负')
            }
        }
        this.indentWidth = indentWidth
        this.continuationIndent = continuationIndent
        this.maxBlankLines = maxBlankLines
        this.blankLinesAfterClassOpen = blankLinesAfterClassOpen
        this.blankLinesBeforeClassClose = blankLinesBeforeClassClose
        this.verifyParseTree = verifyParseTree
    }

    FormatStyle withIndentWidth(int v) {
        new FormatStyle(v, continuationIndent, maxBlankLines,
                blankLinesAfterClassOpen, blankLinesBeforeClassClose, verifyParseTree)
    }

    FormatStyle withContinuationIndent(int v) {
        new FormatStyle(indentWidth, v, maxBlankLines,
                blankLinesAfterClassOpen, blankLinesBeforeClassClose, verifyParseTree)
    }

    /** 关掉所有空行调整 —— 回到"绝不增删 token"的保守行为。 */
    FormatStyle withoutBlankLineAdjustment() {
        new FormatStyle(indentWidth, continuationIndent, 0, 0, 0, verifyParseTree)
    }

    FormatStyle withMaxBlankLines(int v) {
        new FormatStyle(indentWidth, continuationIndent, v,
                blankLinesAfterClassOpen, blankLinesBeforeClassClose, verifyParseTree)
    }

    FormatStyle withVerifyParseTree(boolean v) {
        new FormatStyle(indentWidth, continuationIndent, maxBlankLines,
                blankLinesAfterClassOpen, blankLinesBeforeClassClose, v)
    }

    /** 是否启用了空行调整（会增删 NL token）。 */
    boolean isBlankLineAdjustmentEnabled() {
        maxBlankLines > 0 || blankLinesAfterClassOpen > 0 || blankLinesBeforeClassClose > 0
    }

    @Override
    String toString() {
        'FormatStyle{indent=' + indentWidth + ', continuation=' + continuationIndent +
        ', maxBlankLines=' + maxBlankLines +
        ', afterClassOpen=' + blankLinesAfterClassOpen +
        ', beforeClassClose=' + blankLinesBeforeClassClose +
        ', verifyParseTree=' + verifyParseTree + '}'
    }

}
