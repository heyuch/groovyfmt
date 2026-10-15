// 用 Groovy 6 的 NullChecker 做一次【编译期】空值检查。
//
// 由 `./gradlew nullCheck` 启动：那个任务的 classpath 完全来自一个独立 configuration，
// 所以 Groovy 6 不会进入本项目的编译或运行 classpath（进程边界 + 独立 classpath）。
//
// 关键点：
//   · @TypeChecked 用 ASTTransformationCustomizer 全局注入 —— 不改项目源码
//   · 只编译到 SEMANTIC_ANALYSIS（类型检查发生在这一相）—— 不生成字节码
//   · 编译前用 Class.forName 验证扩展真的存在 —— 名字写错要立刻响，而不是静默通过
//   · Groovy 版本打印出来 —— 确认跑的是哪一份（避免"以为在检查其实没检查"）

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

String extension = System.getProperty('nullCheck.extension') ?: 'groovy.typecheckers.NullChecker'
String reportPath = System.getProperty('nullCheck.report')

println "  运行中的 Groovy 版本: ${GroovySystem.version}"

// 扩展存在性：名字写错时立刻失败（fail-closed）
try {
    Class.forName(extension, false, this.class.classLoader)
    println "  检查扩展: ${extension} ✓"
} catch (Throwable ignored) {
    println "  ✗ 找不到检查扩展: ${extension}"
    println "    可用的 typechecker 扩展（从 groovy jar 里找）:"
            .protectionDomain?.codeSource?.location
    println "      groovy jar = ${jar}"
    System.exit(2)
}

List<File> files = []
args.each { String dir ->
    File d = new File(dir)
    if (d.directory) {
        d.eachFileRecurse { File f ->
            if (f.file && f.name.endsWith('.groovy')) {
                files << f
            }
        }
    } else if (d.file && d.name.endsWith('.groovy')) {
        files << d
    }
}
files.sort { it.path }
println "  待检查文件数: ${files.size()}（来自 ${args.join(', ')}）"
if (files.empty) {
    System.exit(0)
}

CompilerConfiguration cfg = new CompilerConfiguration()
// extensions 的成员类型是 Class[] —— 源码里写字符串会被 Groovy 强制转换，
// 但这里是运行期传值，必须直接给 Class 对象，否则变换拿到 String 会静默不用它。
boolean inject = (System.getProperty('nullCheck.inject') ?: 'true') != 'false'
if (inject) {
    // 必须传【字符串】：实测四种写法里只有 String 会被变换真正采纳，
    // Class / ClassExpression 都被静默忽略（于是"任何输入都报无发现"）。
    cfg.addCompilationCustomizers(new ASTTransformationCustomizer(
            [extensions: [extension]], Class.forName('groovy.transform.TypeChecked')))
    println "  注入方式: ASTTransformationCustomizer(extensions=['${extension}'])"
} else {
    println "  注入方式: 不注入（依赖源码里的 @TypeChecked(extensions=...)）"
}

def cu = new org.codehaus.groovy.control.CompilationUnit(cfg)
files.each { cu.addSource(it) }

String report
int findings
try {
    // 必须到 INSTRUCTION_SELECTION：实测 @TypeChecked 的检查要到这一相才完成。
    // 走过两个错相，都是"任何输入都报无发现"（连非法正则都不报）——比不检查更危险：
    //   SEMANTIC_ANALYSIS  ✗ 变换还没跑         CANONICALIZATION ✗ 检查还没出结果
    // INSTRUCTION_SELECTION ✓ 检查完成且尚未写 class 文件（写文件在 OUTPUT 相）。
    cu.compile(Phases.INSTRUCTION_SELECTION)
    def collected = cu.errorCollector.errors ?: []
    findings = collected.size()
    if (findings > 0) {
        // 兜底：万一某个版本不抛异常而只收集错误，也绝不能报"无发现"
        report = collected.collect { it.toString() }.join('\n')
        println "  ✗ 编译未抛异常，但 errorCollector 收集到 ${findings} 条 —— 按发现处理"
    } else {
        report = "无发现"
        println "  ✓ ${extension} 没有报出问题（相=${Phases.INSTRUCTION_SELECTION}）"
    }
} catch (MultipleCompilationErrorsException e) {
    report = e.message
    findings = report.readLines().findAll { it.contains('.groovy:') }.size()
    println "  ✗ ${extension} 报出 ${findings} 处（下面按文件列出）"
    report.readLines().findAll { it.trim() }.take(60).each { println '      ' + it }
    if (report.readLines().count { it.trim() } > 60) {
        println "      ...（完整内容见报告文件）"
    }
}

if (reportPath) {
    File out = new File(reportPath)
    out.parentFile?.mkdirs()
    out.text = "extension=${extension}\ngroovy=${GroovySystem.version}\nfiles=${files.size()}\nfindings=${findings}\n\n${report}\n"
    println "  报告: ${reportPath}"
}
System.exit(findings > 0 ? 1 : 0)
