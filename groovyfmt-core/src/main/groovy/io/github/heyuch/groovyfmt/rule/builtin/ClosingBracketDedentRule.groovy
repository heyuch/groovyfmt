package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.rule.IndentContext
import io.github.heyuch.groovyfmt.rule.IndentRule

/**
 * 以闭合括号开头的行，把对应那一层缩进退回去：} / ) / ]
 *
 * <p>退多少取决于最内层是哪种括号 —— 块缩进和续行缩进的宽度不同。
 */
@CompileStatic
class ClosingBracketDedentRule implements IndentRule {

    @Override
    String getId() {
        'indent.closer'
    }

    @Override
    int delta(IndentContext ctx) {
        if (!ctx.startsWithCloser()) {
            return 0
        }
        char enclosing = ctx.enclosing()
        if (enclosing == ('{' as char)) {
            return -ctx.style.indentWidth
        }
        if (enclosing == ('(' as char) || enclosing == ('[' as char)) {
            return -ctx.style.continuationIndent
        }
        0
    }

}
