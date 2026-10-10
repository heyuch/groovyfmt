package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing

/**
 * 对认领的所有 Slot 给出同一个决策。
 *
 * <p>绝大多数规则只需要这么点代码 —— 这正是"加一条规则 = 加一个 10 行的类"的体现。
 */
@CompileStatic
class ConstantSpacingRule implements SpacingRule {

    final String id
    final Set<Slot> slots
    private final Spacing spacing

    protected ConstantSpacingRule(String id, Spacing spacing, Slot... slots) {
        this.id = id
        this.spacing = spacing
        this.slots = Collections.unmodifiableSet(
                slots.length == 0 ? EnumSet.noneOf(Slot) : EnumSet.copyOf(slots as List<Slot>))
    }

    @Override
    Spacing decide(Slot slot, GapContext ctx) {
        spacing
    }

    @Override
    String toString() {
        id
    }

}
