package io.github.heyuch.groovyfmt.annotate

import groovy.transform.CompileStatic

import org.apache.groovy.parser.antlr4.GroovyLexer

import io.github.heyuch.groovyfmt.cst.Cst
import io.github.heyuch.groovyfmt.cst.CstBuilder
import io.github.heyuch.groovyfmt.cst.CstToken
import io.github.heyuch.groovyfmt.model.TokenRole

/**
 * 预标注遍：给每个 token 打语义角色，并记录它在嵌套结构中的位置。
 *
 * <p>这是"规则只消费角色、不自己推导"的落地点。整份标注<b>只算一遍</b>，
 * 之后所有规则都是纯查表。
 */
@CompileStatic
class TokenAnnotator {

    private static final char BASE = (char) 0
    private static final char INTERPOLATION = '$' as char

    /** 以"值"结尾的 token 类型 —— 用来判断紧随的 + - * 是一元还是二元。 */
    private static final Set<Integer> VALUE_END_TYPES = [
            GroovyLexer.Identifier, GroovyLexer.CapitalizedIdentifier,
            GroovyLexer.IntegerLiteral, GroovyLexer.FloatingPointLiteral,
            GroovyLexer.BooleanLiteral, GroovyLexer.NullLiteral,
            GroovyLexer.StringLiteral, GroovyLexer.GStringEnd,
            GroovyLexer.RPAREN, GroovyLexer.RBRACK,
            GroovyLexer.THIS, GroovyLexer.SUPER,
    ] as Set

    private static final Set<Integer> BINARY_OPERATORS = [
            GroovyLexer.ASSIGN, GroovyLexer.ADD_ASSIGN, GroovyLexer.SUB_ASSIGN,
            GroovyLexer.MUL_ASSIGN, GroovyLexer.DIV_ASSIGN, GroovyLexer.AND_ASSIGN,
            GroovyLexer.OR_ASSIGN, GroovyLexer.XOR_ASSIGN, GroovyLexer.MOD_ASSIGN,
            GroovyLexer.LSHIFT_ASSIGN, GroovyLexer.RSHIFT_ASSIGN, GroovyLexer.URSHIFT_ASSIGN,
            GroovyLexer.ELVIS_ASSIGN,
            GroovyLexer.EQUAL, GroovyLexer.NOTEQUAL, GroovyLexer.IDENTICAL, GroovyLexer.NOT_IDENTICAL,
            // 注意：Groovy 词法里没有独立的 << >> token，它们是 2~3 个 LT / GT
            GroovyLexer.LT, GroovyLexer.GT, GroovyLexer.LE, GroovyLexer.GE,
            GroovyLexer.SPACESHIP, GroovyLexer.REGEX_FIND, GroovyLexer.REGEX_MATCH,
            GroovyLexer.POWER,
            GroovyLexer.AND, GroovyLexer.OR, GroovyLexer.BITAND, GroovyLexer.BITOR, GroovyLexer.XOR,
            GroovyLexer.DIV, GroovyLexer.MOD,
            GroovyLexer.ELVIS, GroovyLexer.ARROW, GroovyLexer.QUESTION,
            // in / as / instanceof 语义上是二元运算符
            GroovyLexer.IN, GroovyLexer.AS, GroovyLexer.INSTANCEOF,
            GroovyLexer.NOT_IN, GroovyLexer.NOT_INSTANCEOF,
    ] as Set

    private static final Set<Integer> KEYWORDS = [
            GroovyLexer.IF, GroovyLexer.ELSE, GroovyLexer.RETURN, GroovyLexer.ASSERT,
            GroovyLexer.THROW, GroovyLexer.NEW, GroovyLexer.FOR, GroovyLexer.WHILE,
            GroovyLexer.SWITCH, GroovyLexer.CATCH, GroovyLexer.TRY, GroovyLexer.FINALLY,
            GroovyLexer.DO, GroovyLexer.YIELD, GroovyLexer.SYNCHRONIZED,
            GroovyLexer.PACKAGE, GroovyLexer.IMPORT, GroovyLexer.CLASS, GroovyLexer.INTERFACE,
            GroovyLexer.ENUM, GroovyLexer.TRAIT, GroovyLexer.RECORD, GroovyLexer.DEF, GroovyLexer.VAR,
            GroovyLexer.THROWS, GroovyLexer.EXTENDS, GroovyLexer.IMPLEMENTS, GroovyLexer.PERMITS,
            GroovyLexer.ABSTRACT, GroovyLexer.FINAL, GroovyLexer.STATIC, GroovyLexer.PRIVATE,
            GroovyLexer.PROTECTED, GroovyLexer.PUBLIC, GroovyLexer.NATIVE, GroovyLexer.VOLATILE,
            GroovyLexer.TRANSIENT, GroovyLexer.STRICTFP, GroovyLexer.SEALED, GroovyLexer.NON_SEALED,
            GroovyLexer.CASE, GroovyLexer.DEFAULT, GroovyLexer.CONST, GroovyLexer.THREADSAFE,
            GroovyLexer.BuiltInPrimitiveType,
    ] as Set

    private static final Set<Integer> GSTRING_TOKENS = [
            GroovyLexer.GStringBegin, GroovyLexer.GStringPart,
            GroovyLexer.GStringEnd, GroovyLexer.GStringPathPart,
    ] as Set

    private TokenAnnotator() {
    }

    /** 标注结果。 */
    @CompileStatic
    static class Annotated {

        final Cst cst
        final TokenRole[] roles

        private final int[] braceDepthArr
        private final int[] parenDepthArr
        private final int[] parenSinceBraceArr
        private final char[] topArr
        private final int[] openerArr
        private final int[] lineArr
        private final int[] lineFirstArr
        private final boolean[] newline
        private final boolean[] interpolationBrace

        @SuppressWarnings('ParameterCount')
        @SuppressWarnings('ParameterCount')
        Annotated(Cst cst, TokenRole[] roles, int[] braceDepthArr, int[] parenDepthArr,
                int[] parenSinceBraceArr, char[] topArr, int[] openerArr, int[] lineArr,
                int[] lineFirstArr, boolean[] newline, boolean[] interpolationBrace) {
            this.cst = cst
            this.roles = roles
            this.braceDepthArr = braceDepthArr
            this.parenDepthArr = parenDepthArr
            this.parenSinceBraceArr = parenSinceBraceArr
            this.topArr = topArr
            this.openerArr = openerArr
            this.lineArr = lineArr
            this.lineFirstArr = lineFirstArr
            this.newline = newline
            this.interpolationBrace = interpolationBrace
        }

        int size() {
            roles.length
        }

        boolean isComment(int i) {
            cst.tokens[i].comment
        }

        boolean isNewline(int i) {
            i >= 0 && i < newline.length && newline[i]
        }

        /**
         * 该 token 是否是 GString 插值 ${...} 的花括号。
         *
         * <p>这类花括号<b>绝对不能加空格</b>：{@code "${x}"} 一旦变成 {@code "$ {x}"}，
         * 词法器就会报 token recognition error，Groovy 编译器直接拒绝。
         */
        boolean isInterpolationBrace(int i) {
            i >= 0 && i < interpolationBrace.length && interpolationBrace[i]
        }

        /** 该 token 之前已打开的 {@code {} 层数。 */
        int braceDepthBefore(int i) {
            braceDepthArr[i]
        }

        /** 该 token 之前已打开的 {@code (} / {@code [} 层数。 */
        int parenDepthBefore(int i) {
            parenDepthArr[i]
        }

        /**
         * 该 token 之前打开的、且位于<b>最内层未闭合 {@code {} 之后</b>的 {@code (} / {@code [} 层数。
         *
         * <p>续行缩进只该由这一部分贡献：{@code f(new Runnable() { ... })} 里
         * 外层那个 {@code f(} 已经被花括号的块缩进"接管"了，再叠加一次就会多缩进一层。
         */
        int parenDepthSinceBraceBefore(int i) {
            parenSinceBraceArr[i]
        }

        /**
         * 该 token 之前<b>最内层未闭合括号的打开位置</b>（token 下标）；没有则 -1。
         *
         * <p>这是"缩进锚点"模型的基础：一行的缩进 = 打开本行所在括号的那一行的缩进 + 一次增量，
         * 而不是"从第 0 列数几层"。两者只在每层锚点恰好落在 {@code 层数×增量} 网格上时才相等 ——
         * 一旦花括号/括号嵌在续行里（匿名类、闭包实参、多层实参），深度模型就会算错。
         */
        int openerBefore(int i) {
            openerArr[i]
        }

        /** 该 token 所在的行号（从 0 开始）。 */
        int lineOf(int i) {
            lineArr[i]
        }

        /** 总行数。 */
        int lineCount() {
            lineFirstArr.length
        }

        /** 第 line 行的第一个 token 下标。 */
        int firstTokenOfLine(int line) {
            lineFirstArr[line]
        }

        /** 该 token 所在的最内层括号类型：{@code {} {@code (} {@code [} 或 0。 */
        char topBefore(int i) {
            topArr[i]
        }

        TokenRole role(int i) {
            (i < 0 || i >= roles.length) ? TokenRole.NONE : roles[i]
        }

        int type(int i) {
            (i < 0 || i >= roles.length) ? -1 : cst.tokens[i].type
        }

        /** 向前找第一个真正的代码 token（跳过注释与换行）。 */
        int prevCode(int from) {
            for (int i = Math.min(from, size() - 1); i >= 0; i--) {
                if (!isComment(i) && !isNewline(i)) {
                    return i
                }
            }
            -1
        }

        /** 向后找第一个真正的代码 token（跳过注释与换行）。 */
        int nextCode(int from) {
            for (int i = Math.max(from, 0); i < size(); i++) {
                if (!isComment(i) && !isNewline(i)) {
                    return i
                }
            }
            -1
        }

    }

    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'MethodSize'])   // 词法状态机，分支密度即规范
    static Annotated annotate(Cst cst) {
        annotate(cst, null)
    }

    /** 带语法上下文索引的版本；索引为 null 时全部退回词法判据。 */
    // AbcMetric / CyclomaticComplexity / MethodSize：这是整个词法注解状态机，
    // 拆开反而让"状态如何推进"难以跟踪；属非格式化规则。
    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'MethodSize'])
    static Annotated annotate(Cst cst, ParseContextIndex parseIndex) {
        List<CstToken> toks = cst.tokens
        int n = toks.size()
        def roles = new TokenRole[n]
        def braceDepth = new int[n]
        def parenDepth = new int[n]
        def parenSinceBrace = new int[n]
        def top = new char[n]
        def newline = new boolean[n]
        def interpolationBrace = new boolean[n]

        Deque<Character> stack = new ArrayDeque<>()
        Deque<Boolean> ternary = new ArrayDeque<>()
        stack.push(BASE)
        ternary.push(Boolean.FALSE)
        int braces = 0
        int parens = 0

        for (int i = 0; i < n; i++) {
            CstToken t = toks[i]
            braceDepth[i] = braces
            parenDepth[i] = parens
            parenSinceBrace[i] = parensSinceBrace(stack)
            top[i] = stack.peek().charValue()

            if (t.comment) {
                roles[i] = TokenRole.COMMENT
                continue
            }
            if (CstBuilder.isNewlineToken(t)) {
                newline[i] = true
                roles[i] = TokenRole.NEWLINE
                continue
            }

            int type = t.type

            if (type == GroovyLexer.LPAREN) {
                parens++
                open(stack, ternary, '(' as char)
                roles[i] = TokenRole.LPAREN
                continue
            }
            if (type == GroovyLexer.RPAREN) {
                parens--
                close(stack, ternary)
                roles[i] = TokenRole.RPAREN
                continue
            }
            if (type == GroovyLexer.LBRACK) {
                parens++
                open(stack, ternary, '[' as char)
                roles[i] = TokenRole.LBRACK
                continue
            }
            if (type == GroovyLexer.RBRACK) {
                parens--
                close(stack, ternary)
                roles[i] = TokenRole.RBRACK
                continue
            }
            if (type == GroovyLexer.LBRACE) {
                if (isInterpolationStart(toks, roles, i)) {
                    // GString 插值 ${ —— 用 $ 作为栈标记，且不计入块缩进深度
                    interpolationBrace[i] = true
                    stack.push(INTERPOLATION)
                    ternary.push(Boolean.FALSE)
                } else {
                    braces++
                    open(stack, ternary, '{' as char)
                }
                roles[i] = TokenRole.LBRACE
                continue
            }
            if (type == GroovyLexer.RBRACE) {
                if (stack.peek().charValue() == INTERPOLATION) {
                    interpolationBrace[i] = true
                    close(stack, ternary)
                } else {
                    braces--
                    close(stack, ternary)
                }
                roles[i] = TokenRole.RBRACE
                continue
            }

            int prevIdx = prevCodeIndex(roles, i - 1)
            int prevType = prevIdx >= 0 ? toks[prevIdx].type : -1

            if (type == GroovyLexer.COLON) {
                if (Boolean.TRUE == ternary.peek()) {
                    ternary.pop()
                    ternary.push(Boolean.FALSE)
                    roles[i] = TokenRole.COLON_TERNARY
                } else if (isForInColon(toks, i, parseIndex)) {
                    roles[i] = TokenRole.COLON_FOR_IN
                } else {
                    char enclosing = stack.peek().charValue()
                    if (enclosing == ('(' as char) || enclosing == ('[' as char)) {
                        // Groovy 把具名实参建成隐式 Map，所以两者的叶子节点都是 mapEntry；
                        // 区别在祖先链（实测遍历全部 165 条规则问 insideAt 得到）：
                        //   纯 Map 字面量 / 显式传 map -> ... map < mapEntryList < mapEntry
                        //   具名实参                    -> ... mapEntry < arguments | creator
                        roles[i] = parseIndex?.insideAt(toks[i].start, 'map') ?
                        TokenRole.COLON_NAMED_OR_MAP : TokenRole.COLON_NAMED_ARG
                    } else {
                        roles[i] = TokenRole.COLON_UNKNOWN
                    }
                }
                continue
            }

            roles[i] = classifyToken(prevType, type)

            if (type == GroovyLexer.QUESTION) {
                if (isWildcardQuestion(toks, i, parseIndex)) {
                    // 泛型通配符不是三元运算符：不能压 ternary 栈，
                    // 否则后面第一个冒号会被误判成三元冒号。
                    roles[i] = TokenRole.QUESTION_WILDCARD
                } else {
                    ternary.pop()
                    ternary.push(Boolean.TRUE)
                }
            }
        }

        int[] opener = new int[n]
        int[] line = new int[n]
        List<Integer> firstOfLine = []
        computeLineAndOpeners(toks, interpolationBrace, opener, line, firstOfLine)
        int[] lineFirst = new int[firstOfLine.size()]
        for (int i = 0; i < lineFirst.length; i++) {
            lineFirst[i] = firstOfLine.get(i)
        }

        new Annotated(cst, roles, braceDepth, parenDepth, parenSinceBrace, top, opener, line,
                lineFirst, newline, interpolationBrace)
    }

    /**
     * 数出栈中位于最内层未闭合 {@code {} （或 GString 插值 {@code ${}}）之上的 {@code (} / {@code [} 个数。
     *
     * <p>{@link ArrayDeque} 的迭代顺序是栈顶→栈底，所以遇到花括号即可停。
     */
    private static int parensSinceBrace(Deque<Character> stack) {
        int count = 0
        for (Character c: stack) {
            if (c.charValue() == ('{' as char) || c.charValue() == INTERPOLATION) {
                break
            }
            if (c.charValue() == ('(' as char) || c.charValue() == ('[' as char)) {
                count++
            }
        }
        count
    }

    /**
     * 泛型通配符 {@code ?}：后面的第一个代码 token 是 {@code extends} / {@code super} / {@code >} / {@code ,}。
     *
     * <p>这是纯局部判据，不需要知道是否在泛型里 —— 三元运算符后面不会出现这四者
     * （{@code x ? extends y : z} 不是合法 Groovy）。
     */
    private static boolean isWildcardQuestion(List<CstToken> toks, int tokenIndex,
            ParseContextIndex parseIndex) {
        // 优先问 parse tree：泛型通配符一定在 typeArguments 之下，三元的 ? 不会。
        // 这比"下一个 token 是 extends/super/&gt;/就算通配符"可靠 —— 后者是一条假设（
        // 虽然语法上这四者不会跟在三元的 ? 后面，但那是推理出来的）。
        if (parseIndex != null) {
            return parseIndex.insideAt(toks[tokenIndex].start, 'typeArguments')
        }
        int next = nextCodeToken(toks, tokenIndex + 1)
        if (next < 0) {
            return false
        }
        int type = toks[next].type
        type == GroovyLexer.EXTENDS || type == GroovyLexer.SUPER ||
        type == GroovyLexer.GT || type == GroovyLexer.COMMA
    }

    /** 向后找第一个真正的代码 token（跳过注释与换行）。 */
    private static int nextCodeToken(List<CstToken> toks, int from) {
        for (int i = Math.max(from, 0); i < toks.size(); i++) {
            if (!toks[i].comment && !CstBuilder.isNewlineToken(toks[i])) {
                return i
            }
        }
        -1
    }

    /**
     * 独立的一遍扫描：算出每个 token 的行号、每行首 token、以及"最内层未闭合括号的打开位置"。
     *
     * <p>刻意与上面那个词法状态机分开 —— 那一遍已经够密了，这里只需要括号配对信息，
     * 重算一遍比把三个新的栈穿进去风险小得多。
     */
    private static void computeLineAndOpeners(List<CstToken> toks, boolean[] interpolationBrace,
            int[] opener, int[] line, List<Integer> firstOfLine) {
        Deque<Integer> openIdx = new ArrayDeque<>()
        openIdx.push(-1)
        int currentLine = 0
        for (int i = 0; i < toks.size(); i++) {
            CstToken t = toks[i]
            // 每一行都要记（含空行与纯注释行），否则行号会与下标错位
            if (firstOfLine.size() == currentLine) {
                firstOfLine.add(i)
            }
            opener[i] = openIdx.peek().intValue()
            line[i] = currentLine

            int type = t.type
            if (type == GroovyLexer.LBRACE && !interpolationBrace[i]) {
                openIdx.push(i)
            } else if (type == GroovyLexer.LPAREN || type == GroovyLexer.LBRACK) {
                openIdx.push(i)
            } else if (type == GroovyLexer.RBRACE || type == GroovyLexer.RPAREN
                    || type == GroovyLexer.RBRACK) {
                // GString 插值的 } 也不能 pop —— 它的 { 同样没 push（对称处理），
                // 否则会弹掉外面真正的方法体/类体花括号，让之后所有行的锚点整体错位。
                if (type != GroovyLexer.RBRACE || !interpolationBrace[i]) {
                    if (openIdx.size() > 1) {
                        openIdx.pop()
                    }
                }
            }
            if (CstBuilder.isNewlineToken(t)) {
                currentLine++
            }
        }
        if (firstOfLine.empty) {
            firstOfLine.add(0)
        }
    }

    /**
     * {@code for (X x : xs)} 里的那个冒号。
     *
     * <p>判据：向前找到<b>最近的未配对左括号</b>，它是这个冒号的直接外层；
     * 若它前面紧跟 {@code for} 关键字，就是 for-in 冒号。
     *
     * <p>为什么必须找"最近的未配对括号"而不是只看层数：{@code for (x in [a: 1])} 里的
     * 冒号外层是 {@code [} 而不是 for 的 {@code (}，那种情况不能算。
     */
    private static boolean isForInColon(List<CstToken> toks, int colonIndex,
            ParseContextIndex parseIndex) {
        // 优先问 parse tree：冒号是 enhancedForControl 的【直接】子节点，
        // 而 map 条目的冒号在 mapEntry 之下、for 头里三元的冒号在 expression 之下 ——
        // 用"最内层规则"恰好把这三者分开，不需要向后扫括号去猜。
        if (parseIndex != null) {
            return parseIndex.innermostAt(toks[colonIndex].start) == 'enhancedForControl'
        }
        int paren = 0
        int square = 0
        int curly = 0
        for (int i = colonIndex - 1; i >= 0; i--) {
            CstToken t = toks[i]
            if (t.comment || CstBuilder.isNewlineToken(t)) {
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
            if (type == GroovyLexer.RBRACE) {
                curly++
                continue
            }
            if (type == GroovyLexer.LPAREN) {
                if (paren > 0) {
                    paren--
                    continue
                }
                if (square != 0 || curly != 0) {
                    return false
                }
                int before = prevCodeToken(toks, i - 1)
                return before >= 0 && toks[before].type == GroovyLexer.FOR
            }
            if (type == GroovyLexer.LBRACK) {
                if (square > 0) {
                    square--
                    continue
                }
                // 最近的未配对括号是 [ —— 冒号的直接外层不是 for 的 ( ，例如 for (x in [a: 1])
                return false
            }
            if (type == GroovyLexer.LBRACE) {
                if (curly > 0) {
                    curly--
                    continue
                }
                // 最近的未配对括号是 { ，例如 for (x in list.collect { [a: 1] })
                return false
            }
        }
        false
    }

    /** 向前找第一个真正的代码 token（跳过注释与换行）。 */
    private static int prevCodeToken(List<CstToken> toks, int from) {
        for (int i = Math.min(from, toks.size() - 1); i >= 0; i--) {
            if (!toks[i].comment && !CstBuilder.isNewlineToken(toks[i])) {
                return i
            }
        }
        -1
    }

    private static void open(Deque<Character> stack, Deque<Boolean> ternary, char c) {
        stack.push(c)
        ternary.push(Boolean.FALSE)
    }

    private static void close(Deque<Character> stack, Deque<Boolean> ternary) {
        if (stack.size() > 1) {
            stack.pop()
        }
        if (ternary.size() > 1) {
            ternary.pop()
        }
    }

    private static int prevCodeIndex(TokenRole[] roles, int from) {
        for (int i = from; i >= 0; i--) {
            if (roles[i] != TokenRole.COMMENT && roles[i] != TokenRole.NEWLINE) {
                return i
            }
        }
        -1
    }

    /** 这个 {@code {} 是不是 GString 的 {@code ${} 插值开头（前一个 token 是结尾带 $ 的 GString token）。 */
    private static boolean isInterpolationStart(List<CstToken> toks, TokenRole[] roles, int i) {
        for (int j = i - 1; j >= 0; j--) {
            if (roles[j] == TokenRole.COMMENT || roles[j] == TokenRole.NEWLINE) {
                continue
            }
            CstToken prev = toks[j]
            return GSTRING_TOKENS.contains(prev.type) && prev.text.endsWith('$')
        }
        false
    }

    static boolean endsValue(int type) {
        VALUE_END_TYPES.contains(type)
    }

    private static TokenRole classifyToken(int prevType, int type) {
        if (type == GroovyLexer.COMMA) {
            return TokenRole.COMMA
        }
        if (type == GroovyLexer.SEMI) {
            return TokenRole.SEMICOLON
        }
        if (type == GroovyLexer.DOT || type == GroovyLexer.SAFE_DOT
                || type == GroovyLexer.SPREAD_DOT || type == GroovyLexer.SAFE_CHAIN_DOT
                || type == GroovyLexer.METHOD_POINTER) {
            return TokenRole.DOT_LIKE
        }
        if (type == GroovyLexer.AT) {
            return TokenRole.ANNOTATION_AT
        }
        if (type == GroovyLexer.NOT || type == GroovyLexer.BITNOT) {
            return TokenRole.UNARY_PREFIX
        }
        if (type == GroovyLexer.INC || type == GroovyLexer.DEC) {
            return endsValue(prevType) ? TokenRole.UNARY_POSTFIX : TokenRole.UNARY_PREFIX
        }
        if (type == GroovyLexer.ADD || type == GroovyLexer.SUB || type == GroovyLexer.MUL) {
            return endsValue(prevType) ? TokenRole.BINARY_OP : TokenRole.UNARY_PREFIX
        }
        if (BINARY_OPERATORS.contains(type)) {
            return TokenRole.BINARY_OP
        }
        if (KEYWORDS.contains(type)) {
            return TokenRole.KEYWORD
        }
        TokenRole.OTHER
    }

}
