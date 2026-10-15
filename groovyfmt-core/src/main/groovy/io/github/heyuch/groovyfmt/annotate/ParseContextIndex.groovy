package io.github.heyuch.groovyfmt.annotate

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovyjarjarantlr4.v4.runtime.CharStreams
import groovyjarjarantlr4.v4.runtime.CommonTokenStream
import groovyjarjarantlr4.v4.runtime.ParserRuleContext
import groovyjarjarantlr4.v4.runtime.tree.ParseTree

import org.apache.groovy.parser.antlr4.GroovyLangLexer
import org.apache.groovy.parser.antlr4.GroovyLangParser

/**
 * 语法上下文索引：<b>按字符偏移回答"这里在哪个语法构造里"</b>，数据来自 ANTLR 的 parse tree。
 *
 * <h3>为什么是 parse tree，而不是 Groovy AST</h3>
 * 两者我们都已经在建（安全闸门 #2 用 parse tree，闸门 #3 用真编译器到 CONVERSION）。
 * 选 parse tree 的理由是它<b>逐 token 保真</b>：
 * <ul>
 *   <li>注释与关键字都还在（注释在 Groovy 词法里是 {@code NL} 类 token）；Groovy AST 没有注释节点。</li>
 *   <li>它是<b>语法</b>结构，而"这个冒号是 map 条目还是 for-in""这个 {@code ?} 是泛型通配符还是三元"
 *       恰恰是语法问题 —— 语义层的类型解析对此毫无帮助。</li>
 *   <li>它的规则名<b>本身就是分类结果</b>。</li>
 * </ul>
 *
 * <h3>为什么这比现在的做法可靠</h3>
 * 我们至今每一个分类 bug 都是"靠 token 贴邻猜构造"猜错的：
 * <pre>
 *   泛型通配符 ? 被当成运算符      -> parse tree: typeArgument / typeArguments
 *   for-in 冒号被当成 map 条目     -> parse tree: enhancedForControl vs mapEntry
 *   闭包 -&gt; 被当成行尾运算符       -> parse tree: closure
 *   一元加语句被当成续行           -> parse tree: statement（且该节点【正好起始于】这一 token）
 *</pre>
 * 这些都是**按名字**读出来的，不是猜的。
 *
 * <h3>使用纪律：先只让它"否决"</h3>
 * 曾经把续行判据整体交给 AST 推导，结果 68 个 fixture 全过、真实代码的 CodeNarc Indentation
 * 却从 0 涨到 161。原因是推导出来的区间只是<b>近似</b>我们想要的构造。
 * parse tree 的规则区间是精确的，但在证明之前仍按同一纪律使用：<b>让它否决，不让它肯定</b>。
 */
@CompileStatic
class ParseContextIndex {

    private static final String[] RULE_NAMES = GroovyLangParser.ruleNames

    private static final class Node {

        final int ruleId
        final int parent
        final int start
        final int stop

        Node(int ruleId, int parent, int start, int stop) {
            this.ruleId = ruleId
            this.parent = parent
            this.start = start
            this.stop = stop
        }

    }

    private final Node[] nodes
    /** 每个字符偏移上【最深】的规则节点 id（-1 = 没有）。 */
    private final int[] deepest
    private final int capacity

    private ParseContextIndex(Node[] nodes, int[] deepest, int capacity) {
        this.nodes = nodes
        this.deepest = deepest
        this.capacity = capacity
    }

    /** 构建失败返回 {@code null} —— 调用方必须容忍，绝不能因此改变格式化行为。 */
    @SuppressWarnings(['CatchThrowable', 'FactoryMethodName'])
    static ParseContextIndex build(String source) {
        if (source == null || source.empty) {
            return null
        }
        try {
            def lexer = new GroovyLangLexer(CharStreams.fromString(source))
            lexer.removeErrorListeners()
            def parser = new GroovyLangParser(new CommonTokenStream(lexer))
            parser.removeErrorListeners()
            def tree = parser.compilationUnit()

            int cap = source.length()
            def nodeList = new ArrayList<Node>()
            int[] deepest = new int[cap]
            int[] depthAt = new int[cap]
            Arrays.fill(deepest, -1)
            Arrays.fill(depthAt, -1)
            visit(tree, 0, -1, nodeList, deepest, depthAt, cap)

            def arr = new Node[nodeList.size()]
            for (int i = 0; i < arr.length; i++) {
                arr[i] = nodeList.get(i)
            }
            new ParseContextIndex(arr, deepest, cap)
        } catch (Throwable ignored) {
            null
        }
    }

    // Instanceof：这里就是在遍历 ANTLR 的节点类型，instanceof 正是意图本身；属非格式化规则。
    @SuppressWarnings(['ParameterCount', 'Instanceof'])
    private static void visit(ParseTree node, int depth, int parent, List<Node> nodeList,
            int[] deepest, int[] depthAt, int cap) {
        int myId = parent
        if (node instanceof ParserRuleContext) {
            ParserRuleContext rc = (ParserRuleContext) node
            int start = rc.start == null ? -1 : rc.start.startIndex
            int stop = rc.stop == null ? start : rc.stop.stopIndex
            if (start >= 0) {
                myId = nodeList.size()
                nodeList.add(new Node(rc.ruleIndex, parent, start, stop))
                int from = Math.max(0, start)
                int to = Math.min(cap - 1, stop)
                for (int i = from; i <= to; i++) {
                    if (depth > depthAt[i]) {
                        depthAt[i] = depth
                        deepest[i] = myId
                    }
                }
            }
        }
        int count = node.childCount
        for (int i = 0; i < count; i++) {
            visit(node.getChild(i), depth + 1, myId, nodeList, deepest, depthAt, cap)
        }
    }

    /** 该偏移处最内层的规则名；没有则 {@code null}。 */
    @Nullable
    String innermostAt(int offset) {
        int id = nodeIdAt(offset)
        id < 0 ? null : RULE_NAMES[nodes[id].ruleId]
    }

    /** 该偏移是否位于（含）某个规则名之下。 */
    boolean insideAt(int offset, String ruleName) {
        int id = nodeIdAt(offset)
        while (id >= 0) {
            if (RULE_NAMES[nodes[id].ruleId] == ruleName) {
                return true
            }
            id = nodes[id].parent
        }
        false
    }

    /**
     * 是否有某个规则名<b>正好起始于</b>该偏移。
     *
     * <p>这条是"这一 token 是否开启了一个新构造"的精确答案。例如
     * {@code def s = a} 换行后的 {@code + b} 会让 {@code statement} 正好起始于 {@code +}，
     * 而 {@code a +} 换行后的操作数不会 —— 这正是词法判据区分不了的那一条。
     */
    boolean ruleStartsAt(int offset, String ruleName) {
        int id = nodeIdAt(offset)
        while (id >= 0) {
            Node n = nodes[id]
            if (n.start == offset && RULE_NAMES[n.ruleId] == ruleName) {
                return true
            }
            id = n.parent
        }
        false
    }

    private int nodeIdAt(int offset) {
        offset >= 0 && offset < capacity ? deepest[offset] : -1
    }

}
