package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

import org.apache.groovy.parser.antlr4.GroovyLexer

import io.github.heyuch.groovyfmt.annotate.TokenAnnotator.Annotated
import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstToken
import io.github.heyuch.groovyfmt.model.FormatStyle

/** 一个"行首 token"的缩进上下文。 */
@CompileStatic
class IndentContext {

    final Cst cst
    final Annotated annotated
    final FormatStyle style

    /** 该行第一个 token 的下标。 */
    final int lineStartToken

    IndentContext(Cst cst, Annotated annotated, int lineStartToken, FormatStyle style) {
        this.cst = cst
        this.annotated = annotated
        this.lineStartToken = lineStartToken
        this.style = style
    }

    /** 进入本行时已打开的 {@code {} 层数。 */
    int braceDepth() {
        annotated.braceDepthBefore(lineStartToken)
    }

    /** 进入本行时已打开的 {@code (} / {@code [} 层数。 */
    int parenDepth() {
        annotated.parenDepthBefore(lineStartToken)
    }

    /** 本行所在的行号（从 0 开始）。 */
    int line() {
        annotated.lineOf(lineStartToken)
    }

    /**
     * 本行缩进的<b>锚点行</b>：打开"最内层未闭合括号"的那一行；没有则 -1。
     *
     * <p>锚点模型：本行缩进 = 锚点行的缩进 + 一次增量（{@code {} → 块缩进，
     * {@code (} / {@code [} → 续行缩进）。这与"从第 0 列数几层"在花括号/括号
     * 嵌在续行里时会给出不同结果，而对普通块体两者一致。
     */
    int anchorLine() {
        int opener = annotated.openerBefore(lineStartToken)
        opener < 0 ? -1 : annotated.lineOf(opener)
    }

    /** 进入本行时，位于最内层未闭合 {@code {} 之后的 {@code (} / {@code [} 层数。 */
    int parenDepthSinceBrace() {
        annotated.parenDepthSinceBraceBefore(lineStartToken)
    }

    /** 本行所在的最内层括号类型：{@code {} {@code (} {@code [} 或 0。 */
    char enclosing() {
        annotated.topBefore(lineStartToken)
    }

    CstToken first() {
        cst.tokens[lineStartToken]
    }

    int firstType() {
        first().type
    }

    /** 本行是否以闭合括号开头。 */
    boolean startsWithCloser() {
        int t = firstType()
        t == GroovyLexer.RBRACE || t == GroovyLexer.RPAREN || t == GroovyLexer.RBRACK
    }

    /** 该行原本的缩进空白。 */
    String originalIndent() {
        cst.gaps[lineStartToken].original
    }

}
