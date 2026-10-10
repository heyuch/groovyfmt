package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/**
 * 关键字之后一个空格：def x、return y、new Foo、class A
 *
 * <p>后接 ( / [ / : / ; 等情形在分类阶段就被更高优先级的 Slot 截走了，所以不需要写特例。
 */
@CompileStatic
class KeywordSpacingRule extends ConstantSpacingRule {

    KeywordSpacingRule() {
        super('spacing.keyword', Spacing.SPACE, Slot.AFTER_KEYWORD)
    }

}
