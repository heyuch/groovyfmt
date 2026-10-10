package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.annotate.TokenAnnotator.Annotated
import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstToken
import io.github.heyuch.groovyfmt.model.FormatStyle
import io.github.heyuch.groovyfmt.model.TokenRole

/**
 * 规则看到的全部上下文。刻意只暴露"语义信息"，不暴露原始整串文本 ——
 * 避免规则去做文本层面的 hack。
 */
@CompileStatic
class GapContext {

    final Cst cst
    final Annotated annotated
    final int gapIndex
    final FormatStyle style

    /** 前一个 token（可能是注释或换行）。 */
    final CstToken prev
    /** 后一个 token。 */
    final CstToken next

    final TokenRole prevRole
    final TokenRole nextRole

    GapContext(Cst cst, Annotated annotated, int gapIndex, FormatStyle style) {
        this.cst = cst
        this.annotated = annotated
        this.gapIndex = gapIndex
        this.style = style
        this.prev = gapIndex > 0 ? cst.tokens[gapIndex - 1] : null
        this.next = gapIndex < cst.tokenCount ? cst.tokens[gapIndex] : null
        this.prevRole = annotated.role(gapIndex - 1)
        this.nextRole = annotated.role(gapIndex)
    }

    /** 这个 gap 的原始空白。 */
    String original() {
        cst.gaps[gapIndex].original
    }

    /** 该 gap 是否处在文件第一行之前。 */
    boolean isFileStart() {
        gapIndex == 0
    }

    /** 该 gap 是否为行尾空白。 */
    boolean isTrailingWhitespace() {
        next != null && annotated.isNewline(gapIndex)
    }

}
