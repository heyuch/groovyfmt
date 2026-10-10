package io.github.heyuch.groovyfmt.rule

import groovy.transform.CompileStatic

/**
 * 缩进规则：为某个"行首 token"贡献缩进列数增量（可正可负）。
 *
 * <p>注意缩进按<b>逻辑深度</b>算，不按实际列宽算 —— 这样就切断了
 * "缩进 ← 折行 ← 缩进"的反馈循环（clang-format 也是这么做的）。
 */
@CompileStatic
interface IndentRule {

    String getId()

    /**
     * @return 缩进<b>列数</b>增量（可正可负）；0 表示不表态。
     *         Resolver 会把所有规则的返回值求和 —— 所以块缩进用 {@code style.indentWidth}，
     *         续行缩进用 {@code style.continuationIndent}。
     */
    int delta(IndentContext ctx)

}
