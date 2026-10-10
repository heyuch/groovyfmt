package io.github.heyuch.groovyfmt.rule.builtin

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.rule.IndentContext
import io.github.heyuch.groovyfmt.rule.IndentRule

/** 每进入一层 {} 加一个块缩进。 */
@CompileStatic
class BlockIndentRule implements IndentRule {

    @Override
    String getId() {
        'indent.block'
    }

    @Override
    int delta(IndentContext ctx) {
        ctx.braceDepth() * ctx.style.indentWidth
    }

}
