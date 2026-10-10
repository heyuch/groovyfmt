package io.github.heyuch.groovyfmt.model

import groovy.transform.CompileStatic

/** 一个 gap 的水平空白决策。 */
@CompileStatic
enum Spacing {

    /** 不加任何空白。 */
    NONE,
    /** 恰好一个空格。 */
    SPACE,
    /** 不表态，保留原文。 */
    PRESERVE

}
