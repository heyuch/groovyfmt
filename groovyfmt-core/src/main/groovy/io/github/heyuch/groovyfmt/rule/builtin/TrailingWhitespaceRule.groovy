package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/** 去掉行尾空白。 */
@CompileStatic
class TrailingWhitespaceRule extends ConstantSpacingRule {

    TrailingWhitespaceRule() {
        super('spacing.trailing-whitespace', Spacing.NONE, Slot.TRAILING_WHITESPACE)
    }

}
