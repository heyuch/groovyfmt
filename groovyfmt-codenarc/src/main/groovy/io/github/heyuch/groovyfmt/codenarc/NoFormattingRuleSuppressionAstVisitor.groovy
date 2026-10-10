package io.github.heyuch.groovyfmt.codenarc

import groovy.transform.CompileStatic

import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codenarc.rule.AbstractAstVisitor

/**
 * {@link NoFormattingRuleSuppressionRule} 的访问器。
 *
 * <p>覆盖范围刻意对齐 CodeNarc 自己采信抑制的位置（见 CodeNarc 的
 * {@code org.codenarc.analyzer.SuppressionAnalyzer}，它引用了 AnnotatedNode、
 * ClassNode、MethodNode、ConstructorNode、ImportNode、ModuleNode、PackageNode 与表达式）：
 * <ul>
 *   <li>{@link #visitAnnotations} —— 所有注解的必经之路，覆盖 class / method / constructor /
 *       field / property / 局部变量声明；</li>
 *   <li>{@link #checkModule} —— 文件级与包级；</li>
 *   <li>{@link #checkImports} —— import 上的注解。</li>
 * </ul>
 */
@CompileStatic
class NoFormattingRuleSuppressionAstVisitor extends AbstractAstVisitor {

    /**
     * 显式遍历【当前类】的注解与成员。
     *
     * <p>不用 {@code visitAnnotations(AnnotatedNode)} —— 实测它在 CodeNarc 的遍历里不会被调用。
     * {@code visitClassEx} 是 CodeNarc 保证会调用的钩子，而嵌套类会被单独访问，
     * 所以这里只走本类的成员，不需要递归 innerClasses。
     * 用 {@code isFirstVisit} 去重：{@code node.methods} 也可能包含构造器。
     */
    @Override
    protected void visitClassEx(ClassNode node) {
        check(node)
        for (FieldNode field: node.fields) {
            check(field)
        }
        for (MethodNode method: node.methods) {
            check(method)
        }
        for (ConstructorNode constructor: node.declaredConstructors) {
            check(constructor)
        }
        super.visitClassEx(node)
    }

    private void check(AnnotatedNode node) {
        if (isFirstVisit(node)) {
            checkAnnotations(node, node.annotations)
        }
    }

    /**
     * import 与包级注解。这里直接用传进来的 {@code ModuleNode}，
     * 不走 {@code getSourceUnit()}（在 CodeNarc 的访问器里调用它抛 "should never be called"）。
     *
     * <p>文件级 {@code @SuppressWarnings} 不存在：{@code ModuleNode} 并不实现 {@code AnnotatedNode}。
     */
    @Override
    void visitImports(ModuleNode module) {
        if (module != null) {
            PackageNode pkg = module.package
            if (pkg != null) {
                checkAnnotations(pkg, pkg.annotations)
            }
            List<ImportNode> imported = []
            imported.addAll(module.imports)
            imported.addAll(module.starImports)
            imported.addAll(module.staticImports.values())
            imported.addAll(module.staticStarImports.values())
            for (ImportNode node: imported) {
                checkAnnotations(node, node.annotations)
            }
        }
        super.visitImports(module)
    }

    // NestedForLoop：注解 × 规则名的两层遍历就是语义本身，拆不开。
    // 这两条都是【非格式化】规则，按项目约定可以豁免（约定只禁止豁免格式化规则）。
    @SuppressWarnings('NestedForLoop')
    private void checkAnnotations(AnnotatedNode node, List<AnnotationNode> annotations) {
        if (annotations == null || annotations.empty) {
            return
        }
        Set<String> formatting = ((NoFormattingRuleSuppressionRule) rule).formattingRuleNames()
        for (AnnotationNode annotation: annotations) {
            // 按简单名判断：@SuppressWarnings 实际是 java.lang.SuppressWarnings，
            // 用全限定名判断曾导致规则完全不生效（写了 groovy.lang.SuppressWarnings）。
            if (annotation.classNode.nameWithoutPackage != 'SuppressWarnings') {
                continue
            }
            for (String suppressed: suppressedNames(annotation)) {
                if (formatting.contains(suppressed)) {
                    addViolation(node, "Formatting rule '${suppressed}' must not be suppressed: "
                            + 'the CodeNarc formatting rules are the gate, groovyfmt is the spec. '
                            + 'Implement the formatting feature instead of silencing the gate.')
                }
            }
        }
    }

    /** 取出 {@code @SuppressWarnings('A')} / {@code @SuppressWarnings(['A','B'])} 里的规则名。 */
    // Instanceof：这里是按 AST 节点类型分派，instanceof 正是合适的工具。
    @SuppressWarnings('Instanceof')
    private static List<String> suppressedNames(AnnotationNode annotation) {
        Expression value = annotation.members['value']
        if (value instanceof ConstantExpression) {
            return [String.valueOf(((ConstantExpression) value).value)]
        }
        if (value instanceof ListExpression) {
            List<String> names = []
            for (Expression item: ((ListExpression) value).expressions) {
                if (item instanceof ConstantExpression) {
                    names.add(String.valueOf(((ConstantExpression) item).value))
                }
            }
            return names
        }
        []
    }

}
