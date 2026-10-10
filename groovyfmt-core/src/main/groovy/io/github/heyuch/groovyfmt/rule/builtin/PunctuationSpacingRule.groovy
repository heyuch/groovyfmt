package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.MappedSpacingRule

/** 标点：逗号/分号后加空格、其前不加；括号与方括号内部两侧不留空格。 */
@CompileStatic
class PunctuationSpacingRule extends MappedSpacingRule {

    PunctuationSpacingRule() {
        super('spacing.punctuation', slotMapping())
    }

    private static Map<Slot, Spacing> slotMapping() {
        Map<Slot, Spacing> m = [:]
        m[Slot.AFTER_COMMA] = Spacing.SPACE
        m[Slot.AFTER_SEMICOLON] = Spacing.SPACE

        m[Slot.BEFORE_COMMA] = Spacing.NONE
        m[Slot.BEFORE_SEMICOLON] = Spacing.NONE
        m[Slot.AFTER_LPAREN] = Spacing.NONE
        m[Slot.BEFORE_RPAREN] = Spacing.NONE
        m[Slot.AFTER_LBRACK] = Spacing.NONE
        m[Slot.BEFORE_RBRACK] = Spacing.NONE
        m
    }

}
