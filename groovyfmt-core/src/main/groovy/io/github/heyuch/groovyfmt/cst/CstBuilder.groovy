package io.github.heyuch.groovyfmt.cst

import groovy.transform.CompileStatic
import groovyjarjarantlr4.v4.runtime.ANTLRErrorListener
import groovyjarjarantlr4.v4.runtime.BaseErrorListener
import groovyjarjarantlr4.v4.runtime.CharStreams
import groovyjarjarantlr4.v4.runtime.CommonTokenStream
import groovyjarjarantlr4.v4.runtime.RecognitionException
import groovyjarjarantlr4.v4.runtime.Recognizer
import groovyjarjarantlr4.v4.runtime.Token

import org.apache.groovy.parser.antlr4.GroovyLangLexer
import org.apache.groovy.parser.antlr4.GroovyLangParser
import org.apache.groovy.parser.antlr4.GroovyLexer

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

/**
 * 用 Groovy 自带的 ANTLR4 lexer/parser 构造无损 CST。
 *
 * <p>关键点：{@code GroovyParser} 被 relocate 进 groovy jar
 * （{@code groovyjarjarantlr4.v4.runtime}），所以不需要额外依赖 ANTLR runtime，
 * 也不需要 fork 语法文件。
 *
 * <p><b>三道闸门</b>：
 * <ol>
 *   <li><b>词法监听器</b> —— 任何 token recognition error 直接拒绝；</li>
 *   <li><b>ANTLR 语法树</b> —— {@code GroovyParser.compilationUnit()}</li>
 *   <li><b>真正的 Groovy 编译器</b> —— {@code CompilationUnit.compile(Phases.CONVERSION)}，
 *       这才是"语法正确"的权威定义；</li>
 *   <li><b>无损断言</b> —— token 区间 + gap 拼回原文必须逐字节一致。</li>
 * </ol>
 *
 * <p><b>血泪教训</b>：词法器必须有 error listener。ANTLR 的词法错误恢复会<b>静默跳过</b>
 * 无法识别的字符 —— 也就是说两份文本可以有完全相同的 token 序列，其中一份却是非法的。
 * 曾经因为漏掉这个监听器，把 {@code "${x}"} 改成了 {@code "$ {x}"}：
 * token 序列没变、ANTLR 语法树也没变，但真正的 Groovy 编译器直接拒绝。
 */
@CompileStatic
class CstBuilder {

    private static final String SOURCE_NAME = 'Source.groovy'

    private CstBuilder() {
    }

    @SuppressWarnings('CatchThrowable')   // 有意为之：必须捕获一切才能真正 fail-closed
    static Cst build(String source) throws GroovySyntaxException {
        def errors = new ArrayList<String>()

        // 必须用 GroovyLangLexer / GroovyLangParser —— 这是 Groovy 编译器实际使用的那一对。
        // 基类 GroovyLexer 在「GString 紧跟方法调用」（如 "a $b".toString()）这类模式上会误报
        // token recognition error，而 GroovyLang* 带有正确的上下文消歧逻辑。
        // 这个坑是【自举】逼出来的：之前 17 个真实文件的语料都没触发它。
        def lexer = new GroovyLangLexer(CharStreams.fromString(source))
        // errorIgnored=true：让非法字符走 UNEXPECTED_CHAR token，而不是在 scan-ahead 里直接抛异常。
        // 这样错误能带精确位置，而且下面还有 token 类型检查兜底。
        // CodeNarc UnnecessarySetter 的建议：Groovy 属性语法更地道
        lexer.errorIgnored = true
        lexer.removeErrorListeners()
        lexer.addErrorListener(new LexerErrors(errors))
        def stream = new CommonTokenStream(lexer)

        def parser = new GroovyLangParser(stream)
        parser.removeErrorListeners()
        parser.addErrorListener(new ParserErrors(errors))

        String treeShape = ''
        try {
            def tree = parser.compilationUnit()
            treeShape = tree.toStringTree(parser)
        } catch (Throwable t) {
            errors << "${t.class.simpleName}: ${t.message}".toString()
        }

        if (errors) {
            throw new GroovySyntaxException(errors)
        }

        def tokens = new ArrayList<CstToken>()
        def gaps = new ArrayList<Gap>()
        int cursor = 0
        stream.tokens.each { Token t ->
            if (t.type == Token.EOF) {
                return
            }
            // GroovyLangLexer 的 PositionAdjustingLexerATNSimulator 会插入 RollBackOne 伪 token，
            // 语义是"上一个 token 的结束位置回退一个字符"，真正的内容由紧随其后的 token 覆盖。
            // 它不是真实内容，必须丢弃 —— 否则 GString 的裸 $name 形式（如 "line $a:$b $c"）
            // 会导致 token 区间互相重叠，CST 就不是无损的了。
            if (t.type == GroovyLexer.RollBackOne) {
                return
            }
            int s = t.startIndex
            int e = t.stopIndex
            if (s < 0 || e < s || e >= source.length()) {
                return
            }
            // 词法层面的坏字符（未闭合字符串等）也当作语法错误
            if (t.type == GroovyLexer.UNEXPECTED_CHAR) {
                throw new GroovySyntaxException([
                                "line ${t.line}:${t.charPositionInLine} 非法字符或未闭合字面量: ${t.text}".toString()])
            }
            if (s < cursor) {
                // 兜底：token 区间必须单调递增。跑到这里说明出现了没预料到的位置调整，
                // 宁可拒绝这个文件（fail-closed），也不产出一个不可信的 CST。
                throw new GroovySyntaxException([
                                "CST 构造失败：token 区间重叠（位置 $s < $cursor），已拒绝该文件".toString()])
            }
            gaps << new Gap(cursor, s, source.substring(cursor, s))
            String text = source.substring(s, e + 1)
            tokens << new CstToken(t.type, text, s, e, isComment(t.type, text))
            cursor = e + 1
        }
        gaps << new Gap(cursor, source.length(), source.substring(cursor))

        def cst = new Cst(source, tokens, gaps, treeShape)
        if (!cst.lossless) {
            throw new GroovySyntaxException(['CST 无损断言失败（内部 bug）：token 区间无法拼回原文'])
        }

        // 第三层：交给真正的 Groovy 编译器（只跑到 CONVERSION，不做语义分析）
        assertGroovyAccepts(source)
        cst
    }

    /**
     * 用真正的 Groovy 前端做最终裁决 —— 这是"语法正确"的权威定义。
     *
     * <p>只编译到 {@link Phases#CONVERSION}（解析 + 建 AST），不做语义分析、不生成字节码，
     * 所以不需要依赖任何外部类。
     */
    @SuppressWarnings('CatchThrowable')   // 同上：编译器抛什么都得接住
    static void assertGroovyAccepts(String source) throws GroovySyntaxException {
        if (source.trim().empty) {
            return
        }
        try {
            def cu = new CompilationUnit(new CompilerConfiguration())
            cu.addSource(SOURCE_NAME, source)
            cu.compile(Phases.CONVERSION)
        } catch (Throwable t) {
            String msg = t.message ?: t.toString()
            int nl = msg.indexOf('\n')
            throw new GroovySyntaxException(["Groovy 编译器拒绝: ${nl > 0 ? msg.substring(0, nl) : msg}".toString()])
        }
    }

    /**
     * 只做词法 + 语法分析并返回语法树形状，<b>不做任何闸门校验</b>。
     * 用于"忽略空行"的语法树等价比对（空行会改变 token 序列里 NL 的个数，
     * 所以必须先把空行折叠掉再比）。
     */
    @SuppressWarnings('CatchThrowable')   // 这里只用于等价比对，解析失败返回空串即可
    static String treeShapeOnly(String source) {
        def lexer = new GroovyLangLexer(CharStreams.fromString(source))
        lexer.errorIgnored = true
        lexer.removeErrorListeners()
        def parser = new GroovyLangParser(new CommonTokenStream(lexer))
        parser.removeErrorListeners()
        try {
            parser.compilationUnit().toStringTree(parser)
        } catch (Throwable ignored) {
            ''
        }
    }

    /** 注释是 NL 类型的 token，只能靠文本前缀区分。 */
    static boolean isComment(int type, String text) {
        type == GroovyLexer.NL && (text.startsWith('//') || text.startsWith('/*'))
    }

    /** 换行 token：NL 类型且不是注释。 */
    static boolean isNewlineToken(CstToken token) {
        token.type == GroovyLexer.NL && !token.comment
    }

    /** 词法错误监听器。relocate 后的 ANTLRErrorListener 只剩 syntaxError 一个方法。 */
    @CompileStatic
    private static class LexerErrors implements ANTLRErrorListener<Integer> {

        private final List<String> errors

        LexerErrors(List<String> errors) {
            this.errors = errors
        }

        @Override
        void syntaxError(Recognizer<Integer, ?> recognizer, Integer offending,
                int line, int charPositionInLine, String msg, RecognitionException e) {
            errors << "line $line:$charPositionInLine $msg".toString()
        }

    }

    /** 语法错误监听器。 */
    @CompileStatic
    private static class ParserErrors extends BaseErrorListener {

        private final List<String> errors

        ParserErrors(List<String> errors) {
            this.errors = errors
        }

        @Override
        <T extends Token> void syntaxError(Recognizer<T, ?> recognizer, T offending,
                int line, int charPositionInLine,
                String msg, RecognitionException e) {
            errors << "line $line:$charPositionInLine $msg".toString()
        }

    }

}
