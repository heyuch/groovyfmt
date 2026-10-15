package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing
import io.github.heyuch.groovyfmt.rule.MappedSpacingRule

/**
 * 冒号对齐。
 *
 * <p>Map 字面量 / 具名参数：[k:v]、foo(a:1) —— 冒号两侧都不加空格。
 * 实测：CodeNarc 的 SpaceAroundMapEntryColon 默认只接受 [k:v]，
 * [k: v] / [k :v] / [k : v] 都报违规；具名参数同理。
 * 我们的默认规则对齐 CodeNarc 默认。
 * 三元表达式：a ? b : c —— 两侧都加。
 *
 * <p>case / default / label / assert 消息的冒号根本不会被分类到这里
 * （{@code SlotClassifier} 返回 null），所以不会被误改。
 */
@CompileStatic
class ColonSpacingRule extends MappedSpacingRule {

    ColonSpacingRule() {
        super('spacing.colon', slotMapping())
    }

    private static Map<Slot, Spacing> slotMapping() {
        Map<Slot, Spacing> m = [:]
        m[Slot.BEFORE_COLON] = Spacing.NONE
        m[Slot.AFTER_COLON] = Spacing.NONE
        m[Slot.AFTER_NAMED_ARG_COLON] = Spacing.SPACE
        m[Slot.BEFORE_TERNARY_COLON] = Spacing.SPACE
        m[Slot.AFTER_TERNARY_COLON] = Spacing.SPACE
        // for (X x : xs) —— 两侧都要空格
        m[Slot.BEFORE_FOR_IN_COLON] = Spacing.SPACE
        m[Slot.AFTER_FOR_IN_COLON] = Spacing.SPACE
        m
    }

}
