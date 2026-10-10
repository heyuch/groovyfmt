package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing

/**
 * 认领多个 Slot、但每个 Slot 决策不同的规则（例如"逗号后加空格、逗号前不加"）。
 *
 * <p>仍然保持"每个 Slot 只有一个 owner"的契约 —— 只是同一个 owner 管了一族相关位置。
 */
@CompileStatic
class MappedSpacingRule implements SpacingRule {

    final String id
    private final Map<Slot, Spacing> mapping

    protected MappedSpacingRule(String id, Map<Slot, Spacing> mapping) {
        this.id = id
        this.mapping = Collections.unmodifiableMap(new EnumMap<Slot, Spacing>(mapping))
    }

    @Override
    Set<Slot> getSlots() {
        mapping.keySet()
    }

    @Override
    Spacing decide(Slot slot, GapContext ctx) {
        mapping.getOrDefault(slot, Spacing.PRESERVE)
    }

    @Override
    String toString() {
        id
    }

}
