package io.github.heyuch.groovyfmt.gradle

import java.util.function.Function

import groovy.transform.CompileStatic

import com.diffplug.spotless.FormatterStep
import com.diffplug.spotless.Provisioner

import io.github.heyuch.groovyfmt.spotless.GroovyFmtStep

/**
 * groovyfmt 步骤的工厂，交给 Spotless 的 {@code FormatExtension.addStep}。
 *
 * <p>正常用插件（{@code id 'io.github.heyuch.groovyfmt'}）时会自动添加，不需要手动调用；
 * 这个入口留给需要自己控制步骤位置的场景。
 */
@CompileStatic
final class GroovyFmt {

    private GroovyFmt() {
    }

    static Function<Provisioner, FormatterStep> step() {
        { Provisioner provisioner ->
            GroovyFmtStep.create(provisioner, coordinate())
        } as Function<Provisioner, FormatterStep>
    }

    /** formatter 的 Maven 坐标；版本在构建期烘焙，与本插件版本一致。 */
    private static String coordinate() {
        Properties props = new Properties()
        InputStream stream = GroovyFmt.getResourceAsStream('groovyfmt.properties')
        if (stream == null) {
            throw new IllegalStateException('groovyfmt.properties 缺失，构建产物不完整')
        }
        try {
            props.load(stream)
        } finally {
            stream.close()
        }
        props.getProperty('coordinate')
    }

}
