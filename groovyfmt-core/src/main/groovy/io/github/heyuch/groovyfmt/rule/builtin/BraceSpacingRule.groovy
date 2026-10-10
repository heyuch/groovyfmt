package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/**
 * 花括号两侧一个空格：list.each { }、if (x) { }、{ }
 *
 * <p>只会在<b>同一行内</b>生效 —— 跨行的花括号是垂直结构，由缩进层处理。
 */
@CompileStatic
class BraceSpacingRule extends ConstantSpacingRule {

    BraceSpacingRule() {
        super('spacing.brace', Spacing.SPACE, Slot.BEFORE_LBRACE, Slot.AFTER_LBRACE, Slot.BEFORE_RBRACE)
    }

}
