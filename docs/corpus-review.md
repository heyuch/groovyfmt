# 语料复核清单（自动生成，勿手改）

生成：`tools/corpus-review-sheet.sh`　CodeNarc 复核：`tools/check-corpus-codenarc.sh`　冻结登记：`tools/lock-corpus.sh`

共 **85** 个用例，其中 **53** 个带期望输出。

复核每一条时问两件事：**形状可接受吗**（尤其断行断在哪）、**过 CodeNarc 吗**。

| # | 分组 | 用例 | 有期望 |
| --- | --- | --- | --- |
| 1 | `blankline` | `blankline/annotated-field-after-previous-annotations.groovy` | ✓ |
| 2 | `blankline` | `blankline/annotated-field.groovy` | ✓ |
| 3 | `blankline` | `blankline/annotated-param.groovy` | —（要求保持不变） |
| 4 | `blankline` | `blankline/block-start-end.groovy` | ✓ |
| 5 | `blankline` | `blankline/package-imports.groovy` | ✓ |
| 6 | `fail-closed` | `fail-closed/array-type.groovy` | —（要求保持不变） |
| 7 | `fail-closed` | `fail-closed/assert-colon.groovy` | —（要求保持不变） |
| 8 | `fail-closed` | `fail-closed/blank-line-indent.groovy` | ✓ |
| 9 | `fail-closed` | `fail-closed/blank-lines.groovy` | ✓ |
| 10 | `fail-closed` | `fail-closed/case-colon.groovy` | ✓ |
| 11 | `fail-closed` | `fail-closed/command-expression.groovy` | —（要求保持不变） |
| 12 | `fail-closed` | `fail-closed/compact-comparison.groovy` | —（要求保持不变） |
| 13 | `fail-closed` | `fail-closed/crlf.groovy` | ✓ |
| 14 | `fail-closed` | `fail-closed/generic-angles.groovy` | —（要求保持不变） |
| 15 | `fail-closed` | `fail-closed/line-escape.groovy` | —（要求保持不变） |
| 16 | `fail-closed` | `fail-closed/shebang.groovy` | ✓ |
| 17 | `fail-closed` | `fail-closed/shift-operator.groovy` | —（要求保持不变） |
| 18 | `fail-closed` | `fail-closed/trailing-comment.groovy` | —（要求保持不变） |
| 19 | `gstring` | `gstring/bare-name-colon.groovy` | —（要求保持不变） |
| 20 | `gstring` | `gstring/bare-name-then-call.groovy` | ✓ |
| 21 | `gstring` | `gstring/bare-name.groovy` | —（要求保持不变） |
| 22 | `gstring` | `gstring/brace-form.groovy` | —（要求保持不变） |
| 23 | `gstring` | `gstring/brace-ternary.groovy` | —（要求保持不变） |
| 24 | `gstring` | `gstring/brace-with-op.groovy` | ✓ |
| 25 | `gstring` | `gstring/interpolation-then-statement.groovy` | —（要求保持不变） |
| 26 | `gstring` | `gstring/mixed.groovy` | —（要求保持不变） |
| 27 | `gstring` | `gstring/multi-part.groovy` | —（要求保持不变） |
| 28 | `gstring` | `gstring/triple-quoted.groovy` | —（要求保持不变） |
| 29 | `indent` | `indent/anonymous-class-in-call.groovy` | ✓ |
| 30 | `indent` | `indent/anonymous-class-on-continuation.groovy` | ✓ |
| 31 | `indent` | `indent/anonymous-class-on-string-concat.groovy` | ✓ |
| 32 | `indent` | `indent/anonymous-class-two-shapes.groovy` | ✓ |
| 33 | `indent` | `indent/as-cast-then-statement.groovy` | —（要求保持不变） |
| 34 | `indent` | `indent/call-args-nested-brackets.groovy` | ✓ |
| 35 | `indent` | `indent/case-body-indent-from-flat.groovy` | ✓ |
| 36 | `indent` | `indent/case-body-indent.groovy` | ✓ |
| 37 | `indent` | `indent/closure-arg-in-map.groovy` | ✓ |
| 38 | `indent` | `indent/closure-arrow-then-body.groovy` | —（要求保持不变） |
| 39 | `indent` | `indent/dot-chain-continuation.groovy` | ✓ |
| 40 | `indent` | `indent/method-multiline-params.groovy` | —（要求保持不变） |
| 41 | `indent` | `indent/nested-block.groovy` | ✓ |
| 42 | `indent` | `indent/nested-class-blank-lines.groovy` | ✓ |
| 43 | `indent` | `indent/paren-continuation.groovy` | ✓ |
| 44 | `indent` | `indent/statement-continuation.groovy` | ✓ |
| 45 | `indent` | `indent/top-level-continuation.groovy` | —（要求保持不变） |
| 46 | `indent` | `indent/unary-plus-is-new-statement.groovy` | ✓ |
| 47 | `indent` | `indent/wrapped-statement.groovy` | ✓ |
| 48 | `invalid` | `invalid/gstring-dollar-space.invalid.groovy` | —（要求保持不变） |
| 49 | `invalid` | `invalid/unbalanced-paren.invalid.groovy` | —（要求保持不变） |
| 50 | `invalid` | `invalid/unclosed-string.invalid.groovy` | —（要求保持不变） |
| 51 | `spacing` | `spacing/annotation.groovy` | ✓ |
| 52 | `spacing` | `spacing/binary-op.groovy` | ✓ |
| 53 | `spacing` | `spacing/call-paren.groovy` | ✓ |
| 54 | `spacing` | `spacing/closure-as-first-arg.groovy` | ✓ |
| 55 | `spacing` | `spacing/closure-brace.groovy` | ✓ |
| 56 | `spacing` | `spacing/comparison.groovy` | ✓ |
| 57 | `spacing` | `spacing/dot-chain.groovy` | ✓ |
| 58 | `spacing` | `spacing/empty-closure.groovy` | ✓ |
| 59 | `spacing` | `spacing/for-in-colon.groovy` | ✓ |
| 60 | `spacing` | `spacing/for-in-header-others.groovy` | —（要求保持不变） |
| 61 | `spacing` | `spacing/for-semicolons.groovy` | ✓ |
| 62 | `spacing` | `spacing/generic-wildcard-others.groovy` | —（要求保持不变） |
| 63 | `spacing` | `spacing/generic-wildcard.groovy` | ✓ |
| 64 | `spacing` | `spacing/keyword-paren.groovy` | ✓ |
| 65 | `spacing` | `spacing/map-colon.groovy` | ✓ |
| 66 | `spacing` | `spacing/named-arg-colon.groovy` | ✓ |
| 67 | `spacing` | `spacing/postfix-inc.groovy` | ✓ |
| 68 | `spacing` | `spacing/spread-args.groovy` | —（要求保持不变） |
| 69 | `spacing` | `spacing/string-concat.groovy` | ✓ |
| 70 | `spacing` | `spacing/ternary.groovy` | ✓ |
| 71 | `spacing` | `spacing/trailing-whitespace.groovy` | ✓ |
| 72 | `spacing` | `spacing/unary-minus.groovy` | —（要求保持不变） |
| 73 | `wrapping` | `wrapping/multi-pass-convergence.groovy` | ✓ |
| 74 | `wrapping` | `wrapping/no-wrap-inside-closure.groovy` | —（要求保持不变） |
| 75 | `wrapping` | `wrapping/no-wrap-inside-for-header.groovy` | —（要求保持不变） |
| 76 | `wrapping` | `wrapping/no-wrap-inside-generics.groovy` | ✓ |
| 77 | `wrapping` | `wrapping/only-in-closure-break-points.groovy` | —（要求保持不变） |
| 78 | `wrapping` | `wrapping/try-with-resources-then-catch.groovy` | ✓ |
| 79 | `wrapping` | `wrapping/unbreakable-long-line.groovy` | —（要求保持不变） |
| 80 | `wrapping` | `wrapping/wrap-annotation-attributes.groovy` | ✓ |
| 81 | `wrapping` | `wrapping/wrap-args-over-limit.groovy` | ✓ |
| 82 | `wrapping` | `wrapping/wrap-assignment-over-nested-chain.groovy` | ✓ |
| 83 | `wrapping` | `wrapping/wrap-binary-operator.groovy` | ✓ |
| 84 | `wrapping` | `wrapping/wrap-chain-over-limit.groovy` | ✓ |
| 85 | `wrapping` | `wrapping/wrap-ternary-over-limit.groovy` | ✓ |

## 1. `blankline/annotated-field-after-previous-annotations.groovy`

分组：`blankline`　　期望文件：有

**输入**

```groovy
class InsurerHierarchyDO {

    String hierarchyCode
    @CompareField()
    @MessageResource(messageCode = 'A')
    String businessUnit
    @CompareField()
    @MessageResource(messageCode = 'B')
    String businessUnitNo
    int hierarchyLevel

}
```

**期望输出**

```groovy
class InsurerHierarchyDO {

    String hierarchyCode

    @CompareField()
    @MessageResource(messageCode = 'A')
    String businessUnit

    @CompareField()
    @MessageResource(messageCode = 'B')
    String businessUnitNo
    int hierarchyLevel

}
```

## 2. `blankline/annotated-field.groovy`

分组：`blankline`　　期望文件：有

**输入**

```groovy
class AnnotatedField {
    @Deprecated
    private static final long serialVersionUID = 1L
    @Deprecated
    @SuppressWarnings('unused')
    private String name

    @Deprecated
    void annotatedMethod() {
        println name
    }
}
```

**期望输出**

```groovy
class AnnotatedField {

    @Deprecated
    private static final long serialVersionUID = 1L

    @Deprecated
    @SuppressWarnings('unused')
    private String name

    @Deprecated
    void annotatedMethod() {
        println name
    }

}
```

## 3. `blankline/annotated-param.groovy`

分组：`blankline`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class AnnotatedParam {

    @Deprecated
    private String name

    APIResp<RowDTO> updateRow(
            @PathVariable("patternId") String patternId,
            @PathVariable("rowId") String rowId,
            @Valid @RequestBody RowUpdateCmd cmd) {
        @Deprecated
        def local = 1
        return runAndWrap { update(local, cmd) }
    }

}
```

**期望输出**：与输入完全相同。

## 4. `blankline/block-start-end.groovy`

分组：`blankline`　　期望文件：有

**输入**

```groovy
class BlockStartEnd {

    void withBlanks() {

        println 1

    }

    void withClosure() {
        def c = {

            println 2

        }
        c()
    }

}
```

**期望输出**

```groovy
class BlockStartEnd {

    void withBlanks() {
        println 1
    }

    void withClosure() {
        def c = {
            println 2
        }
        c()
    }

}
```

## 5. `blankline/package-imports.groovy`

分组：`blankline`　　期望文件：有

**输入**

```groovy
package com.example.demo
import java.util.List
import java.util.Map
class PackageImports {

    List<String> names
    Map<String, String> index

}
```

**期望输出**

```groovy
package com.example.demo

import java.util.List
import java.util.Map

class PackageImports {

    List<String> names
    Map<String, String> index

}
```

## 6. `fail-closed/array-type.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
int[] a = [1]
```

**期望输出**：与输入完全相同。

## 7. `fail-closed/assert-colon.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
assert x : 'boom'
```

**期望输出**：与输入完全相同。

## 8. `fail-closed/blank-line-indent.groovy`

分组：`fail-closed`　　期望文件：有

**输入**

```groovy
class A {
    def a = 1
   
    def b = 2
}
```

**期望输出**

```groovy
class A {

    def a = 1

    def b = 2

}
```

## 9. `fail-closed/blank-lines.groovy`

分组：`fail-closed`　　期望文件：有

**输入**

```groovy
def a=1



def b=2
```

**期望输出**

```groovy
def a = 1

def b = 2
```

## 10. `fail-closed/case-colon.groovy`

分组：`fail-closed`　　期望文件：有

**输入**

```groovy
switch (x) {
case 1:
break
}
```

**期望输出**

```groovy
switch (x) {
    case 1:
        break
}
```

## 11. `fail-closed/command-expression.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
println 'hi'
```

**期望输出**：与输入完全相同。

## 12. `fail-closed/compact-comparison.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def x = a<b
```

**期望输出**：与输入完全相同。

## 13. `fail-closed/crlf.groovy`

分组：`fail-closed`　　期望文件：有

**输入**

```groovy
def x=1
```

**期望输出**

```groovy
def x = 1
```

## 14. `fail-closed/generic-angles.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
List<String> xs = []
```

**期望输出**：与输入完全相同。

## 15. `fail-closed/line-escape.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def m = 'a' + \
          'b'
```

**期望输出**：与输入完全相同。

## 16. `fail-closed/shebang.groovy`

分组：`fail-closed`　　期望文件：有

**输入**

```groovy
#!/usr/bin/env groovy
def x=1
```

**期望输出**

```groovy
#!/usr/bin/env groovy
def x = 1
```

## 17. `fail-closed/shift-operator.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def x = a << 2
```

**期望输出**：与输入完全相同。

## 18. `fail-closed/trailing-comment.groovy`

分组：`fail-closed`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def x = 1   // note
```

**期望输出**：与输入完全相同。

## 19. `gstring/bare-name-colon.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "line $a:$b $c".toString()
```

**期望输出**：与输入完全相同。

## 20. `gstring/bare-name-then-call.groovy`

分组：`gstring`　　期望文件：有

**输入**

```groovy
def f(){ "a $b".toString() }
```

**期望输出**

```groovy
def f() { "a $b".toString() }
```

## 21. `gstring/bare-name.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "a $b c"
```

**期望输出**：与输入完全相同。

## 22. `gstring/brace-form.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "demo-${x}"
```

**期望输出**：与输入完全相同。

## 23. `gstring/brace-ternary.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "${a ? b : c}"
```

**期望输出**：与输入完全相同。

## 24. `gstring/brace-with-op.groovy`

分组：`gstring`　　期望文件：有

**输入**

```groovy
def s = "n=${a+b}"
```

**期望输出**

```groovy
def s = "n=${a + b}"
```

## 25. `gstring/interpolation-then-statement.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class InterpolationThenStatement {

    private static void check(String code, Object target) {
        P.notNull(target, "can't find config of the ${code}")
        checkMore(code, target.value)
    }

    private static void checkMore(String code, Object v) {
        P.notNull(v, "code: ${code} is not usable")
        P.isTrue(v != null, "outputCode: ${code} could not be used")
    }

}
```

**期望输出**：与输入完全相同。

## 26. `gstring/mixed.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "n=$a+${b}".toUpperCase()
```

**期望输出**：与输入完全相同。

## 27. `gstring/multi-part.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "a-${x}-b-${y}"
```

**期望输出**：与输入完全相同。

## 28. `gstring/triple-quoted.groovy`

分组：`gstring`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def t = """a${x}b"""
```

**期望输出**：与输入完全相同。

## 29. `indent/anonymous-class-in-call.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class A {
void m() {
def r = new Runnable() {
void run() {
println 1
}
}
f(new Runnable() {
@Deprecated
void run() {
println 2
}
})
}
}
```

**期望输出**

```groovy
class A {

    void m() {
        def r = new Runnable() {

            void run() {
                println 1
            }

        }
        f(new Runnable() {

            @Deprecated
            void run() {
                println 2
            }

        })
    }

}
```

## 30. `indent/anonymous-class-on-continuation.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class AnonymousClassOnContinuation {

    void m() {
        return jdbcTemplate.query("select op_id, tenant_code from tenant_op where status=?",
                new RowMapper<Map<String, String>>() {
            @Override
            Map mapRow(ResultSet rs, int rowNum) throws SQLException {
                return [opId: rs.getString(1), tenantCode: rs.getString(2)]
            }
        })
    }

}
```

**期望输出**

```groovy
class AnonymousClassOnContinuation {

    void m() {
        return jdbcTemplate.query("select op_id, tenant_code from tenant_op where status=?",
                new RowMapper<Map<String, String>>() {

                    @Override
                    Map mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return [opId: rs.getString(1), tenantCode: rs.getString(2)]
                    }

                })
    }

}
```

## 31. `indent/anonymous-class-on-string-concat.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class TenantOpQueryServiceImpl {

    @Override
    List<Map<String, String>> getApprovedTenantDataInitialisationTaskIds() {
        return jdbcTemplate.query("select op_id " +
                "from tenant_op where status=?", new RowMapper<Map<String, String>>() {
            @Override
            Map mapRow(ResultSet rs, int rowNum) throws SQLException {
                return [opId: rs.getString(1), tenantCode: rs.getString(2)]
            }
        }, TenantConst.TENANT_OP_STATUS_APPROVED)
    }

}
```

**期望输出**

```groovy
class TenantOpQueryServiceImpl {

    @Override
    List<Map<String, String>> getApprovedTenantDataInitialisationTaskIds() {
        return jdbcTemplate.query("select op_id " +
                "from tenant_op where status=?", new RowMapper<Map<String, String>>() {

            @Override
            Map mapRow(ResultSet rs, int rowNum) throws SQLException {
                return [opId: rs.getString(1), tenantCode: rs.getString(2)]
            }

        }, TenantConst.TENANT_OP_STATUS_APPROVED)
    }

}
```

## 32. `indent/anonymous-class-two-shapes.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class TwoShapes {

    void m() {
        def trailing = run("some long argument string here", new RowMapper<Map<String, String>>() {
            Map mapRow(ResultSet rs, int rowNum) {
                return [a: 1]
            }
        })
        run("some long argument string that pushes this line well past the limit for sure",
                new RowMapper<Map<String, String>>() {
            Map mapRow(ResultSet rs, int rowNum) {
                return [a: 1]
            }
        })
    }

}
```

**期望输出**

```groovy
class TwoShapes {

    void m() {
        def trailing = run("some long argument string here", new RowMapper<Map<String, String>>() {

            Map mapRow(ResultSet rs, int rowNum) {
                return [a: 1]
            }

        })
        run("some long argument string that pushes this line well past the limit for sure",
                new RowMapper<Map<String, String>>() {

                    Map mapRow(ResultSet rs, int rowNum) {
                        return [a: 1]
                    }

                })
    }

}
```

## 33. `indent/as-cast-then-statement.groovy`

分组：`indent`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class AsCastThenStatement {

    void m(tenantCode) {
        Set<String> allCacheKeySet = contentPOList.collect {
            getContentCacheKey(getTenantCode(), it.getContentCode())
        } as Set<String>
        Set<String> versionKeys = contentPOList.collect {
            getContentVersionCacheKey(getTenantCode(), it.getContentCode())
        } as Set<String>
        getContentCache().removeAll(allCacheKeySet)
        getContentVersionCache().removeAll(versionKeys)
    }

}
```

**期望输出**：与输入完全相同。

## 34. `indent/call-args-nested-brackets.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class CallArgsNestedBrackets {

    def save(cell, valueDsl) {
        dslClient.batchSave([new DslSaveCmd(
                tenantCode: cell.tenantCode,
                refType: RefTypeEnum.OUTPUT_HF_PATTERN,
                dslCode: OutputHfPatternDslCodeUtils.cellDslCode(cell.cellId),
                dslContent: valueDsl
        )])
    }

}
```

**期望输出**

```groovy
class CallArgsNestedBrackets {

    def save(cell, valueDsl) {
        dslClient.batchSave([new DslSaveCmd(
                tenantCode: cell.tenantCode,
                refType: RefTypeEnum.OUTPUT_HF_PATTERN,
                dslCode: OutputHfPatternDslCodeUtils.cellDslCode(cell.cellId),
                dslContent: valueDsl
        )])
    }

}
```

## 35. `indent/case-body-indent-from-flat.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class CaseBodyFlat {

    def select(int moduleCode) {
        switch (moduleCode) {
            case 1:
            return ['a']
            default:
            return []
        }
    }

}
```

**期望输出**

```groovy
class CaseBodyFlat {

    def select(int moduleCode) {
        switch (moduleCode) {
            case 1:
                return ['a']
            default:
                return []
        }
    }

}
```

## 36. `indent/case-body-indent.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class CaseBody {

    def select(int moduleCode) {
        switch (moduleCode) {
            case 1:
                return ['a']
            case 2:
            case 3:
                return ['b']
            default:
                return []
        }
    }

}
```

**期望输出**

```groovy
class CaseBody {

    def select(int moduleCode) {
        switch (moduleCode) {
            case 1:
                return ['a']
            case 2:
            case 3:
                return ['b']
            default:
                return []
        }
    }

}
```

## 37. `indent/closure-arg-in-map.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class ClosureArgInMap {

    def build(items) {
        return [
                code: 1,
                items: items?.collect {
                    LabelValueBean.of(it.contentCode)
                }
        ]
    }

}
```

**期望输出**

```groovy
class ClosureArgInMap {

    def build(items) {
        return [
                code: 1,
                items: items?.collect {
                    LabelValueBean.of(it.contentCode)
                }
        ]
    }

}
```

## 38. `indent/closure-arrow-then-body.groovy`

分组：`indent`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class ClosureArrowThenBody {

    void m(list) {
        Closure queryPageDataClosure = { List<String> idList, String tenantCode ->
            List<String> poList = load(idList, tenantCode)
            return poList
        }
        run(queryPageDataClosure)
    }

}
```

**期望输出**：与输入完全相同。

## 39. `indent/dot-chain-continuation.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class DotChain {

    def build(timeout, chromeUserDataDir) {
        Options options = Options.builder()
                .headless(true)
                .readTimeout(timeout)
                .arguments(['--no-sandbox'])
                .userDataDir(chromeUserDataDir.toPath())
                .build()
        return options
    }

}
```

**期望输出**

```groovy
class DotChain {

    def build(timeout, chromeUserDataDir) {
        Options options = Options.builder()
                .headless(true)
                .readTimeout(timeout)
                .arguments(['--no-sandbox'])
                .userDataDir(chromeUserDataDir.toPath())
                .build()
        return options
    }

}
```

## 40. `indent/method-multiline-params.groovy`

分组：`indent`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class MultiLineParams {

    private static OutputHfRenderUnit buildRenderUnit(OutputStepDO outputStep,
            OutputHfRenderProfile renderProfile) {
        OutputHfStepDomain stepDomain = new OutputHfStepDomain(
                stepDomainId: outputStep.stepId,
                boundaryStep: outputStep
        )
        return new OutputHfRenderUnit(
                sourceStep: outputStep,
                stepDomain: stepDomain
        )
    }

    private void single(String a) {
        println a
    }

}
```

**期望输出**：与输入完全相同。

## 41. `indent/nested-block.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class A {
def m() {
return 1
}
}
```

**期望输出**

```groovy
class A {

    def m() {
        return 1
    }

}
```

## 42. `indent/nested-class-blank-lines.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class A {
static final class B {
private static final long serialVersionUID = 1L
void go() {
println 1
}
}
@Deprecated
private static final class C implements Serializable {
private static final long serialVersionUID = 1L
}
}
```

**期望输出**

```groovy
class A {

    static final class B {

        private static final long serialVersionUID = 1L
        void go() {
            println 1
        }

    }
    @Deprecated
    private static final class C implements Serializable {

        private static final long serialVersionUID = 1L

    }

}
```

## 43. `indent/paren-continuation.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
def x = foo(
1,
2
)
```

**期望输出**

```groovy
def x = foo(
        1,
        2
)
```

## 44. `indent/statement-continuation.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class StatementContinuation {

    void m(pageNumberDomainId, flag, other, third) {
        int pageCount = totalByPageNumberDomainId.containsKey(pageNumberDomainId)
                ? totalByPageNumberDomainId.get(pageNumberDomainId) + 1 : 1
        if (flag
                && other
                && third) {
            println pageCount
        }
        return first
                .second()
                .third()
    }

}
```

**期望输出**

```groovy
class StatementContinuation {

    void m(pageNumberDomainId, flag, other, third) {
        int pageCount = totalByPageNumberDomainId.containsKey(pageNumberDomainId)
                ? totalByPageNumberDomainId.get(pageNumberDomainId) + 1 : 1
        if (flag
                && other
                && third) {
            println pageCount
        }
        return first
                .second()
                .third()
    }

}
```

## 45. `indent/top-level-continuation.groovy`

分组：`indent`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def x = a +
        b
```

**期望输出**：与输入完全相同。

## 46. `indent/unary-plus-is-new-statement.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class UnaryPlusIsNewStatement {

    void m(a, b) {
        def s1 = a
        + b
        println s1
    }

}
```

**期望输出**

```groovy
class UnaryPlusIsNewStatement {

    void m(a, b) {
        def s1 = a
        + b
        println s1
    }

}
```

## 47. `indent/wrapped-statement.groovy`

分组：`indent`　　期望文件：有

**输入**

```groovy
class WrappedStatement {

    private static final String PREFIX = "a" +
            "b"

    void m(a, b, c, flag) {
        def s1 = a +
                b +
                c
        def s2 = "x" +
                a
        def n1 = a *
                b
        def n2 = flag
                ? a
                : b
        if (flag
                && a) {
            println a
        }
        return a +
                b
    }

}
```

**期望输出**

```groovy
class WrappedStatement {

    private static final String PREFIX = "a" +
            "b"

    void m(a, b, c, flag) {
        def s1 = a +
                b +
                c
        def s2 = "x" +
                a
        def n1 = a *
                b
        def n2 = flag
                ? a
                : b
        if (flag
                && a) {
            println a
        }
        return a +
                b
    }

}
```

## 48. `invalid/gstring-dollar-space.invalid.groovy`

分组：`invalid`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = "demo-$ { x }"
```

**期望输出**：与输入完全相同。

## 49. `invalid/unbalanced-paren.invalid.groovy`

分组：`invalid`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def x = (
```

**期望输出**：与输入完全相同。

## 50. `invalid/unclosed-string.invalid.groovy`

分组：`invalid`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
def s = 'abc
```

**期望输出**：与输入完全相同。

## 51. `spacing/annotation.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
@Override
def x(){}
```

**期望输出**

```groovy
@Override
def x() { }
```

## 52. `spacing/binary-op.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def x=1
```

**期望输出**

```groovy
def x = 1
```

## 53. `spacing/call-paren.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def y = foo ( 1 , 2 )
```

**期望输出**

```groovy
def y = foo(1, 2)
```

## 54. `spacing/closure-as-first-arg.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
class ClosureAsFirstArg {

    void run(fid) {
        TransactionUtils.withoutTransAfterCurrentTransCommitted( {
            blobService.deleteFile(fid)
        })
        eachItem( { it -> println it })
    }

}
```

**期望输出**

```groovy
class ClosureAsFirstArg {

    void run(fid) {
        TransactionUtils.withoutTransAfterCurrentTransCommitted({
            blobService.deleteFile(fid)
        })
        eachItem({ it -> println it })
    }

}
```

## 55. `spacing/closure-brace.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
list.each{it.name}
```

**期望输出**

```groovy
list.each { it.name }
```

## 56. `spacing/comparison.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def ok=a==b
```

**期望输出**

```groovy
def ok = a == b
```

## 57. `spacing/dot-chain.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def s=a.b?.c*.d
```

**期望输出**

```groovy
def s = a.b?.c*.d
```

## 58. `spacing/empty-closure.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def c = {}
```

**期望输出**

```groovy
def c = { }
```

## 59. `spacing/for-in-colon.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
class ForInColon {

    def render(items) {
        for (OutputHfPatternDO pattern: items) {
            println pattern
        }
    }

}
```

**期望输出**

```groovy
class ForInColon {

    def render(items) {
        for (OutputHfPatternDO pattern : items) {
            println pattern
        }
    }

}
```

## 60. `spacing/for-in-header-others.groovy`

分组：`spacing`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class ForInHeaderOthers {

    def pick(items, flag) {
        for (x in [a: 1]) {
            println x
        }
        for (Map.Entry e : map.entrySet()) {
            println e
        }
        for (y in (flag ? items : [])) {
            println y
        }
    }

}
```

**期望输出**：与输入完全相同。

## 61. `spacing/for-semicolons.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
for(i=0;i < 3;i++){ f(i) }
```

**期望输出**

```groovy
for (i = 0; i < 3; i++) { f(i) }
```

## 62. `spacing/generic-wildcard-others.groovy`

分组：`spacing`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class GenericWildcardOthers {

    def m(a, b, c) {
        def r = a ? b : c
        Class<?> x = null
        Map<String, ?> y = null
        Class<String> z = null
    }

}
```

**期望输出**：与输入完全相同。

## 63. `spacing/generic-wildcard.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
class GenericWildcard {

    Class< ? extends BaseDataVerPoModel> getPoEntityClazz() {
        return null
    }

    Map<String, ? extends Foo> map() {
        return null
    }

}
```

**期望输出**

```groovy
class GenericWildcard {

    Class<? extends BaseDataVerPoModel> getPoEntityClazz() {
        return null
    }

    Map<String, ? extends Foo> map() {
        return null
    }

}
```

## 64. `spacing/keyword-paren.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
if(x){y()}
```

**期望输出**

```groovy
if (x) { y() }
```

## 65. `spacing/map-colon.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def m=[a:1,b:2]
```

**期望输出**

```groovy
def m = [a: 1, b: 2]
```

## 66. `spacing/named-arg-colon.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
foo(a:1)
```

**期望输出**

```groovy
foo(a: 1)
```

## 67. `spacing/postfix-inc.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
i ++
```

**期望输出**

```groovy
i++
```

## 68. `spacing/spread-args.groovy`

分组：`spacing`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
foo(*args)
```

**期望输出**：与输入完全相同。

## 69. `spacing/string-concat.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def s='a'+'b'
```

**期望输出**

```groovy
def s = 'a' + 'b'
```

## 70. `spacing/ternary.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
x = a ? b:c
```

**期望输出**

```groovy
x = a ? b : c
```

## 71. `spacing/trailing-whitespace.groovy`

分组：`spacing`　　期望文件：有

**输入**

```groovy
def x = 1   
```

**期望输出**

```groovy
def x = 1
```

## 72. `spacing/unary-minus.groovy`

分组：`spacing`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
x = -1
```

**期望输出**：与输入完全相同。

## 73. `wrapping/multi-pass-convergence.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class MultiPassConvergence {

    void m(alpha, beta, gamma, delta, epsilon, zeta) {
        def result = builder.firstStep(alpha).secondStep(beta).thirdStep(gamma).fourthStep(delta).fifthStep(epsilon).sixthStep(zeta)
    }

}
```

**期望输出**

```groovy
class MultiPassConvergence {

    void m(alpha, beta, gamma, delta, epsilon, zeta) {
        def result =
                builder.firstStep(alpha).secondStep(beta).thirdStep(gamma).fourthStep(delta).fifthStep(epsilon)
                .sixthStep(zeta)
    }

}
```

## 74. `wrapping/no-wrap-inside-closure.groovy`

分组：`wrapping`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class NoWrapInsideClosure {

    void m(list) {
        list.each { item -> println item.someVeryLongMethodName(firstArgument, secondArgument, thirdArgument, fourthArgument) }
    }

}
```

**期望输出**：与输入完全相同。

## 75. `wrapping/no-wrap-inside-for-header.groovy`

分组：`wrapping`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class NoWrapInsideForHeader {

    void m(collection) {
        for (int index = 0; index < someVeryLongCollectionSizeExpressionProvider.valueOf(collection); index++) {
            println index
        }
    }

}
```

**期望输出**：与输入完全相同。

## 76. `wrapping/no-wrap-inside-generics.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class NoWrapInsideGenerics {

    void m() {
        def result = jdbcTemplate.query("select op_id, tenant_code from tenant_op where status=? and op_type=?", new RowMapper<Map<String, String>>() { })
    }

}
```

**期望输出**

```groovy
class NoWrapInsideGenerics {

    void m() {
        def result = jdbcTemplate.query("select op_id, tenant_code from tenant_op where status=? and op_type=?",
                new RowMapper<Map<String, String>>() { })
    }

}
```

## 77. `wrapping/only-in-closure-break-points.groovy`

分组：`wrapping`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class OnlyInClosureBreakPoints {

    void m() {
        runSomethingVeryLongIndeed { element -> element.getVeryLongPropertyNameThatGoesOnAndOnAndOnAndOn }
    }

}
```

**期望输出**：与输入完全相同。

## 78. `wrapping/try-with-resources-then-catch.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class TryWithResourcesThenCatch {

    private static void m(String license) {
        try (ByteArrayInputStream licenseStream = new ByteArrayInputStream(license.getBytes(StandardCharsets.UTF_8))) {
            new License().setLicense(licenseStream)
        } catch (Exception exception) {
            Panic.notExpectedResult("Output Header/Footer cannot load Aspose license", exception)
        }
    }

}
```

**期望输出**

```groovy
class TryWithResourcesThenCatch {

    private static void m(String license) {
        try (ByteArrayInputStream licenseStream = new ByteArrayInputStream(license.getBytes(StandardCharsets.UTF_8))) {
            new License().setLicense(licenseStream)
        } catch (Exception exception) {
            Panic.notExpectedResult("Output Header/Footer cannot load Aspose license", exception)
        }
    }

}
```

## 79. `wrapping/unbreakable-long-line.groovy`

分组：`wrapping`　　期望文件：无 —— 这条要求【原样不变】

**输入**

```groovy
class UnbreakableLongLine {

    void m() {
        println "this single string literal is far too long to fit inside the configured line length limit at all"
    }

}
```

**期望输出**：与输入完全相同。

## 80. `wrapping/wrap-annotation-attributes.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class A {

    @Parameter(name = "Authorization", description = "Http Basic authorization token", required = true, in = ParameterIn.HEADER)
    void m() {
        def someVeryLongVariableNameHere = computeSomething(alpha, beta, gamma, delta, epsilon, zeta, eta)
    }

}
```

**期望输出**

```groovy
class A {

    @Parameter(name = "Authorization", description = "Http Basic authorization token", required = true,
            in = ParameterIn.HEADER)
    void m() {
        def someVeryLongVariableNameHere = computeSomething(alpha, beta, gamma, delta, epsilon, zeta, eta)
    }

}
```

## 81. `wrapping/wrap-args-over-limit.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class WrapArgsOverLimit {

    void m(a, b) {
        dslClient.saveAllTheThings(firstArgument, secondArgument, thirdArgument, fourthArgument, fifthArgument, sixthArgument)
    }

}
```

**期望输出**

```groovy
class WrapArgsOverLimit {

    void m(a, b) {
        dslClient.saveAllTheThings(firstArgument, secondArgument, thirdArgument, fourthArgument, fifthArgument,
                sixthArgument)
    }

}
```

## 82. `wrapping/wrap-assignment-over-nested-chain.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class WrapAssignmentOverNestedChain {

    void m() {
        AbstractReleaseVersionProvider releaseVerProvider = ReleaseVersionUtils.getReleaseVerProvider(RvRefTypeEnum.OUTPUT_HF_PATTERN.code)
    }

}
```

**期望输出**

```groovy
class WrapAssignmentOverNestedChain {

    void m() {
        AbstractReleaseVersionProvider releaseVerProvider =
                ReleaseVersionUtils.getReleaseVerProvider(RvRefTypeEnum.OUTPUT_HF_PATTERN.code)
    }

}
```

## 83. `wrapping/wrap-binary-operator.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class WrapBinaryOperator {

    void m() {
        def result = firstConditionAlpha && secondConditionBeta && thirdConditionGamma && fourthConditionDelta && fifthConditionEpsilon
    }

}
```

**期望输出**

```groovy
class WrapBinaryOperator {

    void m() {
        def result =
                firstConditionAlpha && secondConditionBeta && thirdConditionGamma && fourthConditionDelta &&
                fifthConditionEpsilon
    }

}
```

## 84. `wrapping/wrap-chain-over-limit.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class WrapChainOverLimit {

    void m() {
        def result = optionsBuilder.withHeadlessModeEnabled().withReadTimeoutInMilliseconds(readTimeout).withChromeUserDataDir(userDataDir).build()
    }

}
```

**期望输出**

```groovy
class WrapChainOverLimit {

    void m() {
        def result = optionsBuilder.withHeadlessModeEnabled().withReadTimeoutInMilliseconds(readTimeout)
                .withChromeUserDataDir(userDataDir).build()
    }

}
```

## 85. `wrapping/wrap-ternary-over-limit.groovy`

分组：`wrapping`　　期望文件：有

**输入**

```groovy
class WrapTernaryOverLimit {

    void m(flag) {
        def message = flag ? "the header pattern is invalid and should be corrected" : "the footer pattern is invalid and should be corrected"
    }

}
```

**期望输出**

```groovy
class WrapTernaryOverLimit {

    void m(flag) {
        def message = flag ? "the header pattern is invalid and should be corrected"
                : "the footer pattern is invalid and should be corrected"
    }

}
```

