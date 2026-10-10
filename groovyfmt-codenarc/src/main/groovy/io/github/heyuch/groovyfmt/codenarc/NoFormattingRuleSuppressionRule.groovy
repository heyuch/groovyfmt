package io.github.heyuch.groovyfmt.codenarc

import groovy.transform.CompileStatic

import org.codenarc.rule.AbstractAstVisitorRule

/**
 * 约定：<b>涉及代码格式的 CodeNarc 规则不得被 {@code @SuppressWarnings} 豁免</b>。
 *
 * <p>理由：CodeNarc 的格式化规则是【门禁】，groovyfmt 是【规范】（自动修复）。
 * 门禁报警说明规范不完整 —— 正确的反应是实现那个格式化特性，而不是把门禁关掉。
 * 一旦允许豁免，门禁就退化成"只要没人抱怨就通过"，格式会静默漂移。
 *
 * <p>格式化规则清单默认直接读 CodeNarc 自带的 {@code rulesets/formatting.xml}，
 * 所以升级 CodeNarc 后清单自动跟上；也可以用 {@code formattingRules} 属性显式覆盖。
 *
 * <p>只检查 class / field / method / constructor 上的注解 —— 这也正是 CodeNarc
 * 自己会采信抑制的位置，其他地方的 {@code @SuppressWarnings} 本来就无效。
 */
@CompileStatic
class NoFormattingRuleSuppressionRule extends AbstractAstVisitorRule {

    String name = 'NoFormattingRuleSuppression'

    int priority = 2

    /** 显式覆盖格式化规则清单（逗号分隔）；留空则读 CodeNarc 自带的格式化规则集。 */
    String formattingRules

    Class astVisitorClass = NoFormattingRuleSuppressionAstVisitor

    /** 生效的格式化规则名集合。 */
    Set<String> formattingRuleNames() {
        if (formattingRules) {
            return formattingRules.tokenize(',')*.trim().findAll { String n -> n } as Set<String>
        }
        String xml = readBundledFormattingRules()
        Set<String> names = [] as Set<String>
        def matcher = xml =~ /class='([^']+)'/
        while (matcher.find()) {
            names.add(matcher.group(1).tokenize('.').last().replaceFirst(/Rule$/, ''))
        }
        names
    }

    private String readBundledFormattingRules() {
        InputStream stream = getClass().classLoader.getResourceAsStream('rulesets/formatting.xml')
        if (stream == null) {
            stream = Thread.currentThread().contextClassLoader
            .getResourceAsStream('rulesets/formatting.xml')
        }
        if (stream == null) {
            // fail closed：读不到清单就不能假装"检查通过了"
            throw new IllegalStateException(
                    'NoFormattingRuleSuppression: 读不到 CodeNarc 的 rulesets/formatting.xml，' +
                    '无法判定哪些规则属于格式化规则')
        }
        try {
            stream.getText('UTF-8')
        } finally {
            stream.close()
        }
    }

}
