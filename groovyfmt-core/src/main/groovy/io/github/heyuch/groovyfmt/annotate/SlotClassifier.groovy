package io.github.heyuch.groovyfmt.annotate

import groovy.transform.CompileStatic

import org.apache.groovy.parser.antlr4.GroovyLexer

import io.github.heyuch.groovyfmt.annotate.TokenAnnotator.Annotated
import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstToken
import io.github.heyuch.groovyfmt.cst.Gap
import io.github.heyuch.groovyfmt.model.Slot
import io.github.heyuch.groovyfmt.model.TokenRole

/**
 * 把一个 gap 分类到 {@link Slot}。
 *
 * <p>分类是<b>唯一</b>决定"这个位置由哪条规则负责"的地方，所以：
 * <ul>
 *   <li>一个 gap 只会得到一个 Slot（天然互斥）；</li>
 *   <li>分类不出来就返回 {@code null} —— 表示<b>保留原文</b>。这是 fail-closed：
 *       工具永远不会去动它没看懂的地方。</li>
 * </ul>
 *
 * <p>判断顺序本身就是优先级契约，改动顺序前请先看注释。
 */
@CompileStatic
class SlotClassifier {

    private static final Set<Integer> PAREN_KEYWORDS = [
            GroovyLexer.IF, GroovyLexer.FOR, GroovyLexer.WHILE,
            GroovyLexer.SWITCH, GroovyLexer.CATCH, GroovyLexer.SYNCHRONIZED,
    ] as Set

    private SlotClassifier() {
    }

    /**
     * @param g gap 下标，表示"第 g 个 token 之前的那个 gap"
     * @return 负责这个 gap 的 Slot；{@code null} 表示保留原文
     */
    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'MethodSize'])   // 优先级顺序本身就是契约，拆散反而看不出顺序
    static Slot classify(Cst cst, Annotated a, int g) {
        int n = cst.tokenCount
        if (g <= 0 || g >= n) {
            return null                                       // 文件首尾：交给 Resolver 清空白
        }
        CstToken prev = cst.tokens[g - 1]
        CstToken next = cst.tokens[g]

        if (prev.comment || next.comment) {
            return null                                       // 贴着注释的位置一概不动
        }
        if (a.isInterpolationBrace(g - 1) || a.isInterpolationBrace(g)) {
            return null                                       // GString 的 ${...}：加空格会把 ${ 拆成 $ {
        }
        if (a.isNewline(g - 1)) {
            return null                                       // 行首缩进，交给缩进层
        }
        if (a.isNewline(g)) {
            return Slot.TRAILING_WHITESPACE                   // 行尾空白
        }

        TokenRole pr = a.role(g - 1)
        TokenRole nr = a.role(g)
        int pt = prev.type
        int nt = next.type

        // ── 1. 后缀一元运算：i ++ → i++
        if (nr == TokenRole.UNARY_POSTFIX) {
            return Slot.BEFORE_UNARY_OP
        }

        // ── 2. 闭合类
        if (nt == GroovyLexer.RPAREN) {
            return Slot.BEFORE_RPAREN
        }
        if (nt == GroovyLexer.RBRACK) {
            return Slot.BEFORE_RBRACK
        }
        if (nt == GroovyLexer.COMMA) {
            return Slot.BEFORE_COMMA
        }
        if (nt == GroovyLexer.SEMI) {
            return Slot.BEFORE_SEMICOLON
        }
        if (nt == GroovyLexer.RBRACE) {
            return Slot.BEFORE_RBRACE
        }

        // ── 3. 开括号
        if (nt == GroovyLexer.LPAREN) {
            if (PAREN_KEYWORDS.contains(pt)) {
                return Slot.BEFORE_LPAREN_KEYWORD
            }
            if (pt == GroovyLexer.CapitalizedIdentifier || TokenAnnotator.endsValue(pt)) {
                return Slot.BEFORE_LPAREN_NO_SPACE
            }
            // 其余（= ( / return ( / , ( / ( ( ...）落到下面由 prev 的角色决定
        } else if (nt == GroovyLexer.LBRACK) {
            if (pt == GroovyLexer.BuiltInPrimitiveType || TokenAnnotator.endsValue(pt)) {
                return null                                   // 数组类型 int[] / 下标 a[0]：不动
            }
            // 其余落到下面（= [ / return [ / ( [ ...）
        } else if (nt == GroovyLexer.LBRACE) {
            if (pt == GroovyLexer.LPAREN || pt == GroovyLexer.LBRACK) {
                // 闭包直接做第一个实参：foo({ ... }) —— ( 与 { 之间不留空格
                return Slot.BEFORE_CLOSURE_ARG
            }
            return Slot.BEFORE_LBRACE
        } else if (nt == GroovyLexer.COLON) {
            if (nr == TokenRole.COLON_TERNARY) {
                return Slot.BEFORE_TERNARY_COLON
            }
            if (nr == TokenRole.COLON_NAMED_OR_MAP) {
                return Slot.BEFORE_COLON
            }
            if (nr == TokenRole.COLON_FOR_IN) {
                return Slot.BEFORE_FOR_IN_COLON
            }
            return null                                       // case / default / label / assert 消息
        }

        // ── 4. 注解 @Foo
        if (pr == TokenRole.ANNOTATION_AT) {
            return Slot.AFTER_ANNOTATION_AT
        }

        // ── 5. 点链 a.b / a?.b / a*.b
        if (nr == TokenRole.DOT_LIKE) {
            return Slot.BEFORE_DOT
        }
        if (pr == TokenRole.DOT_LIKE) {
            return Slot.AFTER_DOT
        }

        // ── 6. 逗号 / 分号之后
        if (pr == TokenRole.COMMA) {
            return Slot.AFTER_COMMA
        }
        if (pr == TokenRole.SEMICOLON) {
            return Slot.AFTER_SEMICOLON
        }

        // ── 7. 冒号之后
        if (pr == TokenRole.COLON_TERNARY) {
            return Slot.AFTER_TERNARY_COLON
        }
        if (pr == TokenRole.COLON_NAMED_OR_MAP) {
            return Slot.AFTER_COLON
        }
        if (pr == TokenRole.COLON_FOR_IN) {
            return Slot.AFTER_FOR_IN_COLON
        }

        // ── 7.5 泛型通配符 ? 之前不留空格（放在逗号之后，才不会被 AFTER_COMMA 抢走）
        if (nr == TokenRole.QUESTION_WILDCARD) {
            return Slot.BEFORE_WILDCARD
        }

        // ── 8. 前缀一元运算之后：!x / -1 / *args
        if (pr == TokenRole.UNARY_PREFIX) {
            return Slot.AFTER_UNARY_OP
        }

        // ── 9. 左括号之后
        if (pt == GroovyLexer.LPAREN) {
            return Slot.AFTER_LPAREN
        }
        if (pt == GroovyLexer.LBRACK) {
            return Slot.AFTER_LBRACK
        }
        if (pt == GroovyLexer.LBRACE) {
            return Slot.AFTER_LBRACE
        }

        // ── 10. 二元运算
        if (nr == TokenRole.BINARY_OP) {
            if (angleAmbiguous(nt) && !spacedBothSides(cst, g)) {
                return null                                   // 可能是泛型 List<String> → 不动
            }
            return Slot.BEFORE_BINARY_OP
        }
        if (pr == TokenRole.BINARY_OP) {
            if (angleAmbiguous(pt) && !spacedBothSides(cst, g - 1)) {
                return null
            }
            return Slot.AFTER_BINARY_OP
        }

        // ── 11. 关键字之后
        if (pr == TokenRole.KEYWORD) {
            return Slot.AFTER_KEYWORD
        }

        null
    }

    /**
     * {@code <} {@code >} 与泛型有歧义，需要额外证据。
     *
     * <p>注意：Groovy 的词法里根本没有独立的 {@code <<} / {@code >>} token ——
     * 它们是两个 / 三个 {@code LT} / {@code GT}（实测），所以这里也一并覆盖了移位运算。
     */
    private static boolean angleAmbiguous(int type) {
        type == GroovyLexer.LT || type == GroovyLexer.GT
    }

    /**
     * 只有当运算符<b>两侧原文都有空白</b>时才敢认定它是比较/移位运算。
     *
     * <p>{@code a < b} → 认定；{@code List<String>} / {@code a<b} → 保守不动。
     * （注意：即便误判也不会改语义 —— 实测 Groovy 解析完全不受水平空白影响 ——
     * 这里纯粹是审美考虑，不想把泛型写成 {@code List < String >}。）
     */
    private static boolean spacedBothSides(Cst cst, int opIndex) {
        if (opIndex < 0 || opIndex + 1 >= cst.gaps.size()) {
            return false
        }
        Gap before = cst.gaps[opIndex]
        Gap after = cst.gaps[opIndex + 1]
        !before.original.empty && !after.original.empty
    }

}
