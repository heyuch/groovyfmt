package io.github.heyuch.groovyfmt.gradle

import groovy.transform.CompileStatic

import com.diffplug.gradle.spotless.GroovyExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 把 groovyfmt 接进 Spotless 的 Gradle 插件。
 *
 * <p>应用本插件后 groovyfmt 会自动成为 Spotless groovy 格式的一个步骤，消费者不需要任何样板：
 * <pre>
 * plugins {
 *     id 'com.diffplug.spotless' version '8.10.2'
 *     id 'io.github.heyuch.groovyfmt' version '0.1.0'
 * }
 * spotless { groovy { target 'src/**&#47;*.groovy' } }
 * </pre>
 *
 * <p>用的是 Spotless 官方的第三方扩展点
 * {@code FormatExtension.addStep(Function&lt;Provisioner, FormatterStep&gt;)}：
 * {@link com.diffplug.spotless.Provisioner} 由 Spotless 注入（走它配置的仓库），
 * 而且解析是惰性的 —— 配置期不要求 artifact 已存在。
 */
@CompileStatic
class GroovyFmtPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.withType(SpotlessPlugin).configureEach {
            // 立即添加而不是 afterEvaluate：Spotless 在自身 afterEvaluate 里固化步骤列表，
            // 晚注册的第三方插件追不进去。
            // 位置在最前是安全的：groovyfmt 与 importOrder 作用于不相交的区域
            // （空白/缩进 vs 整行顺序与分组空行），因此可交换。
            addStep(project)
        }
    }

    private static void addStep(Project project) {
        SpotlessExtension spotless = project.extensions.getByType(SpotlessExtension)
        spotless.groovy { GroovyExtension groovy ->
            groovy.addStep(GroovyFmt.step())
        }
    }

}
