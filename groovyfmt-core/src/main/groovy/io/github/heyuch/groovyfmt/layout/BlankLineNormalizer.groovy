package io.github.heyuch.groovyfmt.layout

import groovy.transform.CompileStatic

import org.apache.groovy.parser.antlr4.GroovyLexer

import io.github.heyuch.groovyfmt.annotate.TokenAnnotator.Annotated
import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstToken
import io.github.heyuch.groovyfmt.model.FormatStyle

/**
 * 空行规范化。
 *
 * <p>这是本工具里<b>唯一</b>会增删 token 的环节：在 Groovy 里换行是真实的 token（NL 类型），
 * 所以"加一个空行"就等于"插一个 NL token"。
 *
 * <h3>为什么这样做仍然是安全的</h3>
 * 因为空行只改变 NL token 的<b>个数</b>，而 Groovy 的语法规则是
 * <pre>nls : NL+</pre>
 * 一个还是三个 NL，解析结果完全相同。
 * 所以这里的约束是：<b>只允许改"连续换行 token 的个数"，绝不碰任何其他 token</b>。
 *
 * <h3>对安全闸门的影响</h3>
 * 因此输出闸门不能再比对"token 序列完全一致"，而要改成
 * {@link Cst#canonicalTokenFingerprint}（把连续换行折叠成一个之后比对）。
 * 这个规范化形式仍然保证：所有非换行 token 逐一相同、每处换行的换行风格
 * （{@code \n} 还是 {@code \r\n}）相同、换行处的个数相同 —— 唯一允许的差异就是空行数。
 */
@CompileStatic
class BlankLineNormalizer {

    /** 规范化后的 token 序列与 gap 序列（两者必须成对使用）。 */
    static final class Result {

        final List<CstToken> tokens
        final List<String> gaps

        Result(List<CstToken> tokens, List<String> gaps) {
            this.tokens = tokens
            this.gaps = gaps
        }

    }

    private BlankLineNormalizer() {
    }

    static Result normalize(Cst cst, Annotated annotated, List<String> gaps, FormatStyle style) {
        List<CstToken> toks = cst.tokens
        int n = toks.size()
        def outToks = new ArrayList<CstToken>(n)
        def outGaps = new ArrayList<String>(n + 1)
        outGaps << gaps[0]

        int i = 0
        while (i < n) {
            if (!annotated.isNewline(i)) {
                outToks << toks[i]
                outGaps << gaps[i + 1]
                i++
                continue
            }
            // 收集一段「连续换行 token」（中间只隔水平空白）
            int s = i
            int e = i
            // 注意 gaps 的下标：gaps[k] 是 token k 之前的空白，
            // 所以"token e 与 e+1 之间"的那个 gap 是 gaps[e + 1]（不是 e + 2）。
            while (e + 1 < n && annotated.isNewline(e + 1) && isBlankText(gaps[e + 1])) {
                e++
            }
            int actual = e - s + 1
            int desired = desiredNewlineCount(cst, s, e, actual, style)

            // 保留第一个（它携带换行风格：\n 还是 \r\n），按需复制或丢弃
            for (int k = 0; k < desired; k++) {
                outToks << toks[s]
                outGaps << (k == desired - 1 ? gaps[e + 1] : '')
            }
            i = e + 1
        }
        new Result(outToks, outGaps)
    }

    private static int desiredNewlineCount(Cst cst, int s, int e, int actual, FormatStyle style) {
        int minBlankLines = 0
        if (s > 0 && isClassBodyOpen(cst, s - 1)) {
            minBlankLines = Math.max(minBlankLines, style.blankLinesAfterClassOpen)
        }
        int next = e + 1
        if (next < cst.tokenCount && isClassBodyClose(cst, next)) {
            minBlankLines = Math.max(minBlankLines, style.blankLinesBeforeClassClose)
        }
        int maxBlankLines = Math.max(style.maxBlankLines, minBlankLines)
        int desired = Math.min(actual, maxBlankLines + 1)
        Math.max(desired, minBlankLines + 1)
    }

    // ─────────────────────── 类体花括号的识别 ───────────────────────

    /**
     * 判断某个 {@code {} 是不是"类声明体"的左花括号（{@code class / interface / enum / trait / record / 匿名类}）。
     *
     * <p>规则刻意保守：<b>一旦往回扫到换行 token 就放弃</b>。
     * 类声明头在实践中几乎总写在一行（{@code class A extends B implements C {}}），
     * 而保守方向是"不插空行"—— 那最多是少做一点，不会把空行插到闭包里去。
     */
    private static boolean isClassBodyOpen(Cst cst, int braceIndex) {
        if (braceIndex < 0 || braceIndex >= cst.tokenCount) {
            return false
        }
        if (cst.tokens[braceIndex].type != GroovyLexer.LBRACE) {
            return false
        }
        int parenDepth = 0
        for (int j = braceIndex - 1; j >= 0; j--) {
            CstToken t = cst.tokens[j]
            if (t.comment) {
                continue
            }
            int type = t.type
            if (type == GroovyLexer.NL) {
                return false                       // 跨行声明头 → 保守放弃
            }
            if (isGStringToken(type)) {
                return false                       // GString 插值的 { 不是类体
            }
            if (type == GroovyLexer.RPAREN) {
                parenDepth++
                continue
            }
            if (type == GroovyLexer.LPAREN) {
                if (parenDepth > 0) {
                    parenDepth--
                    continue
                }
                return false
            }
            if (parenDepth > 0) {
                continue
            }
            Boolean verdict = classifyHeaderToken(type)
            if (verdict != null) {
                return verdict
            }
        }
        false
    }

    /** 类声明头的判定；返回 {@code null} 表示"还没得出结论，继续往前扫"。 */
    private static Boolean classifyHeaderToken(int type) {
        if (type == GroovyLexer.LBRACE || type == GroovyLexer.RBRACE || type == GroovyLexer.SEMI) {
            return Boolean.FALSE
        }
        // 匿名内部类（new Foo() { ... }）CodeNarc 也算作 "Class"，
        // 要让两条空行规则通过，就得一并认它。
        if (type == GroovyLexer.CLASS || type == GroovyLexer.INTERFACE || type == GroovyLexer.ENUM
                || type == GroovyLexer.TRAIT || type == GroovyLexer.RECORD || type == GroovyLexer.NEW) {
            return Boolean.TRUE
        }
        null
    }

    /** 判断某个 {@code }} 是不是"类声明体"的右花括号（即它配对的那个 {@code {} 是类体）。 */
    private static boolean isClassBodyClose(Cst cst, int braceIndex) {
        if (braceIndex < 0 || braceIndex >= cst.tokenCount) {
            return false
        }
        if (cst.tokens[braceIndex].type != GroovyLexer.RBRACE) {
            return false
        }
        int depth = 0
        for (int j = braceIndex; j >= 0; j--) {
            if (cst.tokens[j].comment) {
                continue
            }
            int type = cst.tokens[j].type
            if (type == GroovyLexer.RBRACE) {
                depth++
                continue
            }
            if (type == GroovyLexer.LBRACE) {
                depth--
                if (depth == 0) {
                    return isClassBodyOpen(cst, j)
                }
            }
        }
        false
    }

    /** 是否只含空格 / tab（用来确认两个换行 token 之间没有别的东西）。 */
    private static boolean isBlankText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i)
            if (c != (' ' as char) && c != ('\t' as char)) {
                return false
            }
        }
        true
    }

    private static boolean isGStringToken(int type) {
        type == GroovyLexer.GStringBegin || type == GroovyLexer.GStringPart
    }

}
