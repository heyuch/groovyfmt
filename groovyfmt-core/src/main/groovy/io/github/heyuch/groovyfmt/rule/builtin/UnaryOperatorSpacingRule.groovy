package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/**
 * 一元运算符紧贴操作数：!x、~x、-1、i++
 *
 * <p>{@link Slot#BEFORE_UNARY_OP} 管后缀 ++/-- 之前，{@link Slot#AFTER_UNARY_OP} 管前缀之后。
 */
@CompileStatic
class UnaryOperatorSpacingRule extends ConstantSpacingRule {

    UnaryOperatorSpacingRule() {
        super('spacing.unary-op', Spacing.NONE, Slot.BEFORE_UNARY_OP, Slot.AFTER_UNARY_OP)
    }

}
