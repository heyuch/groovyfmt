package io.github.heyuch.groovyfmt.model

import groovy.transform.CompileStatic

/**
 * 一个 gap 的<b>语义位置</b>。
 *
 * <p>这是整个规则框架的寻址方式：规则不按"第几个 gap"寻址，而按语义位置认领。
 * {@code RuleRegistry} 保证<b>一个 Slot 同时只有一个 owner</b>，
 * 于是规则之间的冲突在注册期就被消除，而不是靠执行顺序碰运气。
 */
@CompileStatic
enum Slot {

    /** 行尾空白（下一个 token 是换行 token）。 */
    TRAILING_WHITESPACE,

    AFTER_COMMA,
    BEFORE_COMMA,
    AFTER_SEMICOLON,
    BEFORE_SEMICOLON,

    /** 调用/声明的左括号之前：foo( / def foo( / new Foo( */
    BEFORE_LPAREN_NO_SPACE,
    /** 控制流关键字的左括号之前：if ( / for ( / while ( */
    BEFORE_LPAREN_KEYWORD,
    AFTER_LPAREN,
    BEFORE_RPAREN,

    AFTER_LBRACK,
    BEFORE_RBRACK,

    /** 闭包直接做第一个实参时的 { ：foo({ ... }) —— 与前面的 ( 之间不留空格。 */
    BEFORE_CLOSURE_ARG,

    BEFORE_LBRACE,
    AFTER_LBRACE,
    BEFORE_RBRACE,

    BEFORE_DOT,
    AFTER_DOT,

    /** Map 字面量 / 具名参数：[k: v]、foo(a: 1) */
    BEFORE_COLON,
    AFTER_COLON,

    /** 三元表达式：a ? b : c */
    BEFORE_TERNARY_COLON,
    AFTER_TERNARY_COLON,

    /** for (X x : xs) 的冒号两侧。 */
    /** 泛型通配符 ? 之前（不留空格）。 */
    BEFORE_WILDCARD,

    BEFORE_FOR_IN_COLON,
    AFTER_FOR_IN_COLON,

    BEFORE_BINARY_OP,
    AFTER_BINARY_OP,

    /** 后缀 ++ / -- 之前。 */
    BEFORE_UNARY_OP,
    /** 前缀 ! ~ ++ -- - 之后。 */
    AFTER_UNARY_OP,

    /** 关键字（if / return / new / class ...）之后。 */
    AFTER_KEYWORD,

    /** 注解的 @ 之后：@Foo */
    AFTER_ANNOTATION_AT

}
