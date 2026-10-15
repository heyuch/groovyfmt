package io.github.heyuch.groovyfmt.spotless

import groovy.transform.CompileStatic

/**
 * Spotless 适配器的测试。
 *
 * <p>刻意只验证"<b>桥是通的</b>"，不做行为测试：
 * formatter 本身的行为由 groovyfmt-core 的 45 个 fixture 覆盖，
 * 适配器再测一遍没有意义，只会让两处都得跟着改。
 *
 * <p>这里真正要守住四件事：
 * <ol>
 *   <li>能在<b>隔离类加载器</b>里加载 core —— 这是适配器存在的全部理由；</li>
 *   <li>被安全闸门拒绝的文件必须<b>抛错</b>而不是静默返回原文 ——
 *       静默会让 spotlessCheck 报"干净"，这是 golden 测试静默失效的同一个陷阱；</li>
 *   <li>幂等；</li>
 *   <li><b>序列化往返后仍可用</b> —— Spotless 会序列化步骤状态，
 *       反序列化后 transient 字段为空，必须由懒加载自愈。</li>
 * </ol>
 */
@CompileStatic
class SpotlessStepTest {

    private static int passed = 0
    private static final List<String> FAILURES = []

    private SpotlessStepTest() {
    }

    static void main(String[] args) {
        println ''
        println '── Spotless 适配器（隔离类加载器路径） ' + '─'.repeat(20)
        def step = new GroovyFmtStep(classpath())
        try {
            String once = step.apply('def x=1\n')
            check('隔离加载 core 并格式化', once == 'def x = 1\n', "实际 [${once.replace('\n', '\\n')}]")
            check('幂等', step.apply(once) == once, '')
            checkBlankLineCapability(step)
            checkRejectionIsLoud(step)
            checkSurvivesSerialization(step)
        } finally {
            step.close()
        }
        report()
    }

    /** 空行能力也要穿透适配器（它走的是同一套 FormatStyle 默认值）。 */
    private static void checkBlankLineCapability(GroovyFmtStep step) {
        String formatted = step.apply('class A {\ndef x = 1\n}\n')
        check('空行规范化生效（类体前后各一空行）',
                formatted == 'class A {\n\n    def x = 1\n\n}\n',
                "实际 [${formatted.replace('\n', '\\n')}]")
    }

    /**
     * 门禁语义的关键一条：拒绝就抛错。
     * 如果这里改成"返回原文"，spotlessCheck 会对一个根本没被格式化的文件报"干净"。
     */
    private static void checkRejectionIsLoud(GroovyFmtStep step) {
        try {
            step.apply('def x = (\n')
            fail('被拒绝的文件必须抛错，不能静默放过', '居然没抛异常')
        } catch (IllegalStateException e) {
            check('被拒绝的文件抛错且信息可读', e.message.contains('拒绝格式化'), e.message.take(70))
        }
    }

    /**
     * Spotless 会把步骤的【状态】序列化（{@code FormatterStep.create} + {@code SerializedFunction}），
     * 所以"transient 字段反序列化后为空"是真实存在的路径，不是理论问题。
     *
     * <p>这里真的走一遍序列化往返。往返后的实例必须能直接格式化 —— 因为 {@code apply()} 会先调
     * {@code ensureLoaded()} 重建隔离类加载器与全部反射句柄。这条测试守的就是那个"懒加载自愈"：
     * 它一旦被破坏，Spotless 的增量与缓存路径会在真实项目里以 NPE 的形式炸掉。
     *
     * <p>（SpotBugs 曾对这条报 SE_TRANSIENT_FIELD_NOT_RESTORED；现在它由这条测试兜住，
     * 而不再只是一句"设计如此"的注释。）
     */
    private static void checkSurvivesSerialization(GroovyFmtStep step) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream()
        new ObjectOutputStream(buffer).withCloseable { ObjectOutputStream out -> out.writeObject(step) }
        GroovyFmtStep round = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))
        .withCloseable { ObjectInputStream input -> input.readObject() as GroovyFmtStep }
        try {
            String formatted = round.apply('def x=1\n')
            check('序列化往返后仍能格式化（transient 状态由 ensureLoaded 重建）',
                    formatted == 'def x = 1\n', "实际 [${formatted.replace('\n', '\\n')}]")
        } finally {
            round.close()
        }
    }

    /**
     * 隔离类加载器的 classpath 由【宿主】提供 —— 这正是消费者使用适配器的方式：
     * 宿主知道 core 在哪，适配器不知道也不该知道。
     *
     * <p>测试里它来自 Gradle 传进来的系统属性；如果拿不到就退回本 JVM 的 classpath
     * （那种情况下 core 必须在测试 classpath 上）。
     */
    private static List<String> classpath() {
        String provided = System.getProperty('groovyfmt.coreClasspath')
        (provided ?: System.getProperty('java.class.path')).split(File.pathSeparator) as List<String>
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++
            println "  ✓ $name"
        } else {
            fail(name, detail)
        }
    }

    private static void fail(String name, String detail) {
        FAILURES << (name + (detail.empty ? '' : '  —— ' + detail))
        println '  ✗ ' + name + (detail.empty ? '' : '  —— ' + detail)
    }

    private static void report() {
        println '  ─────'
        println "  通过 $passed，失败 ${FAILURES.size()}"
        if (FAILURES) {
            FAILURES.each { String f -> println "    - $f" }
        }
        println ''
        System.exit(FAILURES ? 1 : 0)
    }

}
