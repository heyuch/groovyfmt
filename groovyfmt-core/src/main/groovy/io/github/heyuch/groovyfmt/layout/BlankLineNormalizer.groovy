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

    @SuppressWarnings('ParameterCount')
    private static int desiredNewlineCount(Cst cst, int s, int e,
            int actual, FormatStyle style) {
        int prev = s - 1
        int next = e + 1
        int minBlankLines = 0
        int maxBlankLines = style.maxBlankLines

        // ① 类体边界：我们自己的风格要求有且只有这么多空行
        if (prev >= 0 && isClassBodyOpen(cst, prev)) {
            minBlankLines = Math.max(minBlankLines, style.blankLinesAfterClassOpen)
        }
        if (next < cst.tokenCount && isClassBodyClose(cst, next)) {
            minBlankLines = Math.max(minBlankLines, style.blankLinesBeforeClassClose)
        }

        // ② CodeNarc BlockStartsWithBlankLine / BlockEndsWithBlankLine：
        //    非类体的块（方法体 / 闭包 / if / try ...）首尾【禁止】空行。
        //    类体是例外（CodeNarc 对类单独用 ClassStarts/EndsWithBlankLine，
        //    而它在 formatting 规则集里默认不启用），所以这里要把类体排除掉。
        if (prev >= 0 && isPlainBlockOpen(cst, prev)) {
            maxBlankLines = 0
        }
        if (next < cst.tokenCount && isPlainBlockClose(cst, next)) {
            maxBlankLines = 0
        }

        // ③ CodeNarc MissingBlankLineAfterPackage / MissingBlankLineAfterImports：
        //    package 之后、以及 import 块结束之后必须有且至少一个空行。
        if (prev >= 0 && endsPackageOrImportBlock(cst, prev)) {
            minBlankLines = Math.max(minBlankLines, 1)
        }

        // ④ CodeNarc MissingBlankLineBeforeAnnotatedField：
        //    带注解的【字段】声明之前必须有空行。
        if (next < cst.tokenCount && startsAnnotatedField(cst, next)) {
            minBlankLines = Math.max(minBlankLines, 1)
        }

        int limit = Math.max(maxBlankLines, minBlankLines)
        int desired = Math.min(actual, limit + 1)
        Math.max(desired, minBlankLines + 1)
    }

    /** 非类体的左花括号：方法体 / 闭包 / 控制块。GString 插值的 { 也排除。 */
    private static boolean isPlainBlockOpen(Cst cst, int braceIndex) {
        if (braceIndex < 0 || braceIndex >= cst.tokenCount) {
            return false
        }
        CstToken t = cst.tokens[braceIndex]
        if (t.type != GroovyLexer.LBRACE || t.comment || isGStringToken(t.type)) {
            return false
        }
        !isClassBodyOpen(cst, braceIndex)
    }

    /** 与 isPlainBlockOpen 配对的右花括号。 */
    private static boolean isPlainBlockClose(Cst cst, int braceIndex) {
        if (braceIndex < 0 || braceIndex >= cst.tokenCount) {
            return false
        }
        if (cst.tokens[braceIndex].type != GroovyLexer.RBRACE
                || cst.tokens[braceIndex].comment || isGStringToken(cst.tokens[braceIndex].type)) {
            return false
        }
        !isClassBodyClose(cst, braceIndex)
    }

    /**
     * 该 token 是否是一条 package 语句或【整个 import 块】的最后一个 token。
     *
     * <p>import 块的中途不要求空行，只要求块结束后有 —— 所以还要看后面那条语句
     * 是不是 import/package。
     */
    private static boolean endsPackageOrImportBlock(Cst cst, int prev) {
        int start = statementStart(cst, prev)
        if (start < 0) {
            return false
        }
        int head = cst.tokens[start].type
        if (head == GroovyLexer.PACKAGE) {
            return true
        }
        if (head != GroovyLexer.IMPORT) {
            return false
        }
        int after = statementStart(cst, prev + 1)
        if (after <= prev) {
            return true
        }
        int nextHead = cst.tokens[after].type
        nextHead != GroovyLexer.IMPORT && nextHead != GroovyLexer.PACKAGE
    }

    /** 从 index 向前找它所在语句的第一个代码 token（跨过换行即停）。 */
    private static int statementStart(Cst cst, int index) {
        if (index < 0 || index >= cst.tokenCount) {
            return -1
        }
        for (int j = index; j >= 0; j--) {
            CstToken t = cst.tokens[j]
            if (t.comment) {
                continue
            }
            if (t.type == GroovyLexer.NL) {
                return j + 1 < cst.tokenCount ? j + 1 : -1
            }
        }
        0
    }

    /**
     * 该 token 是否开始一个【带注解的字段】声明。
     *
     * <p>刻意保守：跳过注解之后，只有当这一行既没有 {@code (}（方法）也没有
     * {@code class/def/...} 之类的声明头，才认定为字段。
     */
    /** 从 index 向前找最近的未配对左括号，判断它是不是类体花括号。 */
    private static boolean enclosingBracketIsClassBody(Cst cst, int index) {
        int paren = 0
        int square = 0
        for (int j = index - 1; j >= 0; j--) {
            CstToken t = cst.tokens[j]
            if (t.comment) {
                continue
            }
            int type = t.type
            if (type == GroovyLexer.RPAREN) {
                paren++
                continue
            }
            if (type == GroovyLexer.RBRACK) {
                square++
                continue
            }
            if (type == GroovyLexer.LPAREN) {
                if (paren > 0) {
                    paren--
                    continue
                }
                return false                      // 在参数列表 / 实参列表里
            }
            if (type == GroovyLexer.LBRACK) {
                if (square > 0) {
                    square--
                    continue
                }
                return false                      // 在下标 / 列表字面量里
            }
            if (type == GroovyLexer.LBRACE) {
                return isClassBodyOpen(cst, j)    // 是类体才算字段
            }
            if (type == GroovyLexer.RBRACE) {
                return false                      // 已经退到上一个块之外
            }
        }
        false
    }

    @SuppressWarnings('CyclomaticComplexity')
    private static boolean startsAnnotatedField(Cst cst, int index) {
        if (index < 0 || index >= cst.tokenCount || cst.tokens[index].type != GroovyLexer.AT) {
            return false
        }
        // 只有【类体】里的字段才适用这条规则。
        // 带注解的方法参数在 ( 里、带注解的局部变量在方法体 { 里：
        // 前者会被下面的"声明本体扫描"误判成字段（参数行以逗号结尾 + 换行，
        // 看着就像一条字段声明结束了），于是在参数之间插出空行。
        if (!enclosingBracketIsClassBody(cst, index)) {
            return false
        }
        int j = index
        // 跳过连续的注解：@Name / @Name(...) / @a.b.Name(...)
        while (j < cst.tokenCount && cst.tokens[j].type == GroovyLexer.AT) {
            j++
            int depth = 0
            while (j < cst.tokenCount) {
                CstToken t = cst.tokens[j]
                if (t.comment) {
                    j++
                    continue
                }
                int type = t.type
                if (type == GroovyLexer.NL) {
                    break
                }
                if (type == GroovyLexer.LPAREN) {
                    depth++
                } else if (type == GroovyLexer.RPAREN) {
                    depth--
                    if (depth == 0) {
                        j++
                        break
                    }
                } else if (depth == 0 && type != GroovyLexer.DOT
                        && type != GroovyLexer.Identifier
                        && type != GroovyLexer.CapitalizedIdentifier) {
                    // 注意：注解名（@Deprecated）在 Groovy 词法里是独立的
                    // CapitalizedIdentifier token，不认它就会把注解名当成"声明本体"，
                    // 于是 @Deprecated 后面跟 class 也会被误判成字段。
                    break
                }
                j++
            }
            while (j < cst.tokenCount && cst.tokens[j].comment) {
                j++
            }
        }
        // 注解经常单独占一行，所以先跨过注解后面的换行与注释，再看声明本体。
        int k = j
        while (k < cst.tokenCount
                && (cst.tokens[k].comment || cst.tokens[k].type == GroovyLexer.NL)) {
            k++
        }
        boolean sawDeclarationToken = false
        for (; k < cst.tokenCount; k++) {
            CstToken t = cst.tokens[k]
            if (t.comment) {
                continue
            }
            int type = t.type
            if (type == GroovyLexer.NL) {
                return sawDeclarationToken     // 声明行结束 → 是字段
            }
            sawDeclarationToken = true
            if (type == GroovyLexer.LPAREN) {
                return false                   // 方法
            }
            if (type == GroovyLexer.LBRACE || type == GroovyLexer.CLASS
                    || type == GroovyLexer.INTERFACE || type == GroovyLexer.ENUM
                    || type == GroovyLexer.TRAIT || type == GroovyLexer.DEF) {
                return false
            }
        }
        false
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
