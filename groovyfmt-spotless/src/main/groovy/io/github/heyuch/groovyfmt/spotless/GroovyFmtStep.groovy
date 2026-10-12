package io.github.heyuch.groovyfmt.spotless

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import com.diffplug.spotless.Provisioner
import com.diffplug.spotless.SerializedFunction

/**
 * 把 groovyfmt 接成 Spotless 步骤（给<b>其他项目</b>用的可复用入口）。
 *
 * <h3>为什么必须用隔离类加载器</h3>
 * Groovy 3 与 Groovy 4 都把 ANTLR4 shade 成同一个包名 {@code groovyjarjarantlr4.v4.runtime}，
 * 但类的层次不同。Gradle 守护进程内置 Groovy 3，而 groovyfmt 需要 Groovy 4；
 * buildscript 类加载器是<b>父优先</b>的，于是 {@code Parser} 会解析到 Groovy 3 的同名类：
 *
 * <pre>
 * java.lang.VerifyError: Bad type on operand stack
 *   Type 'org/apache/groovy/parser/antlr4/GroovyLangParser' is not assignable to
 *        'groovyjarjarantlr4/v4/runtime/Parser'
 * </pre>
 *
 * 所以这里用一个 {@code parent = platform} 的类加载器把 groovyfmt 完全隔离起来，
 * 只通过反射调用它。本类自身<b>不引用任何 Groovy 类</b>，因此在守护进程里可以安全加载。
 *
 * <h3>消费者用法</h3>
 * <pre>
 * buildscript {
 *     dependencies { classpath 'io.github.heyuch:groovyfmt-spotless:0.1.0' }
 * }
 * configurations { groovyfmtRuntime }
 * dependencies { groovyfmtRuntime 'io.github.heyuch:groovyfmt-core:0.1.0' }
 *
 * spotless {
 *     groovy {
 *         importOrder 'java', 'javax', 'groovy', 'com', ''   // 先重排行
 *         custom 'groovyfmt', new GroovyFmtStep(
 *                 configurations.groovyfmtRuntime.files.collect { it.absolutePath })
 *     }
 * }
 * </pre>
 *
 * 注意两点：
 * <ul>
 *   <li><b>步骤顺序</b>：{@code importOrder} 要放前面 —— 它只重排整行，
 *       后面再由 groovyfmt 统一间距，组合才是稳定不动点。</li>
 *   <li>Spotless 无法为自定义步骤做缓存（{@code FormatterFunc} 不可序列化），
 *       改了步骤行为后用 {@code bumpThisNumberIfACustomStepChanges(n)} 手动失效。</li>
 * </ul>
 */
@CompileStatic
class GroovyFmtStep implements FormatterFunc, Closeable, Serializable {

    private static final long serialVersionUID = 1L

    /** Spotless 步骤名，用于诊断与缓存比较。 */
    static final String NAME = 'groovyfmt'

    /** 隔离类加载器要用的 classpath：groovyfmt 自己的 jar + Groovy 4 的 jar。 */
    private final List<String> classpath

    // 以下都是隔离世界里的东西，不参与序列化
    private transient URLClassLoader loader
    private transient Object formatter
    private transient Method formatMethod
    private transient Method skippedMethod
    private transient Method reasonMethod
    private transient Method outputMethod

    GroovyFmtStep(List<String> classpath) {
        this.classpath = List.copyOf(classpath)
    }

    @Override
    String apply(String source) throws Exception {
        ensureLoaded()
        def result = formatMethod.invoke(formatter, source)
        if (skippedMethod.invoke(result) as boolean) {
            // 门禁语义：拒绝格式化就抛错，绝不静默放过
            throw new IllegalStateException(
                    "groovyfmt 拒绝格式化: ${reasonMethod.invoke(result)}".toString())
        }
        outputMethod.invoke(result) as String
    }

    private void ensureLoaded() {
        if (formatter != null) {
            return
        }
        def urls = classpath.collect { String p -> new File(p).toURI().toURL() } as URL[]
        loader = new URLClassLoader(urls, ClassLoader.platformClassLoader)
        def clazz = loader.loadClass('io.github.heyuch.groovyfmt.pipeline.Formatter')
        formatter = clazz.getMethod('defaults').invoke(null)
        formatMethod = formatter.getClass().getMethod('format', String)
        def resultClass = loader.loadClass('io.github.heyuch.groovyfmt.pipeline.FormatResult')
        skippedMethod = resultClass.getMethod('isSkipped')
        reasonMethod = resultClass.getMethod('getSkipReason')
        outputMethod = resultClass.getMethod('getOutput')
    }

    /** 释放隔离类加载器。 */
    @Override
    void close() throws IOException {
        if (loader != null) {
            loader.close()
        }
        loader = null
        formatter = null
    }

    /**
     * 官方配方的工厂：用 Spotless 注入的 {@link Provisioner} 解析坐标，构造一个正式的
     * {@link FormatterStep}。第三方 Gradle / Maven 插件应当用这个，而不是
     * {@code custom 'groovyfmt', new GroovyFmtStep(paths)}。
     *
     * <p>三处关键差别：
     * <ul>
     *   <li><b>坐标由 Spotless 自己解析</b>（走它配置的仓库），消费者不需要声明 groovyfmt 的依赖，
     *       也不需要自己拼 classpath；</li>
     *   <li><b>解析是惰性的</b>（由 {@code addStep(Function<Provisioner, …>)} 触发），
     *       所以配置期不要求 artifact 已存在 —— 这是 {@code custom} 做不到的；</li>
     *   <li><b>相等性建立在序列化状态上</b>（本类的 {@code classpath} 字段），
     *       所以 Spotless 能正确做增量与缓存判断，{@code bumpThisNumberIfACustomStepChanges} 不再需要。</li>
     * </ul>
     */
    // FactoryMethodName 规则要求 create* 方法返回自身类型，但 Spotless 的约定恰恰相反：
    // 步骤的工厂方法必须叫 create 并返回 FormatterStep。这里按 Spotless 的约定办。
    @SuppressWarnings('FactoryMethodName')
    static FormatterStep create(Provisioner provisioner, String coordinate) {
        // 必须带传递依赖：core 的 POM 把 Groovy 4 声明为 runtime 依赖，
        // 隔离加载器要靠它才能加载 core 里那些 Groovy 类（否则 NoClassDefFoundError: groovy/lang/GroovyObject）
        Set<File> files = provisioner.provisionWithTransitives(true, [coordinate] as Collection<String>)
        List<String> paths = files*.absolutePath as List<String>
        FormatterStep.create(NAME, new GroovyFmtStep(paths), new Identity())
    }

    /**
     * {@link SerializedFunction} 的恒等实现：本类既是步骤的状态，也是它的 {@link FormatterFunc}。
     *
     * <p>写成显式类而不是闭包：闭包会把外围对象一起捕获进去，Spotless 的序列化 fingerprint
     * 会因此失败（我们在构建脚本里已经踩过一次）。
     */
    @CompileStatic
    private static final class Identity implements SerializedFunction<GroovyFmtStep, FormatterFunc> {

        private static final long serialVersionUID = 1L

        @Override
        FormatterFunc apply(GroovyFmtStep state) {
            state
        }

    }

}
