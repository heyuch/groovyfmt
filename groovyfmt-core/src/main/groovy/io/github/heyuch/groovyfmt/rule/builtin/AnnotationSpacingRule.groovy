package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/** 注解的 @ 紧贴名字：@Override、@CompileStatic */
@CompileStatic
class AnnotationSpacingRule extends ConstantSpacingRule {

    AnnotationSpacingRule() {
        super('spacing.annotation', Spacing.NONE, Slot.AFTER_ANNOTATION_AT)
    }

}
