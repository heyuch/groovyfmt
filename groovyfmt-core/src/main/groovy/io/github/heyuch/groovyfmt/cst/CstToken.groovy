package io.github.heyuch.groovyfmt.cst

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * 一个词法单元，携带它在<b>原始源码</b>中的精确区间。
 *
 * <p>注意 {@code text} 一律取自 {@code source.substring(start, stop + 1)}，
 * 而不是 ANTLR token 的 {@code getText()} —— 用 substring 才有无损保证。
 *
 * <p>注释在 Groovy 的 token 流里是 <b>NL 类型</b>
 * （GroovyLexer.g4：{@code ML_COMMENT / SL_COMMENT -> type(NL)}），只能靠文本前缀区分。
 */
@CompileStatic
@Immutable
class CstToken {

    int type
    String text
    int start
    int stop
    boolean comment

    boolean is(int t) {
        type == t
    }

    @Override
    String toString() {
        def body = text.replace('\n', '\\n')
        "${comment ? 'COMMENT' : 'TOK'}($body)"
    }

}
