package io.github.heyuch.groovyfmt.layout

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.annotate.ParseContextIndex
import io.github.heyuch.groovyfmt.model.FormatStyle

/**
 * 折行（line wrapping）。
 *
 * <h3>为什么单独一层，而不是塞进 gap 重写</h3>
 * 因为折行会<b>插入换行</b>，而 gap 重写只改"已有换行的空白内容"。把它做成
 * "先按正常流程排版 → 再按行长切分行 → 用格式化器重新缩进"的后处理，
 * 好处是<b>缩进零新代码</b>：切出来的续行拿到的缩进，正是现有锚点模型算出来的那个
 * （实参续行 = 括号锚点 + 8；点链 / 三元续行 = 语句首行 + 8）。
 *
 * <h3>安全论证换了另一条</h3>
 * 正常排版的不变量是"绝不改变换行的位置"，折行恰恰要打破它。所以校验必须换成：
 * <pre>
 *   ① 忽略换行后，token 序列完全一致
 *   ② parse tree 形状完全一致
 * </pre>
 * 因为 Groovy 的 NL 是语法级的，②能抓住"换行插在有害位置"这一类唯一的风险。
 * 本类只负责"提议断点"，是否接受由调用方的闸门裁决 —— 断点识别是词法的，
 * 裁决是语法的。
 *
 * <h3>不可断开的行</h3>
 * 找不到断点（典型：一个超长字符串字面量）就<b>原样保留</b>。宁可留下违规，也不猜。
 */
@CompileStatic
class LineWrapper {

    /** 反复折行的上限，防止病态输入下无限循环。 */
    private static final int MAX_PASSES = 400

    private LineWrapper() {
    }

    /** 折行到每行不超过 {@code style.maxLineLength}；未启用（<=0）时原样返回。 */
    static String wrap(String source, FormatStyle style) {
        wrap(source, style, null)
    }

    /**
     * 折行到每行不超过 {@code style.maxLineLength}。
     *
     * <p>{@code index} 用来限制断点的【语境】：不得断在闭包内、
     * 也不得断在 for 头里 —— 这两类位置上插入换行会让
     * CodeNarc 的 ClosureStatementOnOpeningLineOfMultipleLineClosure / BracesForForLoop 报警
     * （都是实测新增的违规）。
     */
    // NestedForLoop：外层是「多轮直到收敛」、内层是「找第一条超限且可断的行」，
    // 拆成两个方法反而把迭代进展隐藏掉；属非格式化规则。
    @SuppressWarnings('NestedForLoop')
    static String wrap(String source, FormatStyle style, ParseContextIndex index) {
        // 索引不可用时【拒绝折行】：断点的语境过滤全部依赖它，
        // 没有它就等于“在闭包里、for 头里也敢断”。宁可留下超长行。
        if (style.maxLineLength <= 0 || source == null || source.empty || index == null) {
            return source
        }
        String eol = source.contains('\r\n') ? '\r\n' : '\n'
        String current = source
        boolean changed = false
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            // 每一轮只处理一行，且偏移从【当前文本】重算。
            // 曾经用"一轮扫所有行 + 跑动计数器"，但同一轮里前面的行被切分后
            // 后续行的偏移就漂了 —— 于是"闭包内不得断"的过滤会失效。
            List<String> lines = new ArrayList<>(Arrays.asList(current.split('\n', -1)))
            int offset = 0
            int target = -1
            int targetOffset = 0
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines[i]
                String text = raw.endsWith('\r') ? raw.substring(0, raw.length() - 1) : raw
                if (text.length() > style.maxLineLength
                        && bestBreakAtOrBefore(text, style.maxLineLength, offset, index) > 0) {
                    target = i
                    targetOffset = offset
                    break
                }
                offset += raw.length() + 1
            }
            if (target < 0) {
                break
            }
            String raw = lines[target]
            String text = raw.endsWith('\r') ? raw.substring(0, raw.length() - 1) : raw
            int cut = bestBreakAtOrBefore(text, style.maxLineLength, targetOffset, index)
            def rebuilt = new ArrayList<String>(lines.size() + 1)
            rebuilt.addAll(lines.subList(0, target))
            rebuilt << text.substring(0, cut)
            rebuilt << text.substring(cut).replaceAll(/^\s+/, '')
            rebuilt.addAll(lines.subList(target + 1, lines.size()))
            current = rebuilt.join(eol)
            changed = true
        }
        changed ? current : source
    }

    /**
     * 在 {@code limit} 之前（含）最后一个可插入换行的位置；没有则返回 -1。
     *
     * <p>断点只有三类，都是"插进去语法不会变"的位置：
     * <ul>
     *   <li>逗号<b>之后</b>（实参 / 形参列表）</li>
     *   <li>点<b>之前</b>（调用链）</li>
     *   <li>三元 {@code ?} / {@code :} <b>之前</b></li>
     * </ul>
     * 字符串、注释与正则字面量内部一律不作为断点 —— 这靠一个小扫描器跟踪引号与注释状态，
     * 而不是靠猜。
     */
    // CyclomaticComplexity：这是一个字符级扫描器（引号 / 注释 / 正则状态 + 三类断点），
    // 分支多是本质的；且属非格式化规则。
    @SuppressWarnings('CyclomaticComplexity')
    /**
     * 选择最优断点。
     *
     * <p>规则：先看哪些候选【切完两边都放得下】，在这些里按
     * <b>元素类型优先级</b>（赋值 &gt; 逗号 &gt; 三元 &gt; 运算符 &gt; 成员链）选，
     * 同类内再按【嵌套越浅越好】，最后 greedy（尽量晚）；
     * 若一个都放不下，则取优先级最高者。
     *
     * <p>为什么不能让深度跨类型地霸道：实参列表
     * {@code dslClient.saveAllTheThings(a, b, c)} 里顶层的 {@code .} 比里面的逗号更浅，
     * 但断在它上面会把整个实参列表留给下一行（仍然超限）。
     * "两边都放得下"这个硬条件自然地把它排除掉了。
     */
    private static int bestBreakAtOrBefore(String line, int limit, int lineOffset,
            ParseContextIndex index) {
        List<BreakPoint> all = collectBreakPoints(line, lineOffset, index)
        if (all.empty) {
            return -1
        }
        List<BreakPoint> fitting = all.findAll { BreakPoint b ->
            b.position <= limit && (line.length() - b.position) <= limit
        }
        List<BreakPoint> pool = fitting ?: all
        pool.sort { BreakPoint a, BreakPoint b ->
            a.priority != b.priority ? (a.priority <=> b.priority) :
            (a.depth != b.depth ? (a.depth <=> b.depth) : (b.position <=> a.position))
        }
        pool[0].position
    }

    /** 一个候选断点。 */
    private static final class BreakPoint {

        final int position
        final int depth
        final int priority

        BreakPoint(int position, int depth, int priority) {
            this.position = position
            this.depth = depth
            this.priority = priority
        }

    }

    /** 赋值优先级最高（把"算什么"与"怎么算"分开）；成员链最低。 */
    private static final int PRIO_ASSIGNMENT = 0
    private static final int PRIO_COMMA = 1
    private static final int PRIO_TERNARY = 2
    private static final int PRIO_OPERATOR = 3
    private static final int PRIO_INNER_ASSIGNMENT = 4
    private static final int PRIO_DOT = 5

    private static final String OP_CHARS = '=+-*/%&|^<>?!'

    private static final Set<String> ASSIGN_OPS = [
            '=', '+=', '-=', '*=', '/=', '%=', '**=', '&=', '|=', '^=', '<<=', '>>=', '>>>=', '?='] as Set<String>

    /**
     * 收集一行里的候选断点，带嵌套深度与类型优先级。
     *
     * <p>字符串 / 注释 / 正则字面量内部一律不作为断点：靠一个小扫描器跟踪状态，而不是猜。
     */
    @SuppressWarnings('CyclomaticComplexity')
    private static List<BreakPoint> collectBreakPoints(String line, int lineOffset,
            ParseContextIndex index) {
        def out = new ArrayList<BreakPoint>()
        int i = 0
        int n = line.length()
        int depth = 0
        QuoteState state = QuoteState.NONE
        while (i < n) {
            char c = line.charAt(i)
            if (state == QuoteState.NONE) {
                if (c == ('/' as char) && i + 1 < n && line.charAt(i + 1) == ('/' as char)) {
                    break
                }
                if (c == ('\'' as char) || c == ('"' as char)) {
                    state = c == ('\'' as char) ? QuoteState.SINGLE : QuoteState.DOUBLE
                    i++
                    continue
                }
                if (c == ('/' as char) && isRegexStart(line, i)) {
                    state = QuoteState.REGEX
                    i++
                    continue
                }
                if (c == ('(' as char) || c == ('[' as char) || c == ('{' as char)) {
                    depth++
                } else if (c == (')' as char) || c == (']' as char) || c == ('}' as char)) {
                    depth = Math.max(0, depth - 1)
                } else if (c == (',' as char)) {
                    // 泛型参数表里的逗号不能断：会把 Map<String, String> 切开。
                    if (index == null || !index.insideAt(lineOffset + i, 'typeArguments')) {
                        addBreak(out, i + 1, depth, PRIO_COMMA, lineOffset, index)
                    }
                } else if (isBinaryOperatorAt(line, i)) {
                    int length = binaryOperatorLength(line, i)
                    // 只在嵌套深度 0 的语句级表达式上断，且断在运算符之后。
                    if (depth == 0
                            && (index == null || !index.insideAt(lineOffset + i, 'typeArguments'))) {
                        addBreak(out, i + length, depth, PRIO_OPERATOR, lineOffset, index)
                    }
                    i += length - 1
                } else if (c == ('.' as char) || c == ('?' as char) || c == (':' as char)) {
                    int prio = c == ('.' as char) ? PRIO_DOT : PRIO_TERNARY
                    // 不允许在链的【头部】断开：前缀里必须已经出现过 . ( ) ]，
                    // 否则 `list.each { ... }` 会被切成 `list` + `.each { ... }`（前半只剩接收者）。
                    if (c != ('.' as char) || isChainTail(line, i)) {
                        addBreak(out, i, depth, prio, lineOffset, index)
                    }
                } else if (isAssignmentAt(line, i)) {
                    // 语句级赋值（深度 0）把"算什么"与"怎么算"分开，优先级最高；
                    // 但【括号内】的赋值（注解属性 @X(in = ...)、命名实参、括号内表达式）
                    // 并不是语句的主分割点：实测 @Parameter(..., in = ParameterIn.HEADER) 在属性的
                    // 逗号处断开才对，而我们原来在 `=` 后面断开。
                    int prio = depth == 0 ? PRIO_ASSIGNMENT : PRIO_INNER_ASSIGNMENT
                    addBreak(out, i + 1, depth, prio, lineOffset, index)
                }
            } else if (c == ('\\' as char)) {
                i += 2
                continue
            } else if (closes(state, c)) {
                state = QuoteState.NONE
            }
            i++
        }
        out
    }

    /** 该 {@code .} 是否在链的尾部（而不是把接收者单独切出去）。 */
    private static boolean isChainTail(String line, int dotIndex) {
        for (int i = 0; i < dotIndex; i++) {
            char c = line.charAt(i)
            if (c == ('.' as char) || c == ('(' as char) || c == (')' as char) || c == (']' as char)) {
                return true
            }
        }
        false
    }

    /** 加入一个候选，但先过掉两类语境：闭包内、for 头内。 */
    // ParameterCount：就是一个候选的接入口（位置/深度/优先级 + 行住置 + 索引），
    // 打包成对象反而多一层间接；属非格式化规则。
    /** 这里是否是可断行的【二元】运算符（前一个有效字符能结束操作数）。 */
    private static boolean isBinaryOperatorAt(String line, int i) {
        binaryOperatorLength(line, i) > 0
    }

    /** 二元运算符长度；0 表示不是。一元 +/- 不算。 */
    private static int binaryOperatorLength(String line, int i) {
        String[] ops = ['>>>', '>>=', '<<=', '===', '!==', '<=>', '**=', '&&', '||', '==', '!=',
                '<=', '>=', '<<', '>>', '+=', '-=', '*=', '/=', '%=', '+', '-', '*', '/',
                '%', '<', '>']
        String rest = line.substring(i)
        for (String op: ops) {
            if (rest.startsWith(op) && endsOperand(line, i)) {
                return op.length()
            }
        }
        0
    }

    /** {@code i} 之前最后一个非空字符是否能结束一个操作数。 */
    private static boolean endsOperand(String line, int i) {
        int j = i - 1
        while (j >= 0 && Character.isWhitespace(line.charAt(j))) {
            j--
        }
        if (j < 0) {
            return false
        }
        char c = line.charAt(j)
        Character.isLetterOrDigit(c) || c == ('_' as char) || c == (')' as char)
        || c == (']' as char) || c == ('\'' as char) || c == ('"' as char)
    }

    @SuppressWarnings('ParameterCount')
    private static void addBreak(List<BreakPoint> out, int position, int depth, int priority,
            int lineOffset, ParseContextIndex index) {
        if (index != null) {
            int absolute = lineOffset + position
            if (index.insideAt(absolute, 'closure')
                    || index.insideAt(absolute, 'forControl')
                    || index.insideAt(absolute, 'enhancedForControl')) {
                return
            }
        }
        out << new BreakPoint(position, depth, priority)
    }

    /** {@code line} 在 {@code index} 处是否是赋值运算符（排除 {@code ==} / {@code !=} / {@code <=} / {@code >=} 等）。 */
    private static boolean isAssignmentAt(String line, int index) {
        int n = line.length()
        int j = index
        def op = new StringBuilder()
        while (j < n && OP_CHARS.indexOf((int) line.charAt(j)) >= 0) {
            op.append(line.charAt(j))
            j++
        }
        op.toString().endsWith('=') && ASSIGN_OPS.contains(op.toString())
    }

    private enum QuoteState {

        NONE, SINGLE, DOUBLE, REGEX

    }

    private static boolean closes(QuoteState state, char c) {
        (state == QuoteState.SINGLE && c == ('\'' as char)) ||
        (state == QuoteState.DOUBLE && c == ('"' as char)) ||
        (state == QuoteState.REGEX && c == ('/' as char))
    }

    /** 粗略判断 {@code /} 是不是正则字面量的开头：前一个非空白字符不能是标识符或右括号。 */
    private static boolean isRegexStart(String line, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char p = line.charAt(i)
            if (p == (' ' as char) || p == ('\t' as char)) {
                continue
            }
            return !(Character.isJavaIdentifierPart(p) || p == (')' as char) || p == (']' as char))
        }
        true
    }

}
