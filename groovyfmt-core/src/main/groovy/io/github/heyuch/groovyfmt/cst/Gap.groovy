package io.github.heyuch.groovyfmt.cst

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * 两个 token 之间的 trivia。
 *
 * <p>实测结论：Groovy 的 {@code WS} 规则是 {@code -> skip}，所以水平空白完全不在 token 流里；
 * 而所有换行都<b>是</b> token（NL 类型）。因此 gap 里通常只有空格 / tab，只有两种情况例外：
 *
 * <ul>
 *   <li>行续接符 {@code \} —— 属于 {@code WS} 被 skip 掉，会连同换行一起落进 gap；</li>
 *   <li>文件开头的 shebang（{@code SH_COMMENT -> skip}）。</li>
 * </ul>
 *
 * <p>这两种 gap 一律原样保留，绝不重写。
 */
@CompileStatic
@Immutable
class Gap {

    int start
    int stop
    String original

    /** 是否只含水平空白（空格 / tab）。 */
    boolean isHorizontal() {
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i)
            if (c != (' ' as char) && c != ('\t' as char)) {
                return false
            }
        }
        true
    }

    /** 是否含行续接符 —— 有就整体保留。 */
    boolean hasLineEscape() {
        original.contains('\\')
    }

    @Override
    String toString() {
        "Gap[${original.replace('\n', '\\n').replace('\t', '\\t')}]"
    }

}
