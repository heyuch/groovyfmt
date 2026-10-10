package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/** 控制流关键字的左括号之前留一个空格：if (x)、for (i in 0..9) */
@CompileStatic
class KeywordParenSpacingRule extends ConstantSpacingRule {

    KeywordParenSpacingRule() {
        super('spacing.keyword-paren', Spacing.SPACE, Slot.BEFORE_LPAREN_KEYWORD)
    }

}
