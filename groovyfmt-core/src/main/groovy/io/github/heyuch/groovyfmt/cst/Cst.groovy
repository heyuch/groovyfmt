package io.github.heyuch.groovyfmt.cst

import groovy.transform.CompileStatic

/**
 * 无损 CST：{@code [gap0] tok0 [gap1] tok1 ... tok(n-1) [gapn]}。
 *
 * <p>不变式（格式化器必须始终满足）：
 * <ol>
 *   <li>{@code gaps.size() == tokens.size() + 1}</li>
 *   <li>{@link #originalText()} 逐字节等于原始源码（无损）</li>
 * </ol>
 *
 * <p>格式化的全部工作就是<b>为每个 gap 重新决定它的空白内容</b>，
 * token 本身（含注释与 NL token）绝不被改写。
 */
@CompileStatic
class Cst {

    final String source
    final List<CstToken> tokens
    final List<Gap> gaps
    /** 语法树形状（ANTLR toStringTree），用于格式化前后做语义等价比对。 */
    final String treeShape

    Cst(String source, List<CstToken> tokens, List<Gap> gaps, String treeShape) {
        this.source = source
        this.tokens = List.copyOf(tokens)
        this.gaps = List.copyOf(gaps)
        this.treeShape = treeShape
    }

    int getTokenCount() {
        tokens.size()
    }

    /** 用原始 trivia 还原原文。 */
    String originalText() {
        render(null)
    }

    /**
     * 用新的 trivia 渲染。
     *
     * @param newGaps 与 {@link #gaps} 等长的新 trivia；传 {@code null} 表示用原文
     */
    String render(List<String> newGaps) {
        if (newGaps != null && newGaps.size() != gaps.size()) {
            throw new IllegalArgumentException(
                    "trivia 数量不匹配: 期望 ${gaps.size()}，实际 ${newGaps.size()}".toString())
        }
        def sb = new StringBuilder(source.length() + 64)
        tokens.eachWithIndex { CstToken token, int i ->
            sb.append(newGaps == null ? gaps[i].original : newGaps[i])
            sb.append(token.text)
        }
        sb.append(newGaps == null ? gaps[tokens.size()].original : newGaps[tokens.size()])
        sb.toString()
    }

    /**
     * 用<b>另一套</b> token 序列渲染（空行规范化会增删 NL token，所以需要这个重载）。
     *
     * @param tokenList 规范化后的 token 序列
     * @param newGaps   与之成对的 gap 序列（长度必须是 tokenList.size() + 1）
     */
    String render(List<CstToken> tokenList, List<String> newGaps) {
        if (newGaps.size() != tokenList.size() + 1) {
            throw new IllegalArgumentException(
                    "trivia 数量不匹配: 期望 ${tokenList.size() + 1}，实际 ${newGaps.size()}".toString())
        }
        def sb = new StringBuilder(source.length() + 64)
        tokenList.eachWithIndex { CstToken token, int i ->
            sb.append(newGaps[i])
            sb.append(token.text)
        }
        sb.append(newGaps[tokenList.size()])
        sb.toString()
    }

    /** 无损断言：还原结果必须逐字节等于原文。 */
    boolean isLossless() {
        originalText() == source
    }

    /**
     * 规范化指纹：把连续的换行 token 折叠成一个之后再做指纹。
     *
     * <p>这是空行规范化之后输出闸门所用的比对方式。之所以仍然安全：
     * 它保证所有非换行 token 逐一相同、每处换行的换行风格相同、换行处的<b>个数</b>相同，
     * 唯一允许的差异就是空行数量 —— 而 Groovy 的 {@code nls : NL+} 说明
     * 一个还是三个 NL 解析结果完全相同。
     */
    String canonicalTokenFingerprint() {
        def sb = new StringBuilder(tokens.size() * 12)
        boolean prevNewline = false
        tokens.each { CstToken t ->
            if (CstBuilder.isNewlineToken(t)) {
                if (!prevNewline) {
                    sb.append(t.type).append('\u0001' as char).append(t.text).append('\u0002' as char)
                }
                prevNewline = true
            } else {
                prevNewline = false
                sb.append(t.type).append('\u0001' as char).append(t.text).append('\u0002' as char)
            }
        }
        sb.toString()
    }

    /** 把连续空行折叠成一个之后的源码 —— 用于对两份文本做"忽略空行"的语法树比对。 */
    String canonicalText() {
        def sb = new StringBuilder(source.length())
        boolean prevNewline = false
        tokens.eachWithIndex { CstToken t, int i ->
            if (CstBuilder.isNewlineToken(t)) {
                if (prevNewline) {
                    return
                }
                prevNewline = true
            } else {
                prevNewline = false
            }
            sb.append(gaps[i].original).append(t.text)
        }
        sb.append(gaps[tokens.size()].original)
        sb.toString()
    }

    /** 生成 token 指纹（类型 + 原文），用于格式化前后比对。 */
    String tokenFingerprint() {
        def sb = new StringBuilder(tokens.size() * 12)
        tokens.each { CstToken t ->
            sb.append(t.type).append('\u0001' as char).append(t.text).append('\u0002' as char)
        }
        sb.toString()
    }

    /** 找出与另一份 CST 的第一个 token 差异，用于报错定位。 */
    String firstTokenDifference(Cst other) {
        int n = Math.min(tokens.size(), other.tokens.size())
        for (int i = 0; i < n; i++) {
            def a = tokens[i]
            def b = other.tokens[i]
            if (a.type != b.type || a.text != b.text) {
                return "第 $i 个 token: 原始=$a 格式化后=$b".toString()
            }
        }
        if (tokens.size() != other.tokens.size()) {
            return "token 数量不同: 原始=${tokens.size()} 格式化后=${other.tokens.size()}".toString()
        }
        null
    }

}
