package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.ConstantSpacingRule

/**
 * 泛型通配符：{@code Class<?>}、{@code Map<String, ?>}、{@code Class<? extends T>}。
 *
 * <p>留着的是"<b>?</b> 之前不留空格"这一条 —— 于是 {@code Class< ? extends T>} 会被收成
 * {@code Class<? extends T>}。
 *
 * <p>注意这里【只管】 {@code ?} 之前：{@code Map<String, ?>} 里那个空格属于 {@code AFTER_COMMA}，
 * 由 {@link PunctuationSpacingRule} 负责，分类器里逗号分支优先级更高，所以不会被这条覆盖。
 */
@CompileStatic
class WildcardSpacingRule extends ConstantSpacingRule {

    WildcardSpacingRule() {
        super('spacing.wildcard', Spacing.NONE, Slot.BEFORE_WILDCARD)
    }

}
