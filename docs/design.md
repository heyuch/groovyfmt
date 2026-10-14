# 轻量级 Groovy 格式化工具 —— 可行性调研与实现路径

> 结论先行：**能实现，而且比预想的更有把握。** 但成败取决于一个架构选择——
> 不要做 "AST → pretty-printer"（ktfmt / google-java-format 路线），
> 要做 **token 重排 + 保留 trivia**（clang-format / gofmt 保守派路线）。
> 下文所有关键结论都附了本机实测证据。

---

## 1. 先算账：greclipse 到底有多重（本机实测）

本机 gradle 缓存里正好有 Spotless 拉下来的 greclipse（`groovy-eclipse-integration-5.8.0-e4.36`），
实测数据：

| 项目 | 体积 |
| --- | --- |
| `~/.gradle/caches/p2-data` 总计 | **142 MB** |
| └ Eclipse 平台 bundle-pool (`eclipse-updates-4.36`) | **97 MB** |
| └ Groovy-Eclipse 集成 | **33 MB** |
| `org.codehaus.groovy_5.0.0...jar`（单个 p2 bundle） | **25 MB** |
| `com.ibm.icu` | 14 MB |
| `org.eclipse.jdt.ui` | 11 MB |
| `bcprov` | 8.3 MB |
| SWT（**6 个平台**各一份：cocoa x86_64/aarch64、gtk x86_64/aarch64/ppc64le/riscv64、win32） | 每份 2–3 MB |
| `org.eclipse.ui.workbench` / `org.eclipse.jface` / `org.eclipse.osgi` / `com.sun.jna` | 各 1–3.5 MB |

对比一个**纯格式化工具**需要的依赖：

| 依赖 | 体积 |
| --- | --- |
| `groovy-4.0.30.jar`（内含已 shade + relocate 的 ANTLR4 runtime 与 GroovyLexer/GroovyParser） | 7.4 MB |
| 或 `antlr4-runtime` 单独 | 315 KB |
| 或（Rust/Go 自写 lexer） | **0**（静态二进制 2–3 MB） |

**结论**：你的直觉是对的。greclipse 拉进来的是**一整个 IDE**（SWT 图形层、UI workbench、JFace、
ICU 国际化、BouncyCastle、6 平台原生库），格式化只用到其中 `org.eclipse.jdt.core` 的一小部分。
这里 95%+ 的体积与格式化无关。

---

## 2. 现有生态：这个空位确实存在

| 项目 | 星数 | 说明 |
| --- | --- | --- |
| `diffplug/spotless` | 5.7k | Groovy 支持 = 转发给 greclipse |
| `nvuillam/npm-groovy-lint` | 248 | lint 用 CodeNarc，**format 仍然是调 greclipse** |
| `nvuillam/vscode-groovy-lint` | 90 | 同上，包了一层 |
| `joycollector/groovy-beautify` | 14 | TypeScript 写的简单 beautifier；规模小，未提供语法保真/语义等价的验证（未深入验证其实现） |
| `murtaza64/tree-sitter-groovy` | 26 | tree-sitter 语法存在，但成熟度低（同名的有 5 个仓库，最多 26★） |

**没有 gofmt / ktfmt / rustfmt 级别的 Groovy 格式化器**。这个空位是真实的。

---

## 3. 三个关键技术事实（本机实测，决定了架构）

### 事实 A：Groovy 自带 lexer/parser 可以直接用，而且是公开 API

`groovy-4.0.30.jar` 中：

```
org/apache/groovy/parser/antlr4/GroovyLexer.class         47262 bytes
org/apache/groovy/parser/antlr4/GroovyParser.class       193355 bytes
org/apache/groovy/parser/antlr4/AstBuilder.class         246728 bytes
groovyjarjarantlr4/v4/runtime/...                        （ANTLR4 runtime 已 shade + 重定位）
```

入口规则是 public 的，可直接拿 parse tree：

```java
GroovyLexer lexer = new GroovyLexer(CharStreams.fromString(src));
CommonTokenStream ts = new CommonTokenStream(lexer);
GroovyParser p = new GroovyParser(ts);          // public GroovyParser
ParseTree tree = p.compilationUnit();           // public final CompilationUnitContext compilationUnit()
```

实测（710 字符的样例，含包声明、注解、GString、slashy、多行字符串、闭包、命令式调用）：

```
parse tree root  = CompilationUnitContext
节点总数 = 718, 叶子 = 259, 最大深度 = 60
语法错误 = 无（代码语法正确）
坏代码检测 = 已捕获: line 27:18 mismatched input '(' expecting {...}
```

⇒ **parse tree 可用**，而且**语法错误可以被可靠检测**（"仅处理语法正确的代码" 这个前提可以严格落实）。

### 事实 B：好消息 —— 现成的 token 流**不是**无损的

这是最反直觉、也是最关键的一点。看 Groovy 官方语法 `src/antlr/GroovyLexer.g4`（993 行）：

```antlr
WS  : ([ \t]+ | LineEscape+) -> skip          // ← 水平空白被直接丢弃
    ;

NL  : LineTerminator   { ignoreTokenInsideParens(); }   // ← NL 是语法级 token
    ;

ML_COMMENT
    :   '/*' .*? ('*/' | EOF {...}) { addComment(0); ... } -> type(NL)   // ← 注释被改写成 NL！
    ;

SL_COMMENT
    :   '//' ~[\r\n\uFFFF]* { addComment(1); ... } -> type(NL)           // ← 同上
    ;

SH_COMMENT
    :   '#!' ... ShCommand ... -> skip                                    // ← shebang 直接丢
    ;
```

实测后果：

```
code=147 comments=4 NL=30 WS=0
LOSSLESS ROUND-TRIP (concat(token.getText()) == source): false     ❌
   首次差异 @ 0 raw=[\npackagecom.example.demo// 包名注]
                  src=[#!/usr/bin/env groovy\npackage ]
```

- `WS -> skip`：token 之间所有空格/tab **不在 token 流里**（也顺带吞掉了行尾的 `\` 续行符）
- `SH_COMMENT -> skip`：shebang 整行消失
- 注释被 `-> type(NL)` 改写：所以你会看到 `NL` token 的 text 居然是 `// 包名注释`、`/** Javadoc\n * 多行\n */`

⇒ **直接 `getText()` 拼接永远还原不出原文。** 如果按这个思路做，会静默破坏代码。

### 事实 C：但是 —— 用 token 的字符区间可以做到 **100% 精确还原**

每个 token 都带 `getStartIndex()` / `getStopIndex()`（在**原始源码**中的精确区间）。
把「token 原文 + token 之间的源码 gap」按序拼起来：

```java
StringBuilder rec = new StringBuilder();
int cursor = 0;
for (Token t : toks) {
    if (t.getStartIndex() > cursor) rec.append(src, cursor, t.getStartIndex()); // trivia gap
    rec.append(src, t.getStartIndex(), t.getStopIndex() + 1);                   // token 原始文本
    cursor = t.getStopIndex() + 1;
}
if (cursor < src.length()) rec.append(src, cursor, src.length());
```

实测：

```
source length = 710
A) concat(token.getText())         == source ? false   (len 526)
B) 源码区间重建(token + gap trivia) == source ? true    (len 710)   ✅
```

⇒ **这是整个方案的地基。** 不需要 fork 语法文件、不需要跑 ANTLR codegen、不需要维护 patch，
就能拿到一个**逐字节无损的 CST**。注释、空行、缩进、`\` 续行符、shebang 全部天然保真。

---

## 4. 架构建议：token 重排，而不是 AST 重打印

### 为什么不要走 ktfmt / google-java-format 的路线

Groovy 官方语法规模：`GroovyParser.g4` **1371 行 / 153 条规则**，`GroovyLexer.g4` **993 行 / 7 个模式**
（`DQ_GSTRING_MODE`、`TDQ_GSTRING_MODE`、`SLASHY_GSTRING_MODE`、`DOLLAR_SLASHY_GSTRING_MODE`、
`GSTRING_TYPE_SELECTOR_MODE`、`GSTRING_PATH_MODE`、`DEFAULT_MODE`）。

AST pretty-printer 要求你**为每一种节点重新生成源码**，这意味着：

- 153 条规则对应的语法糖形态你都得能正确打回去（省略括号的命令式调用、`it` 隐式参数、
  slashy string、dollar-slashy、GString 嵌套、方法指针 `::`、spread-safe 导航 `?.@`、
  trait / sealed / record / GINQ……）
- **注释要重新贴回 AST 节点**，而 Groovy 的注释附加（`addComment`）本来只是给 IDE 用的，并不保证覆盖完整
- 任何一处打错都会**静默改变语义**——这是格式化工具最不可接受的失败模式
- 工作量：保守估计 **8000–15000 行**，且长尾永无止境

### 推荐：token-repositioning（只搬运 token，从不重新生成 token）

核心不变式：

> **格式化器只决定 token 之间的 trivia（空白/换行/缩进）长什么样；
> 它从不改变、不新增、不删除任何 token。**

附带好处：

- 多行字符串、多行注释**自动逐字节保真**（它们是单个 token）
- `\` 行续接符、shebang 自动保真
- "不改变语义" 在结构上就有强保证，而不只是靠测试碰运气
- MVP **只需要一个 lexer，完全不需要 parser**（parser 只在 Stage 2 做换行决策时才需要）

这也是 clang-format 的思路。代价是：不能做「删分号」「`def` 补类型」这类改写——
但你已经明确说了不需要太多规则，这个取舍正好。

---

## 5. 分阶段路径

### Stage 0 —— 骨架 + 安全网（约 1 周，600–1000 行）

1. 读取源码 → lexer → token 列表（含 char 区间）
2. 用 §3-C 的方式构造**无损 CST**（trivia 从源码 gap 恢复）
3. **安全闸门**：lexer/parser 报告任何错误 → 原样退出，0 改动，非零退出码
4. **自检**：格式化前先做一次「CST 还原 == 原文」断言，不成立直接拒绝处理该文件
5. 测试骨架 + 语料库（收集真实 Jenkinsfile / build.gradle / Spock 测试作为 corpus）

Stage 0 结束时应能证明：**「不动」是绝对安全的**。

### Stage 1 —— 保守格式化器 MVP（约 2–3 周，1500–2500 行）→ **建议作为 v0.1 发布**

只做这 5 件事，且**绝不新增或删除换行**：

1. **缩进**：按结构层级重排每行行首空白
   （需要区分「块/闭包/Map 字面量/多行参数列表」的层级——纯花括号计数不够，
   因为 `[...]` 和 `(...)` 里的续行也要缩进）
2. **行内空格规范化**：`a+b` → `a + b`，`,` 后加空格，`:` 前后规则，
   但**必须保护命令式调用的语法必需空格**（见 §7 坑 #1）
3. **行尾空白**删除
4. **空行策略**：连续空行压缩到 ≤ N（可配，默认 1）；文件末尾恰好一个换行
5. **幂等**：`format(format(x)) == format(x)`

**为什么「不改换行」是硬规则**：Groovy 的 `NL` 是语法级 token，且在 `(...)`/`[...]` 内被忽略
（`ignoreTokenInsideParens()`）。换行位置的增删**会改变解析结果**（见 §7 坑 #2）。
Stage 1 完全不碰换行 ⇒ 不可能踩这个坑，**风险极低、收益覆盖 80% 真实痛点**
（"Jenkinsfile / Gradle 脚本一团乱" 基本都是缩进和空格问题）。

### Stage 2 —— 宽度驱动的折行（+1–2 个月，4000–8000 行）

到这一步才需要 parser 和 Wadler-style Doc IR（`text` / `concat` / `nest` / `line` / `group`）：

- 从 parse tree 生成 Doc 树，`group` 决定「能放一行就放一行」
- 保留用户的**手工换行意图**（这是比 ktfmt 难的地方：Groovy 社区的代码风格差异极大）
- **任何 join/split 决策之后必须重新解析并与原 AST 比对**，不一致就回退该决策
- 优先级：**优先 join，谨慎 split**（join 更安全，split 容易触发坑 #2）

### Stage 3 —— 落地集成（持续）

- CLI：`groovyfmt --check --diff -w <files>`
- **Spotless 自定义 `FormatterStep`** ← 这是最关键的一步
  这样团队只需要把 `greclipse()` 换成 `groovyFmt()`，`spotlessCheck` 的 CI 流程一行不用改，
  142MB 缓存瞬间消失。这是最现实的推广路径。
- 再来是 Gradle task / Maven plugin / pre-commit hook / LSP（`textDocument/formatting`）

---

## 6. 必须有测试 oracle（比实现本身更重要）

格式化工具最大的风险是**静默改坏代码**。四条自动化防线：

| # | 属性 | 检验方式 |
| --- | --- | --- |
| 1 | **无损** | CST 还原 == 原文（§3-C，Stage 0 起就有） |
| 2 | **幂等** | `format(format(x)) == format(x)`；跑 `--check` 二次确认干净 |
| 3 | **仍可解析** | 格式化后必须无语法错误；否则拒绝输出 |
| 4 | **语义等价** | 格式化前后分别 `groovyc -g:none` 编译，**对比 class 文件字节**；不同则报警。<br>更轻量的替代：对比 `AstBuilder` 产的 `ModuleNode` 结构（忽略位置信息） |

再加 **corpus + fuzz**：拿 Apache Groovy 自己的测试源码、Jenkins 官方插件、
Gradle 官方构建脚本当语料跑 1–4，任何一条不成立就是 bug。

> 第 4 条（字节码比对）是最强的 oracle，实现成本很低，强烈建议 Stage 0 就搭起来。

---

## 7. Groovy 特有的坑清单（写实现前必须知道）

1. **命令式调用省略括号**：`println "hi"` 的空格是**语法必需**的。
   `foo a b` ≠ `foo(a).b`。行内空格规则必须能识别"这是命令式调用"，
   否则 `foo a` 被改成 `foo  a` 还无所谓，但把 `a - b` 认成参数列表就会出事。
2. **换行即分句**（最危险）：Groovy 中
   ```groovy
   x = y
   (a).b()        // 实际被解析为 y(a).b()！
   ```
   `def r = return` / `x =` 结尾的续行、`.method()` 开头的链式调用，都依赖换行位置。
   **Stage 1 禁止动换行就是为了这个。**
3. **`WS -> skip` 吞掉了行续接符 `\`**：`"a" + \` + 换行 + `"b"`。
   如果按"从 token 重新生成"的思路写，这个 `\` 会丢，导致语法错误。
   §3-C 的 gap 恢复天然解决。
4. **slashy string vs 除法**：`/regex/` 与 `a / b` 的歧义由 lexer 的上下文机制处理
   （`ignoreMultiLineCommentConditionally`、`Paren` 栈等）。**不要自己写正则去切 token。**
5. **GString 的 `${}` ≠ 花括号**：lexer 的 GString 模式会把 `${...}` 处理成
   `GStringBegin`/`GStringPart`/`GStringPathPart` 等 token，**不是** `LBRACE`。
   所以缩进逻辑里 `{` 计数不会把 `${...}` 算进去——但你自己写的朴素解析器会算错。
6. **多行字符串与多行注释的正文一个字节都不能动**（token 化天然保证，前提是走 token 重排）。
7. **`NL` 在 `(...)` / `[...]` 内被忽略，但在 `{...}` 内是语句分隔符**——缩进层级要区分这两类容器。
8. **Groovy 3 / 4 / 5 语法有差异**（GINQ、sealed/record、`!!`、switch 表达式）。
   要么绑定一个版本，要么准备多份 grammar/jar。

---

## 8. 语言与形态选型

| 方案 | 启动耗时 | 体积 | 优点 | 代价 |
| --- | --- | --- | --- | --- |
| **A. Java，复用 groovy jar 的 lexer/parser** | ~200–400 ms | 7.4 MB jar（+JVM） | 最快出成果；parser/错误检测白送；可直接对比 AST 做 oracle | 依赖 Groovy 版本；JVM 启动对 pre-commit 略重 |
| B. Java + GraalVM native-image | ~10 ms | ~15–25 MB 二进 | 保留 A 的开发效率，去掉 JVM 启动 | native-image 配置成本；反射/ANTLR 可能需配置 |
| **C. Rust/Go，自写 lexer** | **~5–20 ms** | **2–3 MB 静态二进制** | 真正的"轻量"终点；pre-commit / IDE 体验最好 | 需要移植 993 行 `.g4` 的 7 个模式；Stage 1 只需 lexer，不需 parser（可行） |

**建议路线**：先用 **A** 快速把规则和测试语料打磨出来（这时迭代速度最重要），
规则稳定后如果确实需要，再按 **C** 把 lexer 移植成单二进制。
不要一开始就在 Rust 里手搓 GString 的 7 个 lexer 模式——那是纯粹的移植体力活，
应该放在规则已经确定之后。

> 注意：移植 `src/antlr/GroovyLexer.g4` 属于衍生作品，语法文件是 Apache-2.0，
> 保留 LICENSE/NOTICE 即可（比 greclipse 的 EPL 更宽松）。

---

## 9. 工作量总估

| 阶段 | 人周 | 累计产物 |
| --- | --- | --- |
| Stage 0 骨架 + 安全网 + oracle | ~1 周 | 「不动」绝对安全，CST 无损验证通过 |
| Stage 1 保守格式化（缩进+空格，不动换行） | ~2–3 周 | **v0.1 可用**，覆盖绝大多数真实痛点 |
| Stage 2 折行（Doc IR + parse tree） | +1–2 月 | 与 greclipse 输出可比的成品 |
| Stage 3 集成（Spotless / CLI / LSP） | 持续 | 能替换 greclipse |

**核心判断：Stage 0 + Stage 1（约 1 个月）就足以做出一个比 greclipse 轻 3 个数量级、
且能解决 80% 需求的工具。** 真正的工程量在 Stage 2 的长尾，而那部分完全可以延后。

---

## 10. 规则抽象设计：为什么 Command 不够，以及应该用什么

### 10.1 先把数据模型定下来，模式会自己浮现

在 token 重排架构下，一个文件可以被完整描述为：

```
[gap₀] tok₀ [gap₁] tok₁ [gap₂] ... tokₙ [gapₙ₊₁]
```

> **格式化 = 为每个 gap 重新决定它的空白内容。**

一个关键推论（来自 §3 事实 B）：注释在 Groovy token 流里**本身就是 token**
（`SL_COMMENT` / `ML_COMMENT` 都被 `-> type(NL)` 改写），所以 **gap 里只剩空白类 trivia**：
空格、tab、换行、`\` 续行符、以及被 skip 掉的 shebang。

⇒ 只要「从不新增/删除/改写 token，只重写 gap」这条不变式成立，
**语义安全就是结构性保证**，而不是靠测试碰运气。这也是整个规则 API 的地基。

> ⚠️ **本章的这条推论在 PoC 落地后被证伪了一半**，见 §11.1。
> 结论仍然成立，但需要补一个前提条件：**两侧都不能有词法错误**。

### 10.2 反面教材：为什么"每个规则一个类，各自改写文本"会崩

看一行真实的 Groovy：

```groovy
def result = someService.fetchAll(params).findAll { it.active }.collect { it.name }
```

假设拆成三个独立的 Command 规则，各自拿文本改：

| 规则 | 想做的事 |
| --- | --- |
| `DotSpacingRule` | `.` 两侧不加空格 |
| `ClosureBraceRule` | `{` 前加一个空格 |
| `MaxLineLengthRule` | 超过 100 列就在 `.findAll` 前断行，续行缩进 +4 |

问题在于**它们不是独立的，存在反馈循环**：

1. 折行规则决定在 `.findAll` 前断开
2. 断行后缩进变了 → 但缩进深度取决于"这是链式调用的续行"，需要折行规则的结果
3. 缩进变宽后，本行可能**又超宽** → 需要重新折行
4. 而且断行位置如果选错（比如断在 `(` 之前），在 Groovy 里**会改变语义**（§7 坑 #2）

Command 模式的隐含假设是"命令之间正交、可顺序执行"。格式化规则**不满足这个假设**：
它们共享同一个输出、互相读取对方的结果、并且有优先级和覆盖关系。
把规则做成一个个独立改文本的 Command，结果是：

- 执行顺序变成隐式契约（改顺序就改输出，且没有任何地方声明这件事）
- 幂等性无法保证（A 改完 B 又改回来，或需要多轮才收敛）
- 冲突只能靠"后写的赢"这种偶然性解决
- 无法做"这个 gap 到底该由谁负责"的静态检查

### 10.3 推荐：把 gap 拆成两个正交轴 + 槽位所有权

一个 gap 的输出其实只有两件事：

```
Gap = ⟨垂直：是否断行 / 断几行 / 缩进多少⟩  ×  ⟨水平：不断行时几个空格⟩
```

把这两件事**分给不同的管辖区**，冲突就消失了：

```java
enum Spacing { NONE, SPACE, PRESERVE }      // 水平轴
enum Breaks  { NONE, ONE, BLANK, PRESERVE } // 垂直轴
```

再引入**槽位（Slot）**：不按"第几个 gap"寻址，而按**语义位置**寻址。

```java
enum Slot {
    BEFORE_LPAREN_CALL,      // foo(1)      —— 调用，不空格
    BEFORE_LPAREN_DECL,      // def foo(    —— 声明，不空格
    AROUND_BINARY_OP,        // a + b
    AFTER_COMMA,             // , 后一个空格
    BEFORE_LBRACE_CLOSURE,   // { it -> ……  —— 前一个空格
    AROUND_MAP_COLON,        // [k: v]
    AROUND_ELVIS,            // a ?: b
    BEFORE_SEMICOLON,        // ; 前不空格
    AFTER_KEYWORD,           // if / return / assert 后
    // ... 约 30–60 个，覆盖语言就完事
}
```

有一个 **SlotClassifier** 负责把 `(prevToken, nextToken, 上下文)` 分类到一个 Slot。
这一步是 O(1) 分派，让规则变成纯查表。

```java
/** 水平空白规则：声明自己占据哪个 Slot —— 一个 Slot 同时只允许一个 owner */
public interface SpacingRule {
    String id();
    Slot slot();                       // 我负责哪个槽位（唯一）
    Spacing decide(GapContext ctx);    // 纯函数：不表态就返回 PRESERVE
}
```

**"一个 Slot 只能有一个 owner" 是这个设计的核心契约。** 它在注册时就能静态校验：

```java
public final class RuleRegistry {
    public void register(SpacingRule r) {
        Slot prev = owners.putIfAbsent(r.slot(), r);
        if (prev != null)
            throw new IllegalStateException(
                "Slot " + r.slot() + " 已被 " + prev.id() + " 占用，" + r.id() + " 冲突");
    }
}
```

⇒ **新增一条规则 = 新增一个类 + 一个 Slot**，不动任何既有代码，而且**不可能和已有规则打架**
（要么编译期就报冲突，要么语义上互不重叠）。这才是"只加类就能扩展"能真正成立的形态。

### 10.4 垂直轴单独管：缩进用所有权，折行用罚分投票

**缩进（Stage 1 就够用）**——确定性，所有权模型：

```java
public interface IndentRule {
    /** 为某个 token 所在的逻辑深度贡献增量，可能为负 */
    int indentDelta(IndentContext ctx);
}
```

`IndentContext` 提供括号栈、是否闭包体、是否链式调用续行、是否 case 分支……
最后由 `LayoutResolver` 累加得到该行的缩进层级。**注意缩进按逻辑深度算，不按实际列宽算**
——这样才切断了 §10.2 里那个"缩进↔折行"的反馈循环（clang-format 也是这么做的）。

**折行（Stage 2）**——这个地方才真正需要"多方投票"，因为断点选择天然是全局优化：

```java
public interface LineBreakRule {
    /** 在候选断点处的代价，越大越不愿在此断开；返回 -1 表示禁止在此断开 */
    int penalty(BreakCandidate c);
}
```

各规则给候选断点打分（`MaxLineLength` 给基础罚分、`PreferBreakAfterComma` 减分、
`NeverBreakBeforeLParenInGroovy` 直接返回 -1 禁止），
`LayoutResolver` 跑一个小的动态规划取总代价最小解。

⇒ **两种模式并存，各管一层**：
- 水平空白 + 缩进 → **槽位所有权**（确定性、无冲突、可静态校验）
- 折行 → **罚分投票**（多贡献者、全局最优）

### 10.5 那 Command 模式用在哪？——用在你没想到的两层

Command 在规则层不合适，但在另外两层**非常合适**：

**① 文件级管道（Spotless 的 `FormatterStep` 就是这个）**

```java
public interface FormatterStep {           // 粗粒度、整文件、顺序执行、天然正交
    String name();
    String apply(String source);
}
// NormalizeLineEndings → StripBom → EnsureFinalNewline → Format → ...
```
这些步骤之间**确实正交**（改行尾不会影响缩进规则），所以 Command 的假设在这里成立。

**② CLI / 编辑器动作（经典 Command，还能白送 undo）**

```java
public interface FormatAction { void execute(FormatContext ctx); }
// CheckAction（--check，非零退出）
// WriteAction（-w）
// DiffAction（--diff，只打印 unified diff）
// StdinAction（stdin → stdout，给编辑器和 LSP 用）
```
把"做什么"和"操作哪个文件"解耦，`--check`/`-w`/`--diff`/stdin 四种模式就是四个类，
互不干扰，也方便做 dry-run 和预览。

⇒ 一句话：**Command 管"阶段"，不管"规则"。**

### 10.6 别忘了 Annotation Pass（最容易漏、但对扩展性最关键）

规则如果各自去推导"这个 `(` 是调用还是声明""这个 `:` 是 Map 键还是三元还是 label"，
会导致同一段逻辑在 N 个规则里重复实现——这是扩展性最大的杀手。

所以规则跑之前先做**一遍标注**，给 token 打上语义角色：

```java
public final class TokenAnnotator {
    // 给每个 token 附加角色，规则只消费角色，不自己推导
    // CALL_LPAREN / DECL_LPAREN / CLOSURE_BRACE / MAP_LBRACE / GSTRING_BRACE(不会出现，见坑#5)
    // TERNARY_COLON / MAP_COLON / LABEL_COLON / COMMAND_ARG / SPREAD_DOT / SAFE_CHAIN_DOT ...
}
```

Groovy 自带 parser 已经把这件事做了一大半（`AstBuilder`、`Paren` 栈、
`ignoreTokenInsideParens`），能复用就复用。

**扩展性公式**：规则新不新，取决于它能不能只消费 `Slot + TokenRole + FormatStyle` 就写完。
能做到 ⇒ 20 行的类；做不到 ⇒ 说明 TokenAnnotator 缺角色，应该去补标注，而不是在规则里写特例。

### 10.7 规则契约（写进接口 javadoc，并且用测试强制）

每条规则必须满足：

1. **纯函数**：只看 `GapContext` / `TokenRole` / `FormatStyle`，不看中间态文本、不看全局可变状态
2. **只碰自己的 Slot**：绝不修改 token 文本、绝不新增/删除 token
3. **幂等**：`apply(apply(x)) == apply(x)`
4. **可关可调**：行为从 `FormatStyle` 读，不硬编码

配套的测试夹具（每条规则一份，自动跑）：

```java
@RuleTest
class BeforeLParenCallTest {
    // ① 黄金样例：输入 → 期望输出
    // ② 幂等：format(format(x)) == format(x)
    // ③ 无损：CST 还原 == 原文（token 序列不变）
    // ④ 交互：与相邻 Slot 的所有者规则组合后仍可解析
}
```

### 10.8 选型清单（一句话版）

| 关注点 | 模式 | 说明 |
| --- | --- | --- |
| 规则（水平空白） | **Strategy + 槽位所有权** | 一 Slot 一 owner，注册期校验冲突 |
| 缩进 | **Strategy + 累加器** | 按逻辑深度，不按列宽 |
| 折行 | **罚分投票 + DP**（clang-format 路线） | 多贡献者、全局最优、可禁止某断点 |
| 上下文推导 | **TokenAnnotator（预标注）** | 角色集中算一次，规则只消费 |
| 冲突仲裁 | **Mediator / Resolver** | 唯一知道"最终 gap 长什么样"的地方 |
| 输出 | **Renderer（纯函数）** | 只拼 trivia，永不生成 token |
| 文件级阶段 | **Command** | `FormatterStep` 管道 |
| CLI / 编辑器动作 | **Command** | `--check` / `-w` / `--diff` / stdin，白送 undo |
| 第三方扩展 | **SPI（ServiceLoader）** | 仅在真有人需要时再加 |

### 10.9 两个反向提醒

**① 不要一上来就搭插件框架。** ServiceLoader + 反射会拖慢 CLI 启动
（对 pre-commit 场景是实打实的体感），而且 Stage 1 根本不需要。
先用 `List<SpacingRule>` 显式注册，等第三方扩展成为真实需求时再加 SPI——
**接口设计对了，换成 SPI 是半小时的事。**

**② 顺序反了：先定数据模型，模式会自己浮现。**
真正决定"加规则是否只需加一个类"的，不是用了哪个模式，而是
**Gap / Slot / TokenRole 这三个数据模型定义得好不好**。
数据模型对了，加一条规则是 20 行；数据模型错了（比如规则拿的是裸文本区间），
用再漂亮的模式也救不回来——每条新规则都得重新解析一遍上下文。

---

## 11. 实战复盘：PoC 落地后新增/更正的结论

> 本节记录的每一条都是**写实现时才暴露出来**的，不是纸面推演能得到的。
> 其中 §11.1 直接更正了本文档前面的一个错误推论。

### 11.1 ❌ 更正：「同 token 序列 ⇒ 同语义」需要一个前提

本文档 §10.1 原本的推论是：只要 token 序列不变，语义就等价。**这个说法不完整。**

PoC 落地时踩到的真实 bug：格式化把 `"demo-${x}"` 改成了 `"demo-$ { x }"`。
- token 序列**完全没变**
- ANTLR 语法树**完全没变**
- 但**真正的 Groovy 编译器直接拒绝**：`token recognition error at: ' '`

根因有两层：

1. **水平空白并非在所有地方都不重要。** GString 的 `${` 内部，`$` 与 `{` 之间的空白
   会让词法器从"插值模式"退出 —— 这是 §3 事实 B 的例外。
2. **ANTLR 的词法错误恢复会静默跳过无法识别的字符。**
   于是两份文本可以有**完全相同**的 token 序列，其中一份却是非法的。

⇒ 正确的表述是：

> **「token 序列不变」+ 「两侧都不存在词法错误」 ⇒ 语义不变。**
> 缺了第二个条件，第一个条件就是不可靠的。

**两层修法**（都要做）：
- **治标**：恢复词法 error listener。因为 ANTLR 的 `ANTLRErrorListener` 与 `Parser` 的
  类型参数不同（`Integer` vs `Token`），没法共用一个监听器 —— 但 relocate 后的
  `ANTLRErrorListener` 只剩一个 `syntaxError` 方法，直接实现它即可（不要像最初那样
  图省事把监听器删掉）。
- **治本**：把 GString 的 `${...}` 花括号标记为 `interpolationBrace`，任何贴着它的 gap 一律保留原文。

**架构层面的教训**：安全闸门不能只依赖"输出与输入的 token 序列对比"，
必须**对输入和输出各自独立做一遍权威语法检查**。PoC 里最终叠了三层：
词法监听器 → ANTLR 语法树 → **真正的 Groovy 编译器**（`CompilationUnit.compile(Phases.CONVERSION)`）。
最后这层才是"语法正确"的权威定义 —— 前两层都会漏掉东西。

### 11.2 `<<` / `>>` 不是 token，`=~` 是

实测（`a << 2`）：

```
Identifier(a)  LT(<)  LT(<)  IntegerLiteral(2)
```

Groovy 语法里**根本没有独立的 `<<` token**，只有 `LSHIFT_ASSIGN(<<=)`。
`<<` 是两个 `LT`，`>>>` 是三个 `GT`。这直接影响 `SlotClassifier`：
"尖括号是泛型还是比较/移位"的歧义判断要把移位一并覆盖进去。

另外 `=~`（find 运算符）和 `==~`（match 运算符）是**单个 token**，
所以 `def r=~/(\d+)/` 是**非法** Groovy（缺少 `=`），必须写 `def r = ~/(\d+)/`。

⇒ **凡是涉及词法的假设，一律先写探针实测再写代码。**

### 11.3 空行缩进：优先级顺序写反了会引入行尾空白

空行由**连续的 NL token** 表示，所以"空行的缩进"其实是**前一个换行 token 之后的那个 gap**。
如果 LayoutResolver 先判"上一个 token 是换行 → 这是行首缩进"，就会给空行填上缩进空格，
凭空造出一堆行尾空白。

正确顺序：**先判"下一个 token 是换行 token → 这是行尾空白"**，再判行首缩进。
（这条也说明：`Slot.TRAILING_WHITESPACE` 有一个反直觉的职责 —— 它同时负责清掉空行的缩进。）

### 11.4 统计口径：`--stats` 曾经永远显示 100% 覆盖率

最初 `stats.horizontalGaps` 只在**分类成功之后**才自增，于是"未分类"的 gap 根本没进分母，
覆盖率恒等于 100% —— 一个**看起来很正常、实际上在说谎**的指标。

⇒ 度量本身也要测。覆盖率这个数字是判断"该补哪条规则"的唯一依据，它错了整个迭代方向就错了。

### 11.5 实测数据（Apache Groovy 仓库 17 个真实 `.groovy` 文件，112 KB）

| 指标 | 结果 |
| --- | --- |
| 被安全闸门拒绝的文件 | **0** |
| 触发回退的文件 | **0** |
| 水平 gap 分类覆盖率 | **95.3%**（10405 / 10915） |
| 主动保留原文（fail-closed） | 4.7% |
| 幂等复检（格式化后再 `--check`） | 通过 |
| 单元测试 | 69 项全通过 |

**说明框架的"fail-closed"设计是成立的**：95% 的位置能规范化，
剩下 5% 没看懂就不动，而且**没有任何一个文件因为看不懂而被整体拒绝**。

### 11.6 一个必须正视的设计张力：排他 owner ↔ 策略可插拔

PoC 落地后，"一个 Slot 只允许一个 owner"这条契约的**两面**都暴露出来了：

- ✅ **好处**：新增规则**不可能**改变既有行为，冲突在注册期就报错。
  这是"加一个类就能扩展"能真正成立的唯一原因。
- ⚠️ **代价**：**不能靠"再加一个类"来覆盖一个已被认领的位置的行为**。
  要改已有位置的风格，只能换 owner，或者让规则读 `FormatStyle` 参数化。

也就是说，这套设计给的是**位置维度的扩展性**（覆盖更多语法位置），
不是**策略维度的可插拔性**（同一位置选不同风格）。

如果将来真的需要策略插拔，就得把排他 owner 换成**带优先级的投票链** ——
但那样冲突就从"注册期静态错误"退化成"运行期隐式行为"，需要重新设计冲突仲裁。

**结论：先明确要哪一种扩展性，再决定要不要动这个契约。**
就"轻量、规则不多、house style 固定"的定位而言，现在的取舍是对的。

---

## 12. 质量闸门实践：把自举变成 CI 的一道关

PoC 用 Gradle（Groovy DSL）+ CodeNarc + 自研测试跑器，全部挂在 `./gradlew check` 上：

| 闸门 | 作用 |
| --- | --- |
| `codenarcMain` / `codenarcTest` | 静态分析，`maxPriority1/2/3Violations = 0` |
| `formatCheck` | **自举**：用刚构建出的 formatter 检查它自己的源码 |
| `runTests` | 73 项：黄金样例 + 幂等 + 无损 + token 序列不变 + 语法树不变 |

### 12.1 三条配置原则（比规则本身更重要）

1. **不在 config 里搞"关掉整个规则集"式的一刀切**，每条取舍都写明理由。
2. **格式问题完全交给 formatter**，linter 不参与。首轮 CodeNarc 报的 78 条里，
   42 条是 `ClassEndsWithBlankLine` —— 纯粹是"另一套格式标准"。
   如果两套都开着，就会出现**IDE 格式化 / linter 格式规则 / git hook 各说各话**，
   这是实践中最常见的坑。正确做法是：**格式只有唯一权威（formatter），linter 只管非格式问题。**
3. **有意为之的偏离用 `@SuppressWarnings` 就地标注理由**，而不是把全局阈值调松。
   例如 `CatchThrowable` —— 必须捕获一切才能真正 fail-closed，
   这是设计目的，不是疏忽：

   ```groovy
   @SuppressWarnings('CatchThrowable')   // 有意为之：必须捕获一切才能真正 fail-closed
   static Cst build(String source) throws GroovySyntaxException { ... }
   ```

### 12.2 首次接入 CodeNarc 的战果

**78 条违规（P1=0, P2=21, P3=57）→ 0 条。** 其中最有价值的一条是**真死代码**：

```
Rule=UnusedPrivateMethodParameter P=2
  Method parameter [prevRole] is never referenced in the method classifyToken
```

这个参数从 Java 版一路带到 Groovy 版都没人发现 —— 因为它不影响任何行为。
其余多为风格类：`FactoryMethodName`（`buildMapping` 被误认为工厂方法）、
`ConfusingMethodName`（私有字段与同名方法重名）、`FieldName`（`static final` 没大写）、
`UnnecessaryGetter/Setter`（该用 Groovy 的 `.empty` 和属性语法）。

⇒ **静态分析工具的价值不在"抓到多少条"，而在"逼你把每一条变成一个有意识的决定"。**

---

## 13. 移植到 Groovy 与自举：又抓到两个真 bug

为了让 formatter 能格式化自己，PoC 从 Java（2365 行）移植到 Groovy（2349 行）。
**注意行数几乎没变** —— 因为文档注释占了大头；Groovy 的简洁性体现在
**每条规则平均 20 行**这类地方，而不是总体积。移植本身靠 73 项测试兜底，语义完全等价。

真正值钱的是自举：**它抓到了 17 个真实文件语料都没触发的两个真 bug。**

### 13.1 必须用 `GroovyLangLexer` / `GroovyLangParser`

一开始用的是 `GroovyLexer` / `GroovyParser`。它们对
**「GString 紧跟方法调用」**（如 `"a $b".toString()`）会误报：

```
token recognition error at: '.toString()'
```

而这段代码用真正的 Groovy 编译器编译**完全合法**。
`GroovyLang*` 才是 Groovy 编译器实际使用的那一对（`Antlr4ParserPlugin` 用的就是它），
带有正确的上下文消歧逻辑（`PositionAdjustingLexerATNSimulator`）。

> 注意这个 bug 的表现是"文件被**跳过**"而不是"代码被改坏" ——
> 这正是 §10 里 fail-closed 设计的价值：**看不懂就不动**。

### 13.2 `RollBackOne` 伪 token 会让 CST 失去无损性

换成 `GroovyLangLexer` 后立刻暴露出第二个问题。它为了实现「`$` 归属」的位置调整，
会插入 **`RollBackOne` 伪 token**，语义是"上一个 token 的结束位置回退一个字符"，
真正的内容由紧随其后的 token 覆盖：

```
GStringBegin [5713..5719] `"line $`
Identifier   [5720..5723] `line`
RollBackOne  [5724..5724] `:`        ← 伪 token，不是内容
GStringPart  [5724..5725] `:$`       ← `:` 真正属于这里
```

不丢弃 `RollBackOne`，就会出现 `concat(token) != source`，
**§10.1 那条"无损 CST"的地基就塌了**。

**根因是测试覆盖缺口**：之前所有 GString 样例都只用了 `${}` 花括号形式，
没有一个用裸 `$name` 形式。修完补了 4 条回归测试。

> **这两条结论纸面推演永远得不到。** 它们都属于"语料碰不到、只有自举才会碰"的那一类，
> 因为 formatter 自己的源码恰好大量使用 `"$x".method()` 这种写法。
> **先做能跑的东西，再谈框架** —— 这个顺序是对的。

### 13.3 自举顺带证明了 formatter 不会改坏自己

首轮 `formatApply` 改动 7 个文件 66 行（主要是把"与开括号对齐"的续行缩进
统一成自己的 `braceDepth×4 + parenDepth×8` 逻辑缩进模型），然后：

- 用被格式化过的源码**重新编译** → 73/73 测试通过
- 再跑 `formatCheck` → **完全幂等，0 改动**

⇒ 这就是自举作为**最强的回归测试**的意义：formatter 每天格式化自己的源码，
任何规则回归都会在下一次 `check` 里立刻暴露。

### 13.4 最新实测数据

| 语料 | 指标 | 结果 |
| --- | --- | --- |
| Apache Groovy 仓库 23 个 `.groovy`（216 KB） | 被拒绝 / 回退的文件 | **0 / 0** |
| | 水平 gap 分类覆盖率 | **93.0%**（21073 / 22655） |
| | 主动保留原文 | 7.0% |
| | 写回后再 check | 幂等通过 |
| 项目自身 | 主代码 / 规则 | 39 文件 2349 行 / 14 条规则（平均 20 行） |
| | CodeNarc | P1=0 P2=0 P3=0 |

---

## 14. 测试策略：E2E 黄金样例为主，但有三个不能省的前提

关于"是不是主要做 E2E 测试（给各种 Groovy 片段，跑完整 formatter 比对结果）"这个问题，
结论是：**是，而且这是正确的主策略** —— 但 golden 测试单独用有两个隐蔽的失效模式，
必须补两件事，再加一件基础设施。

### 14.1 为什么 E2E 为主是对的

**formatter 的契约就是"文本 → 文本"**，E2E 恰好测的就是这个契约。
更关键的是：这个项目里真正危险的 bug **全都出现在跨层交互上**，单元测试测不到：

| bug | 涉及层 |
| --- | --- |
| `${` 里加空格改变语义 | lexer 模式 + 规则 + 安全闸门 |
| `GroovyLexer` 对 `"$x".method()` 误报 | lexer 选型 |
| `RollBackOne` 伪 token 破坏无损性 | lexer 位置调整 + CST 构造 |

所以资源应该投在"**更多、更真实的语法片段**"，而不是"更细的方法拆分"。
而且 fixture 化之后，**加一个用例 = 加一个文件**，不用碰测试代码 —— 这是覆盖长尾的唯一可行方式。

### 14.2 前提一：fail-closed 会让 golden 静默失效（必须断言 `!skipped`）

这是本项目特有的陷阱。formatter 是 fail-closed 的：遇到看不懂的代码就**原样返回**。
而"输入 = 期望"的 golden 用例，在"正确地没改"和"根本没处理"两种情况下**结果完全相同**：

```
format(输入) == 输入     ← 可能是"正确地没改"，也可能是"被拒绝了"
```

⇒ **runner 必须先断言 `!skipped`**，否则一个拒绝对应一整片假通过。

这条已经用实验验证过：往 `spacing/` 丢一个语法错误的 fixture（不带 `.expected`），
runner 立刻报 `被安全闸门跳过（golden 测试会因此静默失效）`。

> 一般化的教训：**当被测系统有"拒绝/降级"这条路径时，
  任何"输出等于输入"形式的断言都是不可靠的**，必须额外断言"它真的执行了"。

### 14.3 前提二：golden 记录的是"当前行为"，不是"正确行为"

它无法区分"正确"和"一致地错"。所以每个 fixture 还要独立过一遍**语义不变式**：

- 无损（CST 能逐字节还原）
- token 序列完全一致
- 语法树形状完全一致
- 幂等（且断言的是**期望输出本身**是稳定不动点，比 `format(format(x))` 更强）

这些判据与 golden **相互独立** —— golden 错了，不变式还是能抓到。

### 14.4 基础设施：bless 工具，但要有两个约束

没有 bless 工具的 golden 套件，改一次规则就要手工改几十个期望文件，很快就会被放弃。
但 bless 是把双刃剑，所以要加两个约束：

1. **只更新已存在的 `.expected.groovy`**。本来没有期望文件却发生了变化 → **报错，不自动生成**。
   否则"新增一个语法片段"这个动作会顺手把当前行为固化成"期望"。
2. 更新后打印 `⚠ 已更新 N 个 .expected.groovy —— 请审查 diff`。
   批量 bless 之后人工过一遍 diff 是**不可省略**的一步。

### 14.5 前提三：少数测试必须留在 API 层

E2E 覆盖不了这三类，硬塞进 E2E 只会测得更差：

| 类型 | 例子 | 为什么 E2E 不行 |
| --- | --- | --- |
| 注册期契约 | Slot 冲突在构造 `RuleRegistry` 时抛异常 | 没有文本输入输出 |
| **指标口径** | `horizontalGaps == classified + unclassified` | golden 永远发现不了"覆盖率恒等于 100%"这种谎报 |
| CLI 退出码 | 1=有差异 / 0=干净 / 2=语法错误 | 是 CI 契约，必须子进程端到端验证 |

### 14.6 落地结果

| 层 | 数量 | 内容 |
| --- | --- | --- |
| E2E fixtures | **45** | `spacing/` 17、`fail-closed/` 13、`gstring/` 9、`indent/` 3、`invalid/` 3 |
| API 契约 | 6 | 注册冲突、指标口径、错误信息质量 |
| CLI 契约 | 4 | 退出码 1/0/2 + stdin 管道 |
| 真实样例冒烟 | 2 | `examples/Messy.groovy` |

原来内联在测试代码里的 42 条黄金样例**全部迁移成了 fixture 文件**，
测试代码从 381 行降到约 250 行，而且**加用例不再需要改代码**。

### 14.7 一个副产品：CodeNarc 又抓出一例"格式标准打架"

写 fixture runner 时 CodeNarc 报了 `SpaceAroundMapEntryColon`。查下来它的**默认配置**
要求 Map 字面量的冒号后**不能**有空格（`characterAfterColonRegex = \S`），
也就是 `[code:1]` —— 而 Groovy 的通用惯例和本 formatter 的输出都是 `[code: 1]`。

⇒ 这条规则的默认值会**和任何主流 Groovy 格式化器打架**。按 §12.1 的原则排除。

至此已经排除三条纯格式规则（`ClassEndsWithBlankLine` / `ClassStartsWithBlankLine` /
`SpaceAroundMapEntryColon`）。**每一条都是同一个问题的不同表现：
格式标准必须只有唯一权威。**

---

## 15. 更正：格式化规则不应该从 CodeNarc 排除，而应该"配置对齐"

§12.1 给的第 2 条原则是"格式问题完全交给 formatter，linter 不参与"，
并据此排除了 `ClassEndsWithBlankLine` 等一批规则。**这条原则是错的，本节予以更正。**

### 15.1 正确的模型：spec 与 gate 是互补的，不是重复的

| 角色 | 谁 | 性质 |
| --- | --- | --- |
| **规范 spec** | formatter 的规则 + fixture | **充分条件**：把代码变成确定的形状，且能自动修复 |
| **门禁 gate** | linter 的格式化规则 | **必要条件**：独立检查形状是否真的合规 |

关键在于：**linter 的格式化规则大多只是必要条件，不是充分条件。**
`SpaceAroundOperator` 只要求运算符旁"有"空格，不要求"恰好一个"；
`Indentation` 只检查缩进是否成倍数，不检查逻辑深度对不对。
所以 linter **当不了 formatter 的规范**。

但正因为它比 formatter 弱、而且是**独立实现**的，它就能在 formatter 出 bug 时
把不合格的输出拦下来。**这个"独立第二意见"的价值，是排除规则所放弃的。**

⇒ 正确做法：**能用配置对齐的就配置，而不是排除。**

### 15.2 典型案例：`SpaceAroundMapEntryColon`

这条规则的**默认配置**要求 Map 字面量冒号后不能有空格（`characterAfterColonRegex=\S`，即 `[code:1]`），
而 Groovy 通用惯例和本 formatter 都是 `[code: 1]`。

我最初的反应是"排除"。但规则是**可配置的**：

```xml
<ruleset-ref path='rulesets/formatting.xml'>
    <exclude name='SpaceAroundMapEntryColon'/>
</ruleset-ref>
<rule class='org.codenarc.rule.formatting.SpaceAroundMapEntryColonRule'>
    <property name='characterBeforeColonRegex' value='\S'/>
    <property name='characterAfterColonRegex' value='\s'/>
</rule>
```

**排除 = 放弃门禁；配置 = 保留门禁。** 差别很大。

（顺带一个 CodeNarc 配置的坑：`<rule-config>` 既不能放在 `<ruleset-ref>` 里，
也不能放在顶层；只能在 `<ruleset-ref>` 里 `exclude` 掉，再用带显式 `class` 的
`<rule>` 重新加回来。而且 `<rule>` 不接受 `name` 属性。）

### 15.3 为了对齐，formatter 需要补的能力：空行控制

`formatting.xml` 里有三条规则需要 formatter 能够**增删换行**：

| CodeNarc 规则 | formatter 需要的旋钮 |
| --- | --- |
| `ConsecutiveBlankLines` | `maxBlankLines` |
| `ClassStartsWithBlankLine` | `blankLinesAfterClassOpen` |
| `ClassEndsWithBlankLine` | `blankLinesBeforeClassClose` |

而 §10.1 建立的地基是"**绝不增删 token**"。所以必须显式地开一个受控例外，
并重新论证安全性：

- 在 Groovy 里换行是**真实的 token**（NL 类型），所以"加一个空行"就是"插一个 NL token"。
- 空行只改变 NL token 的**个数**，而语法规则是 `nls : NL+` ——
  一个还是三个 NL，解析结果完全相同。
- ⇒ 输出闸门从"token 序列完全一致"放宽为"**规范化 token 序列一致**"
  （把连续换行折叠成一个之后比对）。这个形式仍然保证：所有非换行 token 逐一相同、
  每处换行的**换行风格**（`\n` / `\r\n`）相同、换行处的**个数**相同，
  唯一允许的差异就是空行数量。
- 语法树比对相应改为"**忽略空行之后**再比"。
- 提供 `--no-blank-lines` 退回原行为。

实现上有两个值得记的坑：

1. **匿名内部类也算"类"**：CodeNarc 的 `ClassStartsWithBlankLine` / `ClassEndsWithBlankLine`
   对 `new SpacingRule() { ... }` 同样生效。所以类体识别必须把 `new` 也认进去。
2. **gap 下标差一位**：判断"两个换行 token 是否连续"时，
   要读它们**之间**的 gap（`gaps[e+1]`），而不是下一行缩进的 gap（`gaps[e+2]`）。
   写错的表现是空行**累积**（每次格式化多一个空行），
   而"期望输出必须是幂等不动点"这条 fixture 不变式立刻把它抓了出来 ——
   这是 §14.3 那条不变式的第二次救命。

### 15.4 结果

`formatting.xml` **一条都不排除**（`SpaceAroundMapEntryColon` 配置对齐），此时：

```
codenarcMain   TotalFiles=40 FilesWithViolations=0 P1=0 P2=0 P3=0
codenarcTest   TotalFiles=2  FilesWithViolations=0 P1=0 P2=0 P3=0
```

包括 `LineLength`、`Indentation`、`ClassJavadoc`、`TrailingWhitespace`、
`SpaceAroundOperator`、`FileEndsWithoutNewline`、`ConsecutiveBlankLines` 在内的规则全部自然通过 ——
**因为 formatter 已经和它们一致了。** 这是"formatter 当规范、linter 当门禁"这个模型跑通的直接证据。

顺带修掉的真实缺陷：
- CLI 不给任何选项时**什么都不输出**（gofmt 是默认打到 stdout）
- `LineLength` 抓到一处 121 字符的行
- `CyclomaticComplexity` 抓到 `isClassBodyOpen` CC=21 → 拆成两个方法

---

## 16. Spotless 接入：一个非显然的类加载器障碍

接入 Spotless 本身很直接（`custom` 步骤 + `spotlessCheck`/`spotlessApply`），
但**第一次运行必然失败**，原因值得单独记一笔。

### 16.1 ❌ 直接调用会 VerifyError

```groovy
spotless { groovy { custom 'groovyfmt', { src -> io.github.heyuch.groovyfmt.pipeline.Formatter... } } }
```

```
java.lang.VerifyError: Bad type on operand stack
  Type 'org/apache/groovy/parser/antlr4/GroovyLangParser' (current frame, stack[1])
       is not assignable to 'groovyjarjarantlr4/v4/runtime/Parser'
```

**根因**：Groovy 3 和 Groovy 4 都把 ANTLR4 **shade 成同一个包名**
`groovyjarjarantlr4.v4.runtime`，但两个版本的类层次不同。
Gradle 8 守护进程内置 Groovy 3.0.24，而 buildscript 类加载器是**父优先**的，
于是 `Parser` 被解析到 Groovy 3 的同名类 —— 我们的 `GroovyLangParser`（来自 Groovy 4）
自然不是它的子类。

这是**同名不同版本**的经典冲突，而且**无法通过调整 classpath 顺序解决**：
把 Groovy 4 排到前面，Gradle 自己的 Groovy 3 代码就会坏。

### 16.2 ✅ 解法：parent = platform 的隔离类加载器

```groovy
def urls = (formatterJars + formatterClasses)*.toURI()*.toURL() as URL[]
def loader = new URLClassLoader(urls, ClassLoader.platformClassLoader)   // ← 关键
def formatter = loader.loadClass('io.github.heyuch.groovyfmt.pipeline.Formatter')
                    .getMethod('defaults').invoke(null)
```

因为 `parent` 直接跳到 platform，Groovy 4 的 `groovyjarjarantlr4.*` 不会再被
Groovy 3 的祖父类加载器截走。**实测可行**，且输出与命令行完全一致。

顺带解决了另一个问题：**构建脚本不需要依赖本工程的产物**。
构建脚本里只有字符串类名和反射，所以不存在
"Spotless 步骤要加载本工程编译产物、而步骤本身又由本工程编译"的先有鸡还是先有蛋问题。

### 16.3 import 处理：这是第一类"移动 token"的规则，和现有不变式的关系

`importOrder` 与前面所有规则有一个**本质区别**：
空白规则只改 gap，空行规则只改 NL 的个数，而 import 排序要**移动整行** ——
token 的**顺序**变了。

这意味着 §10.1 那条"token 序列不变"的安全论证**直接失效**。
如果要在 groovyfmt 内部实现 import 排序，需要一条新的不变式。可行的一条是：

> **把 import 语句整体重排，然后校验"所有 import 语句文本的多重集完全相同"。**

也就是说：只做排列，不新增/删除/修改任何 import。这条是可以自动校验的，
而且比"序列相同"更适合排序类变换。真正难的部分不是排序算法，而是
**注释归属** —— import 上方的注释必须跟着一起走，否则注释会错位到别的 import 上。

**但本项目推荐的答案是：不要在 groovyfmt 里实现它。** Spotless 的 `importOrder`
已经处理好了注释归属和分组，而 Spotless 的步骤是**可组合**的：

```groovy
spotless {
    groovy {
        importOrder 'java', 'javax', 'groovy', 'com', ''   // 先重排行
        custom 'groovyfmt', fmtFormat                       // 再统一间距
    }
}
```

**顺序不能反过来**：如果先 groovyfmt 再 importOrder，最后一步的输出就不受格式化器约束了，`spotlessCheck` 的幂等性也就无从保证。

⇒ 这里给出了一个更一般的判断标准：
**"只重排、不重写"的规则交给 Spotless 的现成步骤，"改空白/空行"的规则交给 groovyfmt。**
两者以固定的顺序组合，各管一段，避免在同一维度上有两个权威。

### 16.4 另一个必须知道的限制

Spotless **无法缓存自定义步骤**（`FormatterFunc` 不可序列化），
所以每次都会重跑。改了步骤行为后要用 `bumpThisNumberIfACustomStepChanges(n)` 手动失效。

另外：被安全闸门拒绝的文件应当**抛错**而不是静默返回原文 ——
静默会让 `spotlessCheck` 报"干净"，而这正是 §14.2 那个"fail-closed 让 golden 静默失效"
的同一个陷阱，只是换到了 Spotless 场景。

---

## 17. 拆成多 module：三条只有拆完才暴露的约束

结构：

| module | 内容 | 依赖 |
| --- | --- | --- |
| `groovyfmt-core` | formatter 本体（cst/model/annotate/rule/layout/pipeline/cli）+ 47 个 E2E 语料 | 只有 Groovy 4 |
| `groovyfmt-spotless` | Spotless 适配器（一个 `FormatterFunc`） | 只有 Spotless 接口（编译期） |
| `groovyfmt-gradle-plugin` | Gradle 插件：把步骤自动加进 Spotless 的 `groovy` 扩展 | Spotless 插件（编译期） |
| `groovyfmt-codenarc` | CodeNarc 自定义规则：格式化规则不得被豁免（§18） | CodeNarc（编译期） |

拆分本身很直接，但过程中撞出三条约束，都是"不拆就发现不了"的类型。

### 17.1 两类 module 必须用**不同**的 Groovy 版本编译

| module | 编译用 | 理由 |
| --- | --- | --- |
| `groovyfmt-core` | **Groovy 4** | 需要 `org.apache.groovy.parser.antlr4`（Groovy 3 没有） |
| `groovyfmt-spotless` | **Gradle 自带的 Groovy** | 它要在 **Gradle 守护进程**里被加载 |

第二条是第一眼想不到的：适配器的类由 **守护进程的类加载器**加载，
如果它的字节码是按 Groovy 4 生成的（引用了 Groovy 4 的运行时方法签名），
那么在只装了 Groovy 3 的守护进程里会重演 §16.1 的 VerifyError —— 只不过这次炸的是适配器自己。

⇒ 原则：**要在宿主里被加载的代码，必须用宿主的语言版本编译。**
所以根工程的 `subprojects{}` **不再统一覆盖 `groovyClasspath`**，
而是让每个 module 自己声明——这本身就是一种架构声明。

### 17.2 Spotless 的自定义步骤必须是**可序列化的类**，不能是闭包

用闭包写 `custom 'groovyfmt', { ... }` 会失败：

```
Cannot fingerprint input property 'stepsInternalEquality':
  value 'ConfigurationCacheHackList[...]' cannot be serialized
```

因为 Spotless 要对步骤列表做序列化 fingerprint，而 Groovy 闭包会把
**整个构建脚本 / binding** 一起捕获进去。

修法：在构建脚本里声明一个只持有 `List<String>` 的类：

```groovy
class IsolatedGroovyFmt implements com.diffplug.spotless.FormatterFunc, Serializable {
    private final List<String> classpath          // 唯一参与序列化的状态
    private transient URLClassLoader loader       // 隔离世界的东西全部 transient
    private transient Object formatter
    ...
}
```

这也是 §16.3 里"自定义步骤无法被缓存、需要 `bumpThisNumberIfACustomStepChanges`"
那条限制的具体成因：能参与哈希的状态只有你显式声明的那几个字段。

> 注：本节修正了 §16.2 里的示例 —— 那里用的是构建脚本里的闭包，
> 单 module 时侥幸能跑，拆成多 module 后必然失败。

### 17.3 适配器的测试**不能**把 core 放进自己的 classpath

这条最反直觉。适配器测试需要 core 的类，但**不能**通过 `testImplementation` 依赖 core ——
因为 core 会带来 Groovy 4，而适配器自己的运行期是 Groovy 3（`localGroovy()`），
两个版本在**测试 JVM** 里又撞一次。

正确做法是让 core 只以**文件路径**出现：

```groovy
tasks.named('runTests', JavaExec) {
    systemProperty 'groovyfmt.coreClasspath',
            project(':groovyfmt-core').sourceSets.main.runtimeClasspath.asPath
}
```

代码里读这个属性拿到隔离类加载器要的 classpath。

**而这个"别扭"的设计恰好就是消费者真实的用法**：
适配器不知道 core 在哪，宿主告诉它。所以测试反而比"直接依赖"更贴近真实场景。

⇒ 结论：适配器与 core 的正确关系是**编译期零依赖、运行期由宿主注入 classpath**，
不是 `implementation project(':groovyfmt-core')`。

### 17.4 关于 `subprojects{}` 的取舍

根工程用 `subprojects{}` 下发公共配置（java 版本、编码、CodeNarc）。
现代 Gradle 更推荐 convention plugin（`buildSrc` / `includeBuild`），
但对两个 module 的小工程，`subprojects{}` 消除的重复（CodeNarc 三条阈值 + 规则集路径 +
编码设置）大于它的代价（跨工程配置、阻碍 configuration-on-demand）。

**规模变大时再换。** 判据是"模块间开始需要**不同**的公共配置子集"。

§18 落地后，这个判据已经**部分触发**：现在是 4 个 module，而且 `groovyfmt-codenarc`
需要一份专属的 codenarc classpath 配置（见 §18.3）。但到本文为止只有**一处**例外，
`subprojects{}` 仍然比 convention plugin 划算 —— **下次再出现第二处例外时就应该换掉**。

---

## 18. 把约定做进门禁：CodeNarc 自定义规则

### 18.1 约定

> **涉及代码格式的 CodeNarc 规则不得被 `@SuppressWarnings` 豁免，也不得从规则集里排除。**

理由就是 §15 的"规范 / 门禁"模型：门禁报警说明**规范不完整**，正确的反应是实现那个
格式化特性，而不是把门禁关掉。一旦允许豁免，门禁就退化成"只要没人抱怨就通过"，
格式会静默漂移。

先确认 CodeNarc 有没有现成的：**没有**。411 个规则类的字节码里引用 `SuppressWarnings`
的为 **0**；官方规则索引（406 条）里也没有任何一条与之相关。Checkstyle 有，CodeNarc 没有
—— 它把 `@SuppressWarnings` 当**抑制机制**，而不是被检查对象。

### 18.2 为什么是自定义规则，而不是一个测试

第一版写成了测试（扫描源码里的 `@SuppressWarnings`，比对格式化规则清单）。但那是
**两个权威**：同一件事既由测试管、又该由门禁管。更自然的位置是门禁自身 ——
它本来就在遍历代码，报违规时天然带文件与行号，将来对采用我们规则集的消费者也生效。
所以测试被删除，只保留 `groovyfmt-codenarc` 里的规则。

### 18.3 实现要点（每一条都是踩出来的）

| 坑 | 事实 |
| --- | --- |
| 注解全限定名 | `@SuppressWarnings` 是 **`java.lang.SuppressWarnings`**。写成 `groovy.lang.*` 会让规则**静默地从不触发** —— 是靠"验证守卫真的会失败"才发现的 |
| 拦截点 | `visitAnnotations(AnnotatedNode)` 在 CodeNarc 的遍历里**不被调用**；只能用 `visitClassEx` 显式遍历成员 |
| `getSourceUnit()` | 在访问器里调用会抛 `should never be called`；改用 `visitImports(ModuleNode)` 拿模块信息 |
| 覆盖范围 | 对齐 CodeNarc 自己的 `SuppressionAnalyzer`（反编译看它引用哪些 AST 类型）：class / field / method / constructor / package / import。**文件级不存在**（`ModuleNode` 不是 `AnnotatedNode`） |
| 规则集 schema | `<rule>` 必须排在**所有 `<ruleset-ref>` 之后** |
| `ruleset-ref` 解析 | 按 **classpath** 解析，不按文件系统 |
| Gradle codenarc 插件 | 它用 `defaultDependencies` 添加工具，而那只在配置**没有任何显式依赖**时生效 —— 只加我们的规则会把 CodeNarc 自己挤掉（`ClassNotFoundException: CodeNarcTask`），必须显式补上工具坐标 |
| 规则模块自身 | 不能 `project(':groovyfmt-codenarc')`（成环），改用 `files(sourceSets.main.output)` + 显式 `dependsOn classes` |

### 18.4 顺带完成的重命名，以及它暴露的 bug

坐标经历了两步：先把 Maven group 与 Java package 一起从 `dev.groovyfmt` 改为
`io.github.heyuch.groovyfmt`，随后发现 **group 与 artifactId 重复**（`io.github.heyuch.groovyfmt:groovyfmt-core`），
又把 group 收敛为 **`io.github.heyuch`**（变成 `io.github.heyuch:groovyfmt-core`）。
Java package 保持 `io.github.heyuch.groovyfmt.*` —— 它不存在同样的重复问题。

为此 group 被提取成 `gradle.properties` 里的 **`projectGroup`**，由它派生发布坐标、
依赖坐标与本地仓库路径三样东西。原因就是下面这个 bug。

这次重命名暴露出一个**只有重命名才会暴露**的 bug：

> 自举侦测用的 m2 路径仍是**旧 group**，而解析用的坐标已是**新 group**。
> 旧 group 的 artifact 恰好还躺在 `~/.m2` 里 —— 于是侦测认为"已发布"，走了 pinned 分支，
> 却去解析一个不存在的坐标，在**配置期**就失败了。

**教训**：同一个事实（group）在"侦测"和"使用"两处各写了一遍，而残留的旧 artifact
**掩盖**了不一致 —— 如果没有旧 artifact，侦测会正确落到引导模式，反而不会出错。
凡是"路径 = 坐标的另一种写法"的地方，都应该从同一个来源派生。

### 18.5 插件的 id 与坐标是两件事

改 group 时一并考虑过插件 id，**结论是不改**（保持 `io.github.heyuch.groovyfmt`）：

- 它符合 Gradle 惯例：`命名空间 + 项目名`。Spotless 自身就是这个套路 ——
  它的 group 与插件 id 都是 `com.diffplug.spotless`。
- 容易注意到插件**标记**坐标里 id 出现了两次：
  `io.github.heyuch.groovyfmt:io.github.heyuch.groovyfmt.gradle.plugin:0.1.0`。
  但这是 Gradle 标记机制**固有的**：它规定 marker 的 group = 插件 id、
  artifact = 插件 id + `.gradle.plugin`，**换任何 id 都会重复**。
  而且这个坐标是内部的 —— 用户只写 `id '...'`，永远看不到它。
- ⇒ 改 id 并不能消除任何重复，只是换一种命名风格。所以按惯例保留。

顺带一个容易踩的坑：`~/.m2/repository/io/github/heyuch/groovyfmt/` 这个目录下
**同时**存着两样东西 —— 旧的 group artifact，以及**当前的插件标记**（因为标记的 group
恒等于插件 id）。清理旧 group 时必须只删前者：

```bash
# ✗ 会把插件标记一起删掉，让所有 plugins { id 'io.github.heyuch.groovyfmt' } 的
#   消费者失效 —— 而且当场不会报错，要等别人下次构建才发现
rm -rf ~/.m2/repository/io/github/heyuch/groovyfmt

# ✓ 只删那 4 个 artifact
rm -rf ~/.m2/repository/io/github/heyuch/groovyfmt/groovyfmt-{core,spotless,gradle-plugin,codenarc}
```

## 19. 历史缺陷与仍然开放的设计权衡

### 19.1 已修的历史缺陷

两条都是**自举之后**才暴露的 —— 我们自己的源码里原本没有这些形态，直到开始写
Gradle 插件（SAM 适配 = 匿名内部类）才撞上。这说明自举不只是仪式感。

**① 作为方法调用实参的匿名内部类，类体被多缩进一层**

```groovy
f(new Runnable() {
    @Override            // ← 被格式化到 20 空格（应为 12）
    void run() { ... }
})                       // ← 16 空格（应为 8）
```

根因是 `ContinuationIndentRule` 用了**全部**未闭合括号数（`braceDepth×4 + parenDepth×8`）。
而外层那个 `f(` 的"我在实参里"已经被花括号的块缩进表达过了，再叠一层就多缩进。
修法：只统计**最内层未闭合花括号之后**打开的括号（`parensSinceBrace`）。

> 更有意思的是**它怎么被发现**：`formatApply` 的覆盖范围扩大到插件 module 之后，
> CodeNarc 立刻报 `Indentation` —— **门禁抓住了规范自己的 bug**。
> 这是 §15 那个"规范 / 门禁"模型的一个意外收益。

回归用例：`indent/anonymous-class-in-call.groovy`。

**② 具名嵌套类的类体空行：一个并不存在的豁免**

曾以为"groovyfmt 只规范化顶层类体"，于是给 `GroovyFmtStep.Identity` 加了
`@SuppressWarnings('ClassEndsWithBlankLine')`。实测（`static final class B implements ...`、
带注解的嵌套类）**本来就支持**，那个豁免防的是一个不存在的问题，已删除。

> 教训：**豁免之前先验证违规是否真的存在**。那个注解是在跑过 `formatApply` 之前
> 就写上去的 —— 也就是说，豁免的是一个"我以为会报"的违规。

回归用例：`indent/nested-class-blank-lines.groovy`。

### 19.2 仍然开放的设计权衡

这几条是刻意的取舍，不是疏漏。列在这里是为了让"将来要不要改"有据可依。

| # | 取舍 | 换来了什么 | 代价 / 若要走另一条路 |
| --- | --- | --- | --- |
| 1 | **Slot 排他 owner** | "加规则不会改变既有行为"是**注册期静态保证** | 无法用"再加一个类"覆盖已有位置。要策略插拔就得引入优先级投票链，而冲突会从注册期静态错误退化成运行期隐式行为 |
| 2 | **失败即整体回退** | 最安全 | 最保守：任何一个 gap 出问题就整文件不动。更好的是**局部回退**，需要把闸门做到 gap 粒度 |
| 3 | **顶层续行保留原缩进是硬编码兜底** | 避免被拍平到第 0 列 | 应提升成正式的 `IndentRule`；但 `IndentRule` 现在只能返回列数增量，表达不了"不表态、保留原文"，需先扩接口 |
| 4 | **`--no-verify` 只关掉语法树比对** | 词法 / 编译器 / token 序列闸门始终在 | 命名可能误导（听起来像"关掉全部校验"）。要么改名，要么让它分级 |
| 5 | **测试是零依赖自研跑器** | 构建极简，无测试框架依赖 | 接 JUnit 5 能拿到报告与 IDE 集成 |
| 6 | **方法声明参数列表用固定逻辑缩进，不支持"与开括号对齐"** | 与第 3 条同源：不需要知道开括号的列 | **刻意延后**（不是 bug）。折行的断点由格式化器决定，参数列表的缩进方式与"每行长度 + 折行"配置天然耦合，等那一步一起设计。参见 README「已知限制」 |

---

## 附 A：复现本文实测的命令

```bash
GJ=$(find ~/.gradle/caches -path "*org.apache.groovy/groovy/4.0.30*" -name "groovy-4.0.30.jar" | head -1)

# greclipse 体积
du -sh ~/.gradle/caches/p2-data
du -sh ~/.gradle/caches/p2-data/bundle-pool/*

# 语法文件
curl -sO https://raw.githubusercontent.com/apache/groovy/master/src/antlr/GroovyLexer.g4
curl -sO https://raw.githubusercontent.com/apache/groovy/master/src/antlr/GroovyParser.g4

# 无损性 / parse tree 探针源码见本文 §3，编译运行：
javac -cp "$GJ" -d . FmtProbe2.java ParseProbe.java
java  -cp "$GJ:." FmtProbe2 Sample.groovy
java  -cp "$GJ:." ParseProbe  Sample.groovy
```
