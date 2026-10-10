package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/** 二元运算符两侧各一个空格：a + b、x = 1、a == b */
@CompileStatic
class BinaryOperatorSpacingRule extends ConstantSpacingRule {

    BinaryOperatorSpacingRule() {
        super('spacing.binary-op', Spacing.SPACE, Slot.BEFORE_BINARY_OP, Slot.AFTER_BINARY_OP)
    }

}
