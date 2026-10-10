package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.Spacing

/**
 * 水平空白规则。
 *
 * <p>契约（实现者必须满足）：
 * <ol>
 *   <li><b>纯函数</b>：只看 {@link GapContext}，不看中间态文本、不持有可变状态；</li>
 *   <li><b>只碰自己的 Slot</b>：绝不改写 token，绝不新增/删除 token；</li>
 *   <li><b>幂等</b>：同一输入重复执行结果相同；</li>
 *   <li><b>可配</b>：行为从 {@code ctx.style} 读，不硬编码。</li>
 * </ol>
 *
 * <p>扩展方式：新增一个类 → 实现本接口 → 在 {@link RuleRegistry} 注册。
 * 只需认领<b>还没人占用</b>的 Slot，注册期就会校验冲突。
 */
@CompileStatic
interface SpacingRule {

    /** 稳定标识，出现在统计与报错里。 */
    String getId()

    /** 本规则认领的 Slot。一个 Slot 只允许一个 owner。 */
    Set<Slot> getSlots()

    /**
     * 决定这个 gap 长什么样。
     *
     * @param slot 当前 gap 对应的 Slot（必属于 {@link #getSlots()}）
     * @return {@link Spacing#PRESERVE} 表示不表态，保留原文
     */
    Spacing decide(Slot slot, GapContext ctx)

}
