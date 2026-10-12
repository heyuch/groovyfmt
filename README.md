# groovyfmt

**用 Groovy 写的轻量级 Groovy 格式化工具** —— v0.1 骨架 + 11 条基础规则。

两条贯穿始终的设计选择：

1. **不是 AST 重打印**（ktfmt / google-java-format 路线），而是 **token 重排 + trivia 保留**（clang-format 路线）：
   > 格式化 = 为每个 gap 重新决定它的空白内容。token 本身（含注释、换行 token）绝不被改写。
2. **项目本身就是 Groovy，所以能自己格式化自己**（自举 / dogfooding），
   质量闸门统一挂在 `./gradlew check` 上。

## 快速开始

```bash
cd groovy-fmt
./build.sh                       # = ./gradlew check：编译 + CodeNarc + 自举格式检查 + 测试

./build.sh installDist
build/install/groovyfmt/bin/groovyfmt --diff  examples/Messy.groovy   # 看 diff
build/install/groovyfmt/bin/groovyfmt --stats examples/Messy.groovy   # 看覆盖率
build/install/groovyfmt/bin/groovyfmt -w      src/**/*.groovy         # 就地写回
build/install/groovyfmt/bin/groovyfmt --check src/**/*.groovy         # CI 用，有差异退出码 1
build/install/groovyfmt/bin/groovyfmt --rules                         # 列出规则与它们认领的 Slot
cat Foo.groovy | build/install/groovyfmt/bin/groovyfmt -              # stdin → stdout
build/install/groovyfmt/bin/groovyfmt --no-blank-lines Foo.groovy     # 关闭空行调整
```

## 质量闸门

`./gradlew check` 一条命令跑完整套（干净构建约 6 秒）：

| 闸门 | 任务 | 作用 |
| --- | --- | --- |
| 静态分析 | `codenarcMain` / `codenarcTest` | CodeNarc，**`formatting.xml` 一条都不排除**，`maxPriority1/2/3Violations = 0` |
| **自举格式检查** | `formatCheck` | 用刚构建出的 formatter 检查它**自己的源码**是否符合自己的规范 |
| E2E 黄金样例 | `runTests` | **47 个 fixture**：每个语法片段跑完整 formatter 比对结果 |
| API / CLI 契约 | `runTests` | 12 项：注册期冲突、指标口径、CLI 退出码 |

本地修复：

```bash
./build.sh formatApply    # 用 groovyfmt 格式化它自己的源码
```

这跟 Spotless 的工作方式一致：CI 里 check，本地 apply。

### CodeNarc：规范（spec）与门禁（gate）的关系

规则集在 [config/codenarc/codenarc.xml](config/codenarc/codenarc.xml)。核心决策是：
**CodeNarc 的格式化规则全部保留，让 groovyfmt 与它保持一致。**

| 角色 | 谁 | 性质 |
| --- | --- | --- |
| **规范 spec** | groovyfmt 的规则 + fixture | **充分条件**：把代码变成确定的形状，且能自动修复 |
| **门禁 gate** | CodeNarc 的格式化规则 | **必要条件**：独立检查形状是否真的合规 |

为什么两个都要：CodeNarc 的格式化规则大多只是**必要条件**而不是充分条件
（例如 `SpaceAroundOperator` 只要求运算符旁"有"空格，不要求"恰好一个"），
所以它当不了 formatter 的规范；但正因为它比 formatter 弱、而且是独立实现的，
**它能在 formatter 出 bug 时把不合格的输出拦下来** —— 这就是独立的第二意见的价值。

代价是每条风格决策要在两处表达。所以取舍是：**能用配置对齐的就配置，而非排除。**

典型例子：

```xml
<!-- SpaceAroundMapEntryColon 的【默认配置】要求冒号后不能有空格（[code:1]）， -->
<!-- 与 Groovy 通用惯例相反。配成与 house style 一致 —— 规则的把关作用完整保留。 -->
<ruleset-ref path='rulesets/formatting.xml'>
    <exclude name='SpaceAroundMapEntryColon'/>
</ruleset-ref>
<rule class='org.codenarc.rule.formatting.SpaceAroundMapEntryColonRule'>
    <property name='characterBeforeColonRegex' value='\S'/>
    <property name='characterAfterColonRegex' value='\s'/>
</rule>
```

**为了让 `formatting.xml` 全开也能通过，groovyfmt 补上了"空行控制"能力**
（这是它原先唯一做不到的一类）：

| CodeNarc 规则 | groovyfmt 的对应旋钮 |
| --- | --- |
| `ConsecutiveBlankLines` | `maxBlankLines` |
| `ClassStartsWithBlankLine` | `blankLinesAfterClassOpen` |
| `ClassEndsWithBlankLine` | `blankLinesBeforeClassClose` |

空行控制在 Groovy 里意味着**增删 NL token** —— 这是本工具唯一会增删 token 的环节，
因此输出闸门从"token 序列完全一致"改成了"**规范化 token 序列一致**"
（见下文"安全保证"）。`--no-blank-lines` 可以关掉这个能力，退回"绝不增删 token"的保守行为。

**当前实测：`formatting.xml` 全开时 CodeNarc 违规 = 0。**

**其余的偏离仍用 `@SuppressWarnings` 就地标注理由**，而不是把全局阈值调松。例如：

```groovy
@SuppressWarnings('CatchThrowable')   // 有意为之：必须捕获一切才能真正 fail-closed
static Cst build(String source) throws GroovySyntaxException { ... }

@SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'MethodSize'])
// 优先级顺序本身就是契约，拆散反而看不出顺序
static Slot classify(Cst cst, Annotated a, int g) { ... }
```

**首次接入 CodeNarc 的战果**：78 条违规（P1=0, P2=21, P3=57）→ **0 条**。
其中有一条是**真正的死代码**（从 Java 版一路带过来的）：

```
Violation: Rule=UnusedPrivateMethodParameter P=2
  Method parameter [prevRole] is never referenced in the method classifyToken
```

其余主要是 `FactoryMethodName`（`buildMapping` 被当成工厂方法）、`ConfusingMethodName`
（私有字段与同名方法重名）、`FieldName`（`static final` 常量没大写）、`UnnecessaryGetter/Setter`
（用了 `isEmpty()` 而不是 Groovy 的 `.empty`，用了 `setX()` 而不是属性语法）。

## 测试策略：以 E2E 黄金样例为主

详见 [src/test/resources/fixtures/README.md](src/test/resources/fixtures/README.md)。

```
fixtures/<group>/<name>.groovy               输入
fixtures/<group>/<name>.expected.groovy      期望输出（可省 → 断言"必须保持不变"）
fixtures/<group>/<name>.invalid.groovy       必须被安全闸门拒绝
```

**为什么以 E2E 为主**：formatter 的契约就是"文本 → 文本"，
而真正危险的 bug（GString 的 `$`、lexer 选型、`RollBackOne`）
全都出现在**跨层交互**上 —— 单元测试根本测不到。
所以资源应该投在"更多、更真实的语法片段"，而不是"更细的方法拆分"。
加一个用例 = 加一个文件，不用碰测试代码。

**但 golden 测试单独用有两个隐蔽的失效模式，必须堵住**：

1. **fail-closed 会让 golden 静默失效**。formatter 拒绝某个文件时 `output == input`，
   于是"输入 = 期望"的用例会**假通过** —— 你以为是"正确地没改"，其实是"根本没处理"。
   ⇒ runner 对每个 fixture 先断言 `!skipped`。
   （这条不是纸上谈兵：往 `spacing/` 里丢一个语法错误的 fixture，
   runner 会立刻报 `被安全闸门跳过（golden 测试会因此静默失效）`。）
2. **golden 记录的是"当前行为"，不是"正确行为"**，它无法区分"正确"和"一致地错"。
   ⇒ 每个 fixture 还要独立过一遍语义不变式：无损 / token 序列 / 语法树 / 幂等。
   这些判据与 golden 相互独立。

**bless 工具是必需的基础设施，但有两个刻意的约束**（见 fixtures/README）：

- 只更新**已存在**的 `.expected.groovy`；本来没有期望文件却变了 → 报错而不自动生成
  （否则一次盲目 bless 就把 bug 固化成"期望"）；
- 更新后打印警告要求**人工审查 diff**。

**少数必须留在 API 层的测试**（E2E 表达不了）：

- **注册期契约**：Slot 冲突检测发生在构造 `RuleRegistry` 时，没有文本输入输出；
- **指标口径**：覆盖率统计是否在说谎 —— golden 测试永远发现不了，
  它曾经就恒等于 100%（分母漏掉了未分类的 gap）；
- **CLI 退出码**：CI 的契约，必须以子进程端到端验证（1=有差异，0=干净，2=语法错误）。

## Spotless 接入

`check` 现在已经由 **Spotless** 驱动：

```bash
./build.sh check            # = ./gradlew check，其中包含 spotlessCheck
./build.sh formatApply      # = spotlessApply（保留旧任务名，只是换了驱动）
./build.sh spotlessGroovyApply
```

### ⚠️ 接入时必须知道的一件事：Gradle 内置 Groovy 3 vs formatter 需要 Groovy 4

不能直接写 `custom 'groovyfmt', { src -> groovyfmt 的类... }` —— 会炸：

```
java.lang.VerifyError: Bad type on operand stack
  Type 'org/apache/groovy/parser/antlr4/GroovyLangParser' is not assignable to
       'groovyjarjarantlr4/v4/runtime/Parser'
```

**根因**：Groovy 3 和 Groovy 4 都把 ANTLR4 shade 成**同一个包名**
`groovyjarjarantlr4.v4.runtime`，但类层次不同。Gradle 8 守护进程内置 Groovy 3.0.24，
而 buildscript 类加载器是**父优先**的 —— 于是 `Parser` 解析到了 Groovy 3 的同名类。

**解法**：把 formatter 放进一个 `parent = platform` 的**隔离类加载器**，只通过反射调用它。
构建脚本自身完全不引用 formatter 的类，所以也顺带解决了"构建脚本要依赖本工程产物"的
先有鸡还是先有蛋问题。见 [build.gradle](build.gradle) 末尾的 `fmtFormat`：

```groovy
def urls = configurations.formatterTool.files*.toURI()*.toURL() as URL[]
def loader = new URLClassLoader(urls, ClassLoader.platformClassLoader)   // ← 关键
def formatter = loader.loadClass('io.github.heyuch.groovyfmt.pipeline.Formatter').getMethod('defaults').invoke(null)
```

### 自举用的是【已发布】的固定版本，不是工作树里的实现

`formatterTool` 解析的是 `io.github.heyuch:groovyfmt-core:<formatterToolVersion>`，
也就是**本地仓库里已发布的那个成熟版本**。原因见"自举与版本固定"一节。

Spotless 的**步骤类**同样来自本地仓库：`io.github.heyuch:groovyfmt-spotless:<formatterToolVersion>`，
通过构建脚本顶部的 `buildscript {}` 引入。也就是说，自举时跑的是
**和外部消费者完全一样的那个适配器**，构建脚本里没有第二份实现。

> 注意构建脚本里用的是 `Class.forName(...)` 而不是直接 `new GroovyFmtStep(...)`：
> Groovy 在编译期解析整个构建脚本，静态引用一个不在 classpath 上的类会让脚本直接编译失败，
> 而 `if` 的运行时分支救不了编译期问题。引导模式下那个类确实不在 classpath 上。

### 冷启动：自动进入「引导模式」

本地仓库里还没有已发布的 formatter 时（典型场景：刚 clone），**构建会自动侦测并切到引导模式**：

```
$ ./gradlew check
groovyfmt: 本地仓库没有已发布的 formatter → 进入【引导模式】
```

引导模式的行为：**不配置 Spotless**，自举格式检查改用刚编译出的 CLI 在**独立 JVM** 里跑：

```bash
./gradlew check            # 引导模式：格式检查 = :selfCheck（CLI 子进程）
./gradlew publishFormatter # 发布到本地仓库
./gradlew check            # 之后自动切回 Spotless + 已发布的适配器
```

### 为什么引导模式不能也用 Spotless？

这是**构建阶段顺序**的硬约束，不是 Spotless 扩展性的问题：

> Spotless 的步骤对象必须在**【配置期】**就存在（`custom` 接收的是对象实例），
> 而本工程的 artifact 是在**【执行期】**才产出的。`dependsOn` 只能管执行期，救不了配置期。

所以能选的路只有两条，都会引入问题：

- 用已发布的适配器 → 冷启动时 artifact 还不存在，配置期就失败 → 连 `publishToMavenLocal` 都跑不起来（死锁）；
- 用本工程刚编译的适配器 → 配置期 classes 还不存在 → 同样死锁。

⇒ 因此引导模式**根本不走 Spotless**，而是用独立 JVM 里的 CLI。独立进程天然绕开
Groovy 3 / 4 的 ANTLR4 同名冲突，所以**不需要任何桥接类** —— 这是唯一不产生第二份实现的方案。

（代价：引导模式下没有 `importOrder`。发布并切回 Spotless 后就恢复了。）

### import 处理：用 Spotless 内置步骤，和 groovyfmt 组合

```groovy
spotless {
    groovy {
        // 顺序很重要：importOrder 只重排整行，再由 groovyfmt 统一间距。
        // 反过来（先 groovyfmt 再 importOrder）会让最后一步的输出不受格式化约束。
        importOrder 'java', 'javax', 'groovy', 'org.apache.groovy', 'com', 'dev', ''
        custom 'groovyfmt', fmtFormat
    }
}
```

我们已经用这个组合重排了自己全部 30 个文件的 import（并按分组插入空行）。

### 给其他项目用

本项目另外提供了一个可复用的步骤类
[GroovyFmtStep](src/main/groovy/io/github/heyuch/groovyfmt/spotless/GroovyFmtStep.groovy)：

```groovy
buildscript { dependencies { classpath 'io.github.heyuch:groovyfmt-spotless:0.1.0' } }
configurations { groovyfmtRuntime }
dependencies { groovyfmtRuntime 'io.github.heyuch:groovyfmt-core:0.1.0' }

spotless {
    groovy {
        importOrder 'java', 'javax', 'groovy', 'com', ''
        custom 'groovyfmt', new GroovyFmtStep(
                configurations.groovyfmtRuntime.files.collect { it.absolutePath })
    }
}
```

两个注意点：

- **自定义步骤无法被 Spotless 缓存**（`FormatterFunc` 不可序列化）。改了步骤行为之后，
  用 `bumpThisNumberIfACustomStepChanges(n)` 手动让缓存失效。
- **被拒绝的文件会抛错，而不是静默放过** —— 这是刻意的门禁语义：
  "这个文件我格式化不了"必须让人看见。

## 自举与版本固定

### 为什么要把 formatter 装到本地仓库

`publishToMavenLocal` 就是 Gradle 版的 `mvn install`（装到 `~/.m2/repository`）。

这样做是为了隔离一个风险：**正在开发的实现可能不稳定**。
安全闸门能挡住"产出非法 Groovy"，但**挡不住"产出合法但你不想要的格式"** ——
例如某条空行规则写错，整个仓库被插满空行，而且每次格式化都会再插一遍。

所以自举刻意**不加载工作树里刚编译的 classes**，而是加载已发布的固定版本：

```
groovyfmtVersion      正在构建/发布的版本
formatterToolVersion  用来格式化本工程自己的那个"已发布且成熟"的版本
```

**这两个版本号必须分开。** 如果共用一个属性，pin 就会自动指向"还没发布的版本"，
pin 的意义（解耦开发中的实现与自用实现）就消失了。

### 这套机制的实际效果（已实测）

把源码里的 `indentWidth` 从 4 改成 2（模拟一个开发中的改动），然后：

| 命令 | 用的实现 | 结果 |
| --- | --- | --- |
| `spotlessCheck` | **已发布的 0.1.0**（indent=4） | ✅ 通过 —— 开发中的改动碰不到自己的源码 |
| `runTests` | **工作树**（indent=2） | ❌ 3 个 fixture 失败 —— 新行为仍被测试拦住 |

⇒ 完整工作流：**新规则先在 fixture 测试里验证 → 通过后发布 → 再把 pin 跟上去采用。**

### 发布新版本的流程

```bash
# 1. 改 groovyfmtVersion（如 0.1.0 → 0.2.0）
# 2. 确认现有代码是干净的（此时仍用旧 pin 自举）
./gradlew check
# 3. 发布新版本
./gradlew publishFormatter
# 4. 把 formatterToolVersion 跟到同一个版本，让新实现开始参与自举
./gradlew check                     # 这一步才会用新实现格式化自己的源码
```

第 4 步是关键：**只有显式跟版本，新实现才会作用于本仓库**。
如果第 4 步产生大范围 diff，那正是 review 的时机。

### 一个必须知道的限制

`mavenLocal()` 意味着构建依赖**本机状态**，严格说来不可复现。当前的处理方式：

- **本地开发**：`-PbootstrapFormatter=true publishFormatter` 先自我发布，之后 `check` 都走 pin。
- **CI**：要么缓存 `~/.m2`（Gradle 缓存里通常已有），要么在流水线里先跑一次引导发布。
- **开源后的正解**：把 formatter 发到真实仓库（Maven Central / JitPack），
  这时 `formatterToolVersion` 就是跨机器可复现的坐标，`mavenLocal()` 也可以去掉。

## 自举：为什么这一步最值钱

让 formatter 格式化它自己的源码，抓到了**两个之前 17 个真实文件语料都没触发的真 bug**：

### bug 1：用错了 lexer

我们一开始用 `GroovyLexer` / `GroovyParser`。它们对
**「GString 紧跟方法调用」**（如 `"a $b".toString()`）会误报：

```
token recognition error at: '.toString()'
```

而这段代码用真正的 Groovy 编译器编译是**完全合法**的。

修法：改用 **`GroovyLangLexer` / `GroovyLangParser`** —— 这才是 Groovy 编译器实际使用的那一对，
带有正确的上下文消歧逻辑（`PositionAdjustingLexerATNSimulator`）。

> 注意这个 bug 的表现形式是"文件被**跳过**"而不是"代码被改坏" ——
> 这正是 fail-closed 设计的意义：**看不懂就不动**。

### bug 2：`RollBackOne` 伪 token 导致 token 区间重叠

换到 `GroovyLangLexer` 后立刻暴露出第二个问题：它为了实现「`$` 归属」的位置调整，
会插入一个 **`RollBackOne` 伪 token**，语义是"上一个 token 的结束位置回退一个字符"，
真正的内容由紧随其后的 token 覆盖：

```
GStringBegin [5713..5719] `"line $`
Identifier   [5720..5723] `line`
RollBackOne  [5724..5724] `:`        ← 伪 token，不是内容
GStringPart  [5724..5725] `:$`       ← `:` 真正属于这里
```

如果不丢弃 `RollBackOne`，`concat(token) != source`，**CST 就不再是无损的**。

**根因是测试覆盖缺口**：之前所有 GString 样例都只用了 `${}` 花括号形式，
没有一个用裸 `$name` 形式。现在补了 4 条回归测试。

> 这两条结论纸面推演永远得不到。**先做能跑的东西，再谈框架** —— 这个顺序是对的。

### 自举还顺带确认了：formatter 不会改坏自己

首轮 `formatApply` 改动了 7 个文件 66 行（主要是把"参数对齐缩进"统一成自己的
`braceDepth×4 + parensSinceBrace×8` 逻辑缩进模型），然后：

- 用被格式化过的源码重新编译 → **73/73 测试通过**
- 再跑 `formatCheck` → **完全幂等，0 改动**

## 实测结果

### 真实语料（Apache Groovy 仓库 23 个 `.groovy`，216 KB）

| 指标 | 结果 |
| --- | --- |
| 被安全闸门拒绝的文件 | **0** |
| 触发回退的文件 | **0** |
| 水平 gap 分类覆盖率 | **93.0%**（21073 / 22655） |
| 主动保留原文（fail-closed） | 7.0% |
| 实际改动 | 285 处 |
| 写回后再 `--check` | 全部通过（幂等） |

93% 的位置能规范化、剩下 7% 没看懂就不动，**没有任何文件因为看不懂而被整体拒绝**。

### 项目自身

| 指标 | 结果 |
| --- | --- |
| 主代码 | 39 个 Groovy 文件，2349 行 |
| 规则 | 14 条，**平均 20 行/条** |
| 测试 | 59 项全通过（47 E2E fixture + 12 契约/CLI） |
| CodeNarc | P1=0 P2=0 P3=0 |
| 自举格式检查 | 通过且幂等 |

## 架构

三层，各管一件事，互不越界：

```
CST 层      CstBuilder：GroovyLangLexer → 无损 CST（token + gap），四道语法闸门
              ↓
标注层      TokenAnnotator：给每个 token 打语义角色 + 嵌套信息（只算一遍）
            SlotClassifier：把每个 gap 分类到一个 Slot（分不出来就保留原文）
              ↓
规则层      SpacingRule / IndentRule —— 逻辑上的全部可扩展点
              ↓
仲裁层      LayoutResolver：垂直（缩进求和）+ 水平（按 Slot 派发给唯一 owner）
            Cst.render：纯函数，只拼 trivia
```

三个核心数据模型（**规则好不好扩展，取决于这三个模型定义得好不好**）：

- **`Gap`** —— 两个 token 之间的 trivia。实测发现 Groovy 的换行**全都是 NL token**，
  所以 gap 里只有空格/tab（外加行续接符 `\` 和 shebang 两种情况，二者一律原样保留）。
- **`Slot`** —— gap 的**语义位置**（26 个，如 `BEFORE_BINARY_OP`、`AFTER_COMMA`、`BEFORE_LBRACE`）。
  规则按 Slot 认领，**一个 Slot 只允许一个 owner**，冲突在注册期就抛异常。
- **`TokenRole`** —— token 的语义角色（只算一遍）。规则**只消费角色，绝不自己推导**。

### 为什么不用 Command 做规则层

Command 假设"命令之间正交、可顺序执行"，而格式化规则**不满足**：
折行决定缩进、缩进又反过来影响是否超宽，存在反馈循环。
所以：**Command 管"阶段"，不管"规则"**。

Command 用在对的两层：
- `FormatterStep` —— 文件级管道（如 `FinalNewlineStep`），这些步骤之间确实正交
- CLI 动作 —— `--check` / `-w` / `--diff` / stdin 是同一份逻辑的不同执行器

## 约定：格式化规则不得被 `@SuppressWarnings` 豁免

CodeNarc 里**涉及代码格式**的规则，一律不允许用 `@SuppressWarnings` 压掉，也不允许从规则集里排除。

理由就是我们的"规范 / 门禁"模型：

> **门禁报警说明规范不完整** —— 正确的反应是去实现那个格式化特性，而不是把门禁关掉。
> 一旦允许豁免，门禁就退化成"只要没人抱怨就通过"，格式会静默漂移。

这条约定**由 CodeNarc 自定义规则执行**（`NoFormattingRuleSuppression`，见 `groovyfmt-codenarc` 模块），
所以它就在门禁自身：违规会带文件:行号出现在 CodeNarc 报告里。

- 它直接读 CodeNarc 自带的 `rulesets/formatting.xml` 算出格式化规则清单（当前 37 条），
  升级 CodeNarc 后自动跟上；也可用 `formattingRules` 属性显式覆盖。
- 覆盖范围对齐 CodeNarc 自己采信抑制的位置：class / field / method / constructor / package / import
  （文件级不存在 —— `ModuleNode` 不是 `AnnotatedNode`）。
- 读不到规则集时**抛错而不是放行**（fail-closed）。

> CodeNarc 3.7.0 本身**没有**这类规则：411 个规则类的字节码里，引用 `SuppressWarnings` 的为 0。

非格式化规则（`ParameterCount`、`AbcMetric`、`CatchThrowable`、`NestedForLoop`、
`FactoryMethodName` 等）不受此限 —— 它们约束的是设计权衡，不是代码形状。

**实际效果**：这条约定已经抓到过一次真问题 —— 匿名内部类类体的空行与缩进，
见下面「已修的历史缺陷」。

## 已修的历史缺陷

- **作为方法调用实参的匿名内部类缩进**：`f(new Runnable() { ... })` 里的类体会被多缩进一层。
  根因是 `ContinuationIndentRule` 数了**全部**未闭合括号，而外层那个 `f(` 的缩进
  已经被花括号的块缩进表达过了。修法是只统计"最内层未闭合花括号**之后**"的括号。
  回归用例：`indent/anonymous-class-in-call.groovy`。
- **具名嵌套类的类体空行**：曾以为不支持并加了豁免，实测本来就支持，豁免已删除。
  回归用例：`indent/nested-class-blank-lines.groovy`。

## 如何新增一条规则

分两种情况，代价差别很大 —— 这是这套框架最需要你知道的一件事。

### 情况 A：给一个**还没有规则覆盖的位置**加规则（最典型，零风险）

例子：让索引访问也紧贴 —— `a [0]` → `a[0]`。当前 `SlotClassifier` 在 `[` 前遇到"值结尾"
会保守返回 `null`（保留原文）。四步：

```groovy
// 1) 新增一个 Slot —— model/Slot.groovy
BEFORE_LBRACK_INDEX,

// 2) 让分类器认识这个位置 —— annotate/SlotClassifier.groovy 的 LBRACK 分支
} else if (nt == GroovyLexer.LBRACK) {
    if (pt == GroovyLexer.BuiltInPrimitiveType) {
        return null                        // int[] 数组类型：不动
    }
    if (TokenAnnotator.endsValue(pt)) {
        return Slot.BEFORE_LBRACK_INDEX    // ← 原来这里返回 null
    }
}

// 3) 写规则（rule/builtin/IndexBracketSpacingRule.groovy）—— 就这么多
class IndexBracketSpacingRule extends ConstantSpacingRule {
    IndexBracketSpacingRule() {
        super('spacing.index-bracket', Spacing.NONE, Slot.BEFORE_LBRACK_INDEX)
    }
}

// 4) 注册 —— rule/RuleRegistry.groovy#defaults()
.register(new IndexBracketSpacingRule())
```

不动任何既有代码，也不可能与别的规则打架（Slot 唯一），
跑一遍 `./build.sh` 就会同时过 CodeNarc 和自举格式检查。

### 情况 B：**改变一个已被认领的位置**的行为

因为"一个 Slot 一个 owner"，你不能靠"再加一个类"来覆盖它 —— 必须**换 owner**（显式、一次一处），
或者让规则读 `FormatStyle`（如 `indentWidth`）来参数化。

> 这是刻意的取舍：**排他性换来的是"新增规则不可能改变既有行为"**。
> 代价是"策略可插拔"变成了"策略可配置"。见"设计权衡"。

## 安全保证

这个工具的核心承诺：**宁可什么都不做，也绝不改坏代码。**

### 四道语法闸门（输入和输出各跑一遍）

1. **词法 error listener** —— 任何 token recognition error 直接拒绝
2. **`UNEXPECTED_CHAR` token 检查** —— 非法字符 / 未闭合字面量
3. **ANTLR 语法树** —— `GroovyLangParser.compilationUnit()` + 语法树形状比对
4. **真正的 Groovy 编译器** —— `CompilationUnit.compile(Phases.CONVERSION)`

### 两道输出闸门

5. **规范化 token 序列必须完全一致**：把连续换行 token 折叠成一个之后比对
6. **忽略空行后的语法树形状必须一致**

任何一道不过 → **原样返回输入**，标记 `skipped`，退出码 2。绝不输出半成品。

### 为什么"token 序列不变"还不够（血泪教训）

开发中出过一个只靠 3/5/6 抓不到的 bug：把 `"demo-${x}"` 改成了 `"demo-$ { x }"`。

- token 序列**完全没变**
- ANTLR 语法树**完全没变**
- 但**真正的 Groovy 编译器直接拒绝**

根因：**ANTLR 的词法错误恢复会静默跳过无法识别的字符** ——
两份文本可以有相同的 token 序列，其中一份却是非法的。
所以"token 序列不变"只有在**两边都没有词法错误**时才等价于"语义不变"。

⇒ 正确表述：**「token 序列不变」+「两侧都无词法错误」+「两侧都能被真正的编译器接受」⇒ 语义不变。**

### 另外三个必须知道的 Groovy 细节

- **`<<` / `>>` / `>>>` 不是 token** —— 它们分别是 2 个 / 3 个 `LT` / `GT`
- **`=~` 是单个 token**（find 运算符），所以 `def r=~/(x)/` 是**非法**的，必须写 `def r = ~/(x)/`
- **GString 的 `${` 内部，`$` 与 `{` 之间的空白是语义敏感的** —— 这是"水平空白不影响解析"的唯一例外

## 已知限制（Stage 1 范围）

- **绝不改变"换行的位置"**。Groovy 的 `NL` 是语法级 token，在两个 token 之间增删换行
  就可能改变语义（`x = y` 换行后接 `(a).b()` 会被解析成 `y(a).b()`）。
  这是硬规则，也是 v0.1 安全的前提。
  **唯一的例外是"连续换行的个数"（即空行）** —— 因为 `nls : NL+`，一个还是三个换行等价。
- **不做宽度折行**。需要 parse tree + Wadler Doc IR，是 Stage 2 的事。
- **空行控制是受控的例外**：`maxBlankLines` / `blankLinesAfterClassOpen` /
  `blankLinesBeforeClassClose` 三个旋钮会增删 NL token。这是为了与 CodeNarc 的
  `ConsecutiveBlankLines` / `ClassStartsWithBlankLine` / `ClassEndsWithBlankLine` 对齐。
  安全性论证见 `Cst#canonicalTokenFingerprint`：空行只改变 NL 的**个数**，
  而 Groovy 的语法是 `nls : NL+`，一个还是三个换行解析结果完全相同。
  用 `--no-blank-lines` 可完全关闭。
- **`case` 子句体不会比 `case` 标签再深一层**。
- **`<` / `>` 只在原文两侧都有空白时才认定为比较运算**，否则保守不动
  （避免把 `List<String>` 写成 `List < String >`）。
- **续行缩进是"逻辑缩进"**：`braceDepth×4 + parensSinceBrace×8`，不保留"与开括号对齐"的写法。
  其中 `parensSinceBrace` 只数**最内层未闭合花括号之后**的括号 —— `f(new Runnable() { ... })`
  里外层那个 `f(` 已被花括号的块缩进接管，再计一次就会把匿名类体多缩进一层。
- **顶层无括号的续行**保留原缩进（安全兜底，避免被拍平到第 0 列）。
- **`assert x : 'msg'`、label、`default:` 的冒号**一律不动。

## 目录结构

```
groovy-fmt/
├── build.sh / gradlew / gradle/   统一构建入口与 wrapper
├── build.gradle                   根聚合工程：下发公共配置 + Spotless 自举 + 聚合任务
├── config/codenarc/codenarc.xml   CodeNarc 规则集（四个 module 共用，每条取舍都写明理由）
├── docs/                          设计记录（见 docs/design.md）
│
├── groovyfmt-core/                formatter 本体（不依赖任何格式化框架）
│   ├── build.gradle               用 Groovy 4 编译；application 插件产出 CLI
│   ├── src/main/groovy/io/github/heyuch/groovyfmt/
│   │   ├── cst/        CstToken, Gap, Cst, GroovySyntaxException, CstBuilder（四道闸门）
│   │   ├── model/      Spacing, Slot, TokenRole, FormatStyle
│   │   ├── annotate/   TokenAnnotator（角色 + 嵌套）, SlotClassifier（gap → Slot）
│   │   ├── rule/       SpacingRule, ConstantSpacingRule, MappedSpacingRule,
│   │   │               IndentRule, GapContext, IndentContext, RuleRegistry
│   │   │   └── builtin/    14 条内置规则（每个文件平均 20 行）
│   │   ├── layout/     LayoutResolver（仲裁者）, BlankLineNormalizer, Stats
│   │   ├── pipeline/   FormatterStep, FinalNewlineStep, FormatResult, Formatter（安全闸门）
│   │   └── cli/        Main
│   ├── src/test/groovy/io/github/heyuch/groovyfmt/test/    TestMain + FixtureRunner
│   │   ├── src/test/resources/fixtures/           47 个 E2E 语料（含 README 说明约定）
│   └── examples/Messy.groovy
│
├── groovyfmt-spotless/            Spotless 适配器（编译期与 core 完全解耦）
│   ├── build.gradle               用【Gradle 自带的 Groovy】编译 —— 它要在守护进程里被加载
│   └── src/main/groovy/io/github/heyuch/groovyfmt/spotless/GroovyFmtStep.groovy
│
├── groovyfmt-gradle-plugin/       Gradle 插件：把步骤自动加进 Spotless 的 groovy 扩展
│   └── src/main/groovy/.../gradle/    GroovyFmtPlugin, GroovyFmt
│
└── groovyfmt-codenarc/            CodeNarc 自定义规则：格式化规则不得被豁免
    └── src/main/groovy/.../codenarc/   NoFormattingRuleSuppressionRule + Visitor
```

## 文档

| 文档 | 内容 |
| --- | --- |
| [README.md](README.md) | 就是你正在看的这份：用法、质量闸门、约定、已知限制 |
| [docs/design.md](docs/design.md) | 设计记录：greclipse 体积实测、token-repositioning 架构论证、规则抽象、**11 处被证伪的假设**、以及每条结论的实测证据 |
| [fixtures/README.md](groovyfmt-core/src/test/resources/fixtures/README.md) | E2E 语料的约定：三类文件名（`.groovy` / `.expected.groovy` / `.invalid.groovy`）与不变量 |

### 为什么两类 module 的 Groovy 版本策略不同

这是拆分过程中最重要的一个决定：

| module | 用什么 Groovy 编译 | 为什么 |
| --- | --- | --- |
| `groovyfmt-core` | **Groovy 4** | 它需要 `org.apache.groovy.parser.antlr4`（Groovy 3 没有） |
| `groovyfmt-spotless` | **Gradle 自带的 Groovy** | 它要在 **Gradle 守护进程**里被加载 —— 宿主提供什么 Groovy，它就必须兼容什么 |

适配器如果把 Groovy 4 写进自己的字节码，在只装了 Groovy 3 的守护进程里就会重演同一个 VerifyError。
所以它的生产代码只用 `compileOnly localGroovy()`（运行期的 Groovy 由宿主提供），
并且**刻意不依赖 core**：它只认识一个字符串类名，通过反射在隔离类加载器里调用 core。

## 设计权衡（值得下一步讨论）

1. **Slot 排他 owner ↔ 策略可插拔**：现在换来的保证是"加规则不会改变既有行为"，
   代价是无法用"再加一个类"覆盖已有位置。真要策略插拔就得引入优先级投票链，
   冲突也会从"注册期静态错误"退化成"运行期隐式行为"。
2. **失败即整体回退 ↔ 局部回退**：现在任何一个 gap 出问题就整文件不动，最安全但最保守。
   更好的是"只回退出问题的那几个 gap"，需要把闸门做到 gap 粒度。
3. **`indentFor` 里的"顶层续行保留原缩进"是硬编码兜底**，应该提升成一条正式的 `IndentRule`
   （`IndentRule` 现在只能返回列数增量，表达不了"不表态、保留原文"，需要扩一下接口）。
4. **`--no-verify` 只关掉了语法树比对**，词法/编译器/token 序列闸门始终在，命名可能误导。
5. **测试仍是零依赖自研跑器**。接 JUnit 5 能拿到报告和 IDE 集成，但从零依赖换来的构建简洁性也是收益，
   值得权衡。

## 下一步

- [ ] Stage 2：宽度折行（parse tree → Doc IR → 罚分投票选断点）
- [ ] 空行合并（需要受控地删除冗余 NL token）
- [ ] `case` 子句体缩进
- [ ] Spotless 自定义 `FormatterStep` 包装 —— 团队把 `greclipse()` 换成 `groovyFmt()` 即可，
      CI 流程一行不用改，142 MB 的 eclipse p2 缓存立刻消失
- [ ] 把"自举格式检查"接到 pre-commit / CI
- [ ] 接 LSP `textDocument/formatting`
- [ ] 语料回归：把 Apache Groovy 的 2000+ 个 `.groovy` 纳入回归测试
