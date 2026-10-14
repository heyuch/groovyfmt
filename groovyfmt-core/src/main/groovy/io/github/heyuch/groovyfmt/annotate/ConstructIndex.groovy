package io.github.heyuch.groovyfmt.annotate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.CaseStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.SynchronizedStatement
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit

/**
 * 主体索引（Subject Index）：<b>每一行属于哪个语法构造</b>。
 *
 * <h3>为什么需要它</h3>
 * 我们至今踩到的每一个缩进/间距 bug，性质都是<b>主体识别错误</b>，而不是"改写 gap 的机制错了"：
 * <pre>
 *   带注解的方法参数被当成字段      → 于是在参数之间插空行
 *   闭包参数列表的 -&gt; 被当成行尾运算符 → 于是闭包体首行被当成续行
 *   Set&lt;String&gt; 的 &gt; 被当成比较运算符  → 于是后面每一行越缩越深
 *   一元加表达式语句被当成续行      → 于是它多缩进一级
 * </pre>
 * 这些都是"这一行到底在什么构造里"的问题 —— <b>是语法问题，只有 parser 能准确回答</b>。
 * 词法层面再怎么加特例，也只是在猜。
 *
 * <h3>边界（刻意划清）</h3>
 * 本类只负责<b>给构造命名</b>，不负责"输出长什么样"：
 * <ul>
 *   <li><b>不</b>从 AST 生成文本 —— Groovy 的 AST 是编译中间表示而非源码回声
 *       （{@code def x} 在 AST 里是 {@code java.lang.Object x}，下标访问会被脱糖成 {@code getAt}，
 *       显式括号不保留为节点），打印回去必然改动非空白内容，会直接破坏
 *       "输出 = 输入，差异只在空白"这条安全契约。</li>
 *   <li>注释与空行<b>不是</b> AST 节点，所以它们仍然只能靠 token 层判断。</li>
 *   <li>因此：<b>token 流仍是唯一真相</b>（谁存在、什么顺序），AST 只回答"这是什么构造"。</li>
 * </ul>
 *
 * <h3>为什么把 BlockStatement 排除在"语句"之外</h3>
 * 块只是容器。若把块也算作语句，那么"某行被哪条语句覆盖"在嵌套块里会答成最外层的块，
 * 续行就会锚到错误的地方。排除之后，"覆盖某一行的最内层非块语句"恰好就是
 * <b>那一行正在延续的那条语句</b>。
 */
@CompileStatic
class ConstructIndex {

    /** 一个构造的行区间。行号从 0 开始（与 {@code Annotated.lineOf} 一致）。 */
    static final class Span {

        final String kind
        final int startLine
        final int endLine

        Span(String kind, int startLine, int endLine) {
            this.kind = kind
            this.startLine = startLine
            this.endLine = endLine
        }

    }

    /** 行数 → 覆盖该行的最内层构造下标（-1 表示没有）。 */
    private final int[] innermost
    private final List<Span> spans
    /** 该行是否是某条语句/声明的【起始行】。 */
    private final boolean[] statementStart
    private final int lineCount

    private ConstructIndex(int lineCount, List<Span> spans) {
        this.lineCount = lineCount
        this.spans = spans
        this.innermost = new int[lineCount]
        this.statementStart = new boolean[lineCount]
        Arrays.fill(innermost, -1)

        // 先按区间【从大到小】铺一遍，再铺小的 —— 于是留下的就是最内层。
        def sorted = new ArrayList<Span>(spans)
        sorted.sort { Span a, Span b -> (b.endLine - b.startLine) <=> (a.endLine - a.startLine) }
        sorted.each { Span s ->
            int idx = spans.indexOf(s)
            for (int line = Math.max(0, s.startLine); line <= Math.min(lineCount - 1, s.endLine); line++) {
                innermost[line] = idx
            }
        }
        spans.each { Span s ->
            if (s.startLine >= 0 && s.startLine < lineCount) {
                statementStart[s.startLine] = true
            }
        }
    }

    /** 构建失败时返回 {@code null} —— 调用方必须容忍，绝不能因此改变格式化行为。 */
    // FactoryMethodName：这里是一个纯构造函数，取名 build 最清晰；
    // 属非格式化规则，带理由豁免。
    @SuppressWarnings(['CatchThrowable', 'FactoryMethodName'])
    static ConstructIndex build(String source) {
        if (source == null || source.trim().empty) {
            return null
        }
        try {
            def cu = new CompilationUnit(new CompilerConfiguration())
            cu.addSource('ConstructIndex.groovy', source)
            cu.compile(Phases.CONVERSION)
            List<Span> spans = []
            def collector = new Collector(spans)
            for (ClassNode cn: classesOf(cu)) {
                collector.visitClass(cn)
            }
            new ConstructIndex(countLines(source), spans)
        } catch (Throwable ignored) {
            null
        }
    }

    /**
     * 取编译单元里的类节点。
     *
     * <p>刻意用 {@code @CompileDynamic}：{@code CompilationUnit#ast} 属于内部 API，
     * 静态类型检查会直接禁掉这次访问（"Access to ...#ast is forbidden"）。
     * 这里只需要读一次模块列表，动态调用是合适的。
     */
    @CompileDynamic
    private static List<ClassNode> classesOf(CompilationUnit cu) {
        List<ClassNode> out = []
        cu.ast.modules.each { m -> out.addAll(m.classes) }
        out
    }

    private static int countLines(String source) {
        int n = 1
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == ('\n' as char)) {
                n++
            }
        }
        n
    }

    /** 该行是否是某条语句/声明的起始行。 */
    boolean startsStatement(int line) {
        line >= 0 && line < lineCount && statementStart[line]
    }

    /** 覆盖该行的最内层非块构造；没有则 {@code null}。 */
    Span enclosing(int line) {
        if (line < 0 || line >= lineCount) {
            return null
        }
        int idx = innermost[line]
        idx < 0 ? null : spans[idx]
    }

    /**
     * 该行是否是"某条语句的续行"：<b>它自己不开始语句，却被某条语句覆盖</b>。
     *
     * <p>这正是词法判据做不到的那条判断 —— 例如 {@code def s = a +} 换行后的 {@code b}，
     * 与 {@code def s = a} 换行后的 {@code + b}（那是一条一元加表达式语句）。
     */
    boolean isContinuation(int line) {
        if (startsStatement(line)) {
            return false
        }
        Span e = enclosing(line)
        e != null && e.startLine < line
    }

    int spanCount() {
        spans.size()
    }

    // ─────────────────── 收集：只认"表达型"语句与字段/属性 ───────────────────

    private static final class Collector extends ClassCodeVisitorSupport {

        private final List<Span> out

        Collector(List<Span> out) {
            this.out = out
        }

        @Override
        protected SourceUnit getSourceUnit() {
            null                     // 这里不需要源码单元；CodeNarc 那个坑不适用
        }

        private void mark(String kind, ASTNode node) {
            int start = node.lineNumber
            int end = node.lastLineNumber
            if (start <= 0 || end <= 0 || end < start) {
                return                   // 位置未知：宁可少记，也不记错
            }
            out << new Span(kind, start - 1, end - 1)
        }

        @Override
        void visitExpressionStatement(ExpressionStatement node) {
            mark('ExpressionStatement', node)
            super.visitExpressionStatement(node)
        }

        @Override
        void visitReturnStatement(ReturnStatement node) {
            mark('ReturnStatement', node)
            super.visitReturnStatement(node)
        }

        @Override
        void visitThrowStatement(ThrowStatement node) {
            mark('ThrowStatement', node)
            super.visitThrowStatement(node)
        }

        @Override
        void visitAssertStatement(AssertStatement node) {
            mark('AssertStatement', node)
            super.visitAssertStatement(node)
        }

        @Override
        void visitIfElse(IfStatement node) {
            mark('IfStatement', node)
            super.visitIfElse(node)
        }

        @Override
        void visitForLoop(ForStatement node) {
            mark('ForStatement', node)
            super.visitForLoop(node)
        }

        @Override
        void visitWhileLoop(WhileStatement node) {
            mark('WhileStatement', node)
            super.visitWhileLoop(node)
        }

        @Override
        void visitDoWhileLoop(DoWhileStatement node) {
            mark('DoWhileStatement', node)
            super.visitDoWhileLoop(node)
        }

        @Override
        void visitSwitch(SwitchStatement node) {
            mark('SwitchStatement', node)
            super.visitSwitch(node)
        }

        @Override
        void visitCaseStatement(CaseStatement node) {
            mark('CaseStatement', node)
            super.visitCaseStatement(node)
        }

        @Override
        void visitTryCatchFinally(TryCatchStatement node) {
            mark('TryCatchStatement', node)
            super.visitTryCatchFinally(node)
        }

        @Override
        void visitSynchronizedStatement(SynchronizedStatement node) {
            mark('SynchronizedStatement', node)
            super.visitSynchronizedStatement(node)
        }

        // 刻意【不覆盖】visitBlockStatement / visitEmptyStatement：
        // 块是容器，把它算成"语句"会让嵌套块里的续行锚到最外层的块上。

        @Override
        void visitField(FieldNode node) {
            mark('Field', node)
            super.visitField(node)
        }

        @Override
        void visitProperty(PropertyNode node) {
            mark('Property', node)
            super.visitProperty(node)
        }

        @Override
        void visitCatchStatement(CatchStatement node) {
            mark('CatchStatement', node)
            super.visitCatchStatement(node)
        }

    }

}
