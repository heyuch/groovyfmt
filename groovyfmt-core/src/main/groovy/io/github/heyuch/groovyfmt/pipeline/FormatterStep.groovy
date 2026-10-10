package io.github.heyuch.groovyfmt.pipeline

import groovy.transform.CompileStatic

/**
 * 文件级管道步骤（Command 模式）。
 *
 * <p>这些步骤之间<b>确实正交</b>（改文件末尾换行不会影响缩进规则），
 * 所以 Command 的"顺序执行、互不干扰"假设在这里成立。
 *
 * <p>注意：这里只放"整串文本级"的琐碎规范化。真正的格式化不在这里，
 * 而在 CST + 规则框架里 —— 因为规则之间<b>不</b>正交。
 */
@CompileStatic
interface FormatterStep {

    String getName()

    String apply(String source)

}
