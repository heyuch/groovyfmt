package io.github.heyuch.groovyfmt.pipeline

import groovy.transform.CompileStatic

import io.github.heyuch.groovyfmt.layout.Stats

/**
 * 格式化结果。
 *
 * @param output     最终文本（回退时等于输入）
 * @param changed    是否与输入不同
 * @param skipped    是否因为安全闸门而放弃格式化（原样返回）
 * @param skipReason 放弃原因
 * @param stats      覆盖率统计
 * @param ruleCount  参与本次格式化的水平规则数
 */
@CompileStatic
class FormatResult {

    final String output
    final boolean changed
    final boolean skipped
    final String skipReason
    final Stats stats
    final int ruleCount

    @SuppressWarnings('ParameterCount')   // 不可变结果对象，字段一一对应
    FormatResult(String output, boolean changed, boolean skipped,
            String skipReason, Stats stats, int ruleCount) {
        this.output = output
        this.changed = changed
        this.skipped = skipped
        this.skipReason = skipReason
        this.stats = stats
        this.ruleCount = ruleCount
    }

    boolean isOk() {
        !skipped
    }

}
