package io.github.heyuch.groovyfmt.layout

import groovy.transform.CompileStatic

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
    private static final int MAX_PASSES = 20

    private LineWrapper() {
    }

    /** 折行到每行不超过 {@code style.maxLineLength}；未启用（<=0）时原样返回。 */
    static String wrap(String source, FormatStyle style) {
        if (style.maxLineLength <= 0 || source == null || source.empty) {
            return source
        }
        String eol = source.contains('\r\n') ? '\r\n' : '\n'
        List<String> lines = new ArrayList<>(Arrays.asList(source.split('\n', -1)))
        boolean changed = false
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean passChanged = false
            def next = new ArrayList<String>(lines.size())
            lines.each { String line ->
                String text = line.endsWith('\r') ? line.substring(0, line.length() - 1) : line
                if (text.length() <= style.maxLineLength) {
                    next << text
                    return
                }
                int cut = lastBreakAtOrBefore(text, style.maxLineLength)
                if (cut <= 0) {
                    next << text                        // 没有可断点：原样保留（fail-closed）
                    return
                }
                next << text.substring(0, cut)
                next << text.substring(cut).replaceAll(/^\s+/, '')
                passChanged = true
            }
            lines = next
            changed = changed || passChanged
            if (!passChanged) {
                break
            }
        }
        changed ? lines.join(eol) : source
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
    private static int lastBreakAtOrBefore(String line, int limit) {
        int best = -1
        int i = 0
        int n = line.length()
        QuoteState state = QuoteState.NONE
        while (i < n) {
            char c = line.charAt(i)
            if (state == QuoteState.NONE) {
                if (c == ('/' as char) && i + 1 < n && line.charAt(i + 1) == ('/' as char)) {
                    break                                  // 行注释：后面不可能有断点
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
                int candidate = -1
                if (c == (',' as char)) {
                    candidate = i + 1
                } else if (c == ('.' as char) || c == ('?' as char) || c == (':' as char)) {
                    candidate = i
                }
                if (candidate >= 0 && candidate > 0 && candidate <= limit && candidate > best) {
                    best = candidate
                }
            } else if (c == ('\\' as char)) {
                i += 2
                continue
            } else if (closes(state, c)) {
                state = QuoteState.NONE
            }
            i++
        }
        best
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
