package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.rule.builtin.AnnotationSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.BinaryOperatorSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.BlockIndentRule
import io.github.heyuch.groovyfmt.rule.builtin.BraceSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.CallParenSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.ClosingBracketDedentRule
import io.github.heyuch.groovyfmt.rule.builtin.ColonSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.ContinuationIndentRule
import io.github.heyuch.groovyfmt.rule.builtin.DotChainSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.KeywordParenSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.KeywordSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.PunctuationSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.TrailingWhitespaceRule
import io.github.heyuch.groovyfmt.rule.builtin.UnaryOperatorSpacingRule
import io.github.heyuch.groovyfmt.rule.builtin.WildcardSpacingRule

/**
 * 规则注册表 —— 模块化的落点。
 *
 * <p>核心契约：<b>一个 Slot 只允许一个 owner</b>。冲突在<b>注册期</b>就抛异常，
 * 而不是靠执行顺序碰运气。这让"新增一条规则"成为一件不可能破坏既有行为的事。
 *
 * <p>代价（必须正视）：也因此<b>不能靠"再加一个类"覆盖一个已被认领的位置</b>。
 * 要改已有位置的风格，只能换 owner，或者让规则读 {@code FormatStyle} 参数化。
 */
@CompileStatic
class RuleRegistry {

    private final Map<Slot, SpacingRule> owners = new LinkedHashMap<Slot, SpacingRule>()
    private final List<SpacingRule> registeredSpacingRules = []
    private final List<IndentRule> registeredIndentRules = []

    RuleRegistry register(SpacingRule rule) {
        rule.slots.each { Slot slot ->
            SpacingRule prev = owners[slot]
            if (prev != null && prev != rule) {
                throw new IllegalStateException(
                        "Slot 冲突: $slot 已被规则 '${prev.id}' 认领，规则 '${rule.id}' 不能再认领。" +
                        ' 一个 Slot 只允许一个 owner —— 请换一个 Slot，或把两条规则合并。')
            }
            owners[slot] = rule
        }
        registeredSpacingRules << rule
        this
    }

    RuleRegistry register(IndentRule rule) {
        registeredIndentRules << rule
        this
    }

    SpacingRule owner(Slot slot) {
        owners[slot]
    }

    List<SpacingRule> spacingRules() {
        Collections.unmodifiableList(registeredSpacingRules)
    }

    List<IndentRule> indentRules() {
        Collections.unmodifiableList(registeredIndentRules)
    }

    Set<Slot> coveredSlots() {
        Collections.unmodifiableSet(owners.keySet())
    }

    /** 还没有规则认领的 Slot —— 这些位置一律保留原文。 */
    Set<Slot> uncoveredSlots() {
        def missing = EnumSet.allOf(Slot)
        missing.removeAll(owners.keySet())
        Collections.unmodifiableSet(missing)
    }

    /** v1 默认规则集：11 条水平空白规则 + 3 条缩进规则。 */
    static RuleRegistry defaults() {
        new RuleRegistry()
        .register(new BinaryOperatorSpacingRule())
        .register(new UnaryOperatorSpacingRule())
        .register(new PunctuationSpacingRule())
        .register(new CallParenSpacingRule())
        .register(new KeywordParenSpacingRule())
        .register(new DotChainSpacingRule())
        .register(new ColonSpacingRule())
        .register(new WildcardSpacingRule())
        .register(new BraceSpacingRule())
        .register(new KeywordSpacingRule())
        .register(new AnnotationSpacingRule())
        .register(new TrailingWhitespaceRule())
        .register(new BlockIndentRule())
        .register(new ContinuationIndentRule())
        .register(new ClosingBracketDedentRule())
    }

}
