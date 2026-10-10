package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.rule.IndentContext
import io.github.heyuch.groovyfmt.rule.IndentRule

/**
 * 跨行的 ( / [ 内加续行缩进。
 *
 * <p>只按<b>逻辑深度</b>计算，不看实际列宽 —— 这是打断"缩进 ↔ 折行"反馈循环的关键。
 *
 * <p>用的是"最内层未闭合花括号<b>之后</b>"的括号层数，而不是全部括号层数：
 * {@code f(new Runnable() { ... })} 里外层那个 {@code f(} 已被花括号的块缩进接管，
 * 若再计一次就会多缩进一层（曾把匿名类体的缩进改坏）。
 */
@CompileStatic
class ContinuationIndentRule implements IndentRule {

    @Override
    String getId() {
        'indent.continuation'
    }

    @Override
    int delta(IndentContext ctx) {
        ctx.parenDepthSinceBrace() * ctx.style.continuationIndent
    }

}
