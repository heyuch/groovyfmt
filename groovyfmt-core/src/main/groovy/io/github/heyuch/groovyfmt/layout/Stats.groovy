package io.github.heyuch.groovyfmt.layout

import groovy.transform.CompileStatic

/** 覆盖率统计 —— 让"这套框架到底能处理多少代码"变成可测量的数字。 */
@CompileStatic
class Stats {

    int horizontalGaps
    int verticalGaps
    /** 成功分到 Slot 的水平 gap 数。 */
    int classified
    /** 分类不出来（保留原文）的水平 gap 数。 */
    int unclassified
    /** 实际发生变化的 gap 数。 */
    int changed

    /** 分类覆盖率：已分类 / 全部水平 gap。 */
    double getCoverage() {
        int total = classified + unclassified
        total == 0 ? 1.0d : (double) classified / total
    }

    String summary() {
        String.format('水平 gap %d（已分类 %d / 保留原文 %d，覆盖率 %.1f%%），垂直 gap %d，实际改动 %d 处',
                horizontalGaps, classified, unclassified, coverage * 100.0d, verticalGaps, changed)
    }

}
