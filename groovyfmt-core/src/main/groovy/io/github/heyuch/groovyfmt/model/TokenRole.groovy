package io.github.heyuch.groovyfmt.model

import groovy.transform.CompileStatic

/**
 * TokenAnnotator 预先算出的 token 语义角色。
 *
 * <p>关键设计：<b>规则只消费角色，绝不自己推导</b>。
 * "这个 ( 是调用还是声明""这个 : 是 Map 键还是三元"这类判断只在一个地方实现一次，
 * 否则每条新规则都要重复一遍 —— 这是扩展性最大的杀手。
 */
@CompileStatic
enum TokenRole {

    NONE,
    COMMENT,
    NEWLINE,
    OTHER,

    /** 关键字（含基本类型 int / boolean 等）。 */
    KEYWORD,

    BINARY_OP,

    /** 前缀一元：!x、~x、++x、x = -1 */
    UNARY_PREFIX,
    /** 后缀一元：x++、x-- */
    UNARY_POSTFIX,

    /** . ?. *. ?: :: 等。 */
    DOT_LIKE,

    COMMA,
    SEMICOLON,

    LPAREN,
    RPAREN,
    LBRACK,
    RBRACK,
    LBRACE,
    RBRACE,

    COLON_TERNARY,
    /** for (X x : xs) 里的冒号 —— 两侧都要空格。 */
    COLON_FOR_IN,
    COLON_NAMED_OR_MAP,
    /** case / default / label / assert 消息的冒号 —— 一律不表态。 */
    COLON_UNKNOWN,

    ANNOTATION_AT

}
