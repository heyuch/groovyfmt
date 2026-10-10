package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/** 调用/声明的左括号紧贴前面：foo(x)、def foo(x)、new Foo(x) */
@CompileStatic
class CallParenSpacingRule extends ConstantSpacingRule {

    CallParenSpacingRule() {
        super('spacing.call-paren', Spacing.NONE, Slot.BEFORE_LPAREN_NO_SPACE)
    }

}
