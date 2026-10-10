package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/** 点链紧贴：a.b、a?.b、a*.b、a::b */
@CompileStatic
class DotChainSpacingRule extends ConstantSpacingRule {

    DotChainSpacingRule() {
        super('spacing.dot', Spacing.NONE, Slot.BEFORE_DOT, Slot.AFTER_DOT)
    }

}
