# groovyfmt

**轻量级 Groovy 代码格式化器。** 只改空白，不动 token；安全契约可被机械验证。

```groovy
// 输入                          // 输出
class Demo {                    class Demo {
def x=1                             def x = 1
def greet(String n){                def greet(String n) {
return "hi ${n}"                        return "hi ${n}"
}                                   }
}                               }
```

> **当前状态：v0.1（Stage 1），开发阶段。** 功能可用、并通过了自举与真实语料验证，
> 但尚未发布到 Maven Central，**不做折行**（见 [已知限制](#已知限制)）。
> 主要读者是我们自己，API 与配置项仍可能调整。

---

## 这是什么

### 它解决什么问题

Groovy 生态里**没有 `gofmt` / `ktfmt` 级别的格式化器**。现存的方案要么绑在 IDE 里，
要么依赖一套极重的 Eclipse 组件：

| 方案 | 代价 |
| --- | --- |
| Spotless + **greclipse**（事实标准）| 需要下载 **142 MB** Eclipse 组件（实测：Eclipse 平台 97 MB + Groovy-Eclipse 集成 33 MB，其中单个 groovy bundle 25 MB、`com.ibm.icu` 14 MB，还为 **6 个平台**各带一份 SWT）|
| `npm-groovy-lint` | lint 用 CodeNarc，但 **format 仍然是转发给 greclipse** |
| `vscode-groovy-lint` | 同上，只是包了一层 |
| IntelliJ 内置格式化 | 只能在 IDE 里用，**没法当 CI 门禁** |
| `groovy-beautify` 等小项目 | 规模小，未提供语法保真 / 语义等价的验证 |
| google-java-format / ktfmt | 不支持 Groovy |

**groovyfmt 的定位**：把这件事做成一个 7.4 MB（Groovy 4 + 我们自己）的库，
可以独立当 CLI 用，也可以作为 Spotless 的一个步骤；**并让"格式化不会改坏代码"成为可证明的性质**，
而不是靠信任。

### 与同类项目的优劣

**优势**

- **体积**：`groovyfmt-core` 的运行时依赖只有 `groovy-4.0.30.jar`（7.4 MB）+ 被 shade 的 ANTLR4 runtime（315 KB）。
  没有 Eclipse、没有 SWT、没有 Swiss 军刀式的依赖树。
- **安全契约可被机械验证**：格式化后如果 token 序列或语法树发生变化，或者输出不能被真正的
  Groovy 编译器接受，就**回退并原样返回**。这套闸门是我们自己的测试对象（见 [安全契约](#安全契约)）。
- **规则框架可扩展**：一个 Slot 只允许一个 owner，在**注册期**就检查冲突 ——
  所以"新增一条规则"不可能顺带改变已有规则的行为（见 [开发者指南](#开发者指南)）。
- **可自举**：项目用自己的格式化器格式化自己的源码，且用的是**已发布的固定版本**，
  开发中的实现碰不到仓库。

**劣势与边界（诚实版）**

- **不做宽度折行**。这是最大的能力缺口：超长行不会被拆开，`LineLength` 只能靠人。
  需要在 parse tree 上做 Doc IR，属于 Stage 2。
- **规则数量仍在早期**：11 条水平规则 + 3 条缩进规则，覆盖 26 个 Slot 位置。
- **不做 import 排序**。我们建议用 Spotless 内置的 `importOrder` 与它组合（见 [配置](#配置)）。
- **语法非法的文件不动**（fail-closed）。这是刻意的：格式化器不该猜你想写什么。
- **没有 IDE 插件**，也**没有发布到 Maven Central**（目前只发本地 / 需要自己构建）。
- **不是** Google / JetBrains 那种成熟度与打磨程度。

---

## 快速开始

需要 JDK 17。

```bash
git clone <repo> && cd groovy-fmt
./build.sh                       # 编译 + 完整质量闸门（会跑格式检查与全部测试）

# 生成可执行 CLI
./gradlew :groovyfmt-core:installDist
groovyfmt-core/build/install/groovyfmt/bin/groovyfmt --help

# 试一下
groovyfmt-core/build/install/groovyfmt/bin/groovyfmt examples/Messy.groovy   # 打到 stdout
groovyfmt-core/build/install/groovyfmt/bin/groovyfmt --diff examples/Messy.groovy
groovyfmt-core/build/install/groovyfmt/bin/groovyfmt -w examples/Messy.groovy # 就地写回
```

---

## 接入方式

### 方式 1：Gradle + Spotless 插件（推荐）

插件会自动把 groovyfmt 作为 Spotless 的一个步骤加进去，**不需要写任何 `custom` 或 classpath 样板**：

```groovy
// settings.gradle
pluginManagement {
    repositories {
        mavenLocal()          // groovyfmt 目前只发本地仓库
        gradlePluginPortal()
    }
}

// build.gradle
plugins {
    id 'com.diffplug.spotless' version '8.10.2'
    id 'io.github.heyuch.groovyfmt' version '0.1.0'
}

repositories {
    mavenLocal()              // groovyfmt-core 也要能从某个仓库解析到
    mavenCentral()
}

spotless {
    groovy {
        importOrder 'java', 'javax', 'groovy', 'com', ''   // 建议配，见「配置」
        target 'src/**/*.groovy'
    }
}
```

然后就是标准的 Spotless 工作流：

```bash
./gradlew spotlessGroovyCheck   # 检查
./gradlew spotlessGroovyApply   # 修正
```

> 消费者需要有能解析到 `io.github.heyuch:groovyfmt-core` 的仓库。
> 等发布到 Maven Central 之后，上例中的 `mavenLocal()` 都可以去掉。

### 方式 2：Spotless `custom` 步骤（不想引入插件时）

```groovy
buildscript {
    repositories { mavenLocal(); mavenCentral() }
    dependencies { classpath 'io.github.heyuch:groovyfmt-spotless:0.1.0' }
}
plugins { id 'com.diffplug.spotless' version '8.10.2' }

repositories { mavenLocal(); mavenCentral() }
configurations { groovyfmtRuntime }
dependencies { groovyfmtRuntime 'io.github.heyuch:groovyfmt-core:0.1.0' }

spotless {
    groovy {
        target 'src/**/*.groovy'
        custom 'groovyfmt', new io.github.heyuch.groovyfmt.spotless.GroovyFmtStep(
                configurations.groovyfmtRuntime.files*.absolutePath)
    }
}
```

**两个必须知道的点**（详细原因见 [design.md §16](docs/design.md)）：

- **步骤类必须在构建脚本的 classpath 上**，所以方式 2 需要 `buildscript {}`。
  Spotless 的 Gradle 插件只提供 `custom(name, Closure|FormatterFunc)` 这一种第三方步骤入口。
- **不要自己 `new GroovyFmtStep(...)` 去调用隔离加载器** —— 它内部已经处理了
  "Gradle 守护进程的 Groovy 3 vs formatter 需要的 Groovy 4"这个冲突。
  直接传 classpath 即可。

### 方式 3：独立 CLI

```bash
groovyfmt [选项] [文件...]
groovyfmt - < In.groovy > Out.groovy      # stdin → stdout
```

退出码：`0` 正常 / `1` `--check` 发现有文件需要改动 / `2` 语法错误。

---

## 配置

### CLI 选项

| 选项 | 说明 |
| --- | --- |
| `-w`, `--write` | 就地写回（默认打到 stdout） |
| `--check` | 只检查，有文件需要改动则退出码 1 |
| `--diff` | 打印差异，不写回 |
| `--stats` | 打印覆盖率统计（多少 gap 被规范化、多少保留原文） |
| `--indent N` | 块缩进宽度，默认 `4` |
| `--continuation N` | 续行缩进宽度，默认 `8` |
| `--no-verify` | 关闭语法树二次校验（默认开启，**不建议**） |
| `--no-blank-lines` | 关闭空行调整，回到"绝不增删 token"的最保守行为 |
| `--rules` | 列出已注册的规则 |
| `--slots` | 列出还没被任何规则覆盖的 Slot |

### 编程接口（`FormatStyle`）

CLI 只暴露了其中一部分；作为库使用时可以全部设置：

| 字段 | 默认 | CLI 对应 |
| --- | --- | --- |
| `indentWidth` | `4` | `--indent` |
| `continuationIndent` | `8` | `--continuation` |
| `maxBlankLines` | `1` | — |
| `blankLinesAfterClassOpen` | `1` | — |
| `blankLinesBeforeClassClose` | `1` | — |
| `verifyParseTree` | `true` | `--no-verify` 可关 |

> 三个空行相关的旋钮目前**没有 CLI 参数**（`--no-blank-lines` 只能整体关闭）。
> 这是已知的接口缺口，见 [Roadmap](#roadmap)。

### import 排序怎么配

groovyfmt **不做 import 排序**（理由见 [design.md §16.3](docs/design.md)：
排序要移动整行，需要另一条安全论证；而且注释归属很容易做错）。
推荐用 Spotless 内置的 `importOrder` 与它组合：

```groovy
spotless {
    groovy {
        importOrder 'java', 'javax', 'groovy', 'org.apache.groovy', 'com', ''
        // groovyfmt 由插件自动添加
    }
}
```

两步作用于**不相交的区域**（整行顺序 vs 行内空白），所以顺序可交换；
但按上面的写法（`importOrder` 在前）是最直观的。

---

## 安全契约

**这是这个项目最值得看的部分。** 一个格式化器最大的风险不是"格式化得不好看"，
而是"悄悄改坏了代码" —— 而且 Groovy 的风险比 Java 更高：**删掉一个需要的 import，
在动态模式下不会编译报错，要等到运行时才炸**。

groovyfmt 的契约是：

> 格式化后的结果，只有在**同时通过下面全部闸门**时才会被采用；
> 否则**回退并原样返回**，而且**让你知道**。

### 输入侧：四道语法闸门

| 闸门 | 拦什么 |
| --- | --- |
| 1. 词法错误监听器 | `token recognition error`（ANTLR 的错误恢复会**静默跳过字符**，不装监听器就看不见） |
| 2. ANTLR parse tree | 语法结构非法 |
| 3. 真正的 Groovy 编译器（`CompilationUnit` @ `Phases.CONVERSION`） | ANTLR 接受但 Groovy 不接受的情况（例如 GString 里的 `$ {`） |
| 4. 无损性断言 | 所有 token 拼回去必须**逐字节等于**原文 |

### 输出侧：两道闸门

| 闸门 | 保证 |
| --- | --- |
| 5. 规范化 token 序列指纹 | 只允许空白变化（空行是受控例外，见下） |
| 6. 语法树形状比对 | 忽略空行后的 parse tree 必须一致 |

### fail-closed：看不懂就不动

**拒绝格式化时抛错，绝不静默返回原文。** 静默会让 `--check` 或 `spotlessCheck`
对一个根本没被格式化的文件报"干净" —— 那是最坏的结果。

### 实测数据

| 场景 | 结果 |
| --- | --- |
| Apache Groovy 仓库真实语料（23 个文件 / 216 KB） | **0 个文件被回退**；水平 gap 分类覆盖率 **93.0%**（21073 / 22655），其余 7% 主动保留原文 |
| 项目自身（自举） | 格式化自己的全部源码，未触发任何闸门，且是幂等不动点 |

也就是说：**93% 的位置能规范化，剩下 7% 没看懂就不动，没有任何文件因为看不懂而被整体拒绝。**

---

## 已知限制

- **绝不改变"换行的位置"**。Groovy 的 `NL` 是语法级 token：`x = y` 换行后接 `(a).b()`
  会被解析成 `y(a).b()`。这是硬规则，也是 v0.1 安全的前提。
  **唯一例外是"连续换行的个数"（空行）**，因为语法是 `nls : NL+`，一个还是三个换行等价。
- **不做宽度折行**（Stage 2）。
- **空行控制是受控的例外**：`maxBlankLines` / `blankLinesAfterClassOpen` /
  `blankLinesBeforeClassClose` 会增删 NL token，目的是与 CodeNarc 的
  `ConsecutiveBlankLines` / `ClassStartsWithBlankLine` / `ClassEndsWithBlankLine` 对齐。
  用 `--no-blank-lines` 可完全关闭。
- **`case` 子句体不会比 `case` 标签再深一层。**
- **`<` / `>` 只在原文两侧都有空白时才认定为比较运算**，否则保守不动
  （避免把 `List<String>` 写成 `List < String >`）。
- **续行缩进是"逻辑缩进"**（`braceDepth×4 + parensSinceBrace×8`），不保留"与开括号对齐"的写法。
- **顶层无括号的续行**保留原缩进（安全兜底）。
- **`assert x : 'msg'`、label、`default:` 的冒号**一律不动。

---

## 开发者指南

### 环境与常用命令

需要 JDK 17。

```bash
./build.sh                      # 编译 + 完整闸门（= ./gradlew check）
./build.sh runTests             # 只跑测试
./build.sh formatApply          # 用 groovyfmt 格式化本仓库源码
./build.sh :groovyfmt-core:run --args="--stats examples/Messy.groovy"
```

`check` 由三件事组成，**任何一件失败都会挡住构建**：

1. `runTests` —— 47 个 E2E fixture + API 契约 + CLI 退出码契约
2. `codenarcMain` / `codenarcTest` —— 零容忍（P1/P2/P3 全为 0）
3. `spotlessCheck` —— 自举格式检查（用**已发布**的版本，见下）

### 自举与版本固定

项目用自己的格式化器格式化自己，但**刻意不加载工作树里刚编译的产物**，而是加载
本地仓库里**已发布的固定版本**：

```properties
groovyfmtVersion=0.1.0        # 正在构建/发布的版本
formatterToolVersion=0.1.0    # 用来格式化本仓库自己的那个"已发布且成熟"的版本
```

两个版本号**必须分开**：如果共用一个属性，pin 就会自动指向"还没发布的版本"，
pin 的意义（把开发中的实现与自用实现解耦）就消失了。

**发布新版本的流程**：

```bash
# 1. 改 groovyfmtVersion（如 0.1.0 → 0.2.0）
./gradlew check                # 2. 确认现有代码是干净的（此时仍用旧 pin 自举）
./gradlew publishFormatter     # 3. 发布到本地仓库（= mvn install）
# 4. 把 formatterToolVersion 跟到同一个版本，让新实现开始参与自举
./gradlew check                # 这一步才会用新实现格式化自己的源码
```

**冷启动**（刚 clone，本地仓库还没有任何发布版本）：构建会**自动侦测**并切到引导模式 ——
自举格式检查改用刚编译出的 CLI 在独立 JVM 里跑。日志里会明确打印：

```
groovyfmt: 本地仓库没有已发布的 formatter → 引导模式
```

### 如何新增一条规则

规则框架的设计目标是：**"新增规则"不可能顺带改变已有规则的行为。**
一个 `Slot`（语义位置）只允许一个 owner，冲突在**注册期**就报错。

**情况 A：给一个还没有规则覆盖的位置加规则**（最典型，零风险）

1. 在 `model/Slot.groovy` 加一个 Slot
2. 在 `annotate/SlotClassifier.groovy` 加一个分类分支
3. 在 `rule/builtin/` 加一个规则类（多数是 `ConstantSpacingRule` 或 `MappedSpacingRule` 的子类，
   平均 20 行）
4. 在 `rule/RuleRegistry.groovy` 注册
5. 在 `src/test/resources/fixtures/` 加 E2E 语料（见 [fixtures/README.md](groovyfmt-core/src/test/resources/fixtures/README.md)）

用 `--slots` 可以列出**还没被覆盖**的位置 —— 那就是待办清单。

**情况 B：改变一个已被认领的位置的行为**

不能靠"再加一条规则"覆盖（注册期会报冲突）。只能**换 owner**，或让原规则读 `FormatStyle`。
这是刻意的权衡，详见 [design.md §10](docs/design.md)。

### 目录结构

```
groovy-fmt/
├── build.gradle                  根聚合工程：公共配置 + Spotless 自举 + 聚合任务
├── config/codenarc/codenarc.xml  CodeNarc 规则集（四个 module 共用）
├── docs/                         设计记录（见 docs/design.md）
│
├── groovyfmt-core/               formatter 本体（不依赖任何格式化框架）
│   ├── cst/        CstToken, Gap, Cst, CstBuilder（四道语法闸门）
│   ├── model/      Spacing, Slot, TokenRole, FormatStyle
│   ├── annotate/   TokenAnnotator（角色 + 嵌套）, SlotClassifier（gap → Slot）
│   ├── rule/       SpacingRule / IndentRule 接口 + RuleRegistry
│   │   └── builtin/    14 条内置规则
│   ├── layout/     LayoutResolver（仲裁者）, BlankLineNormalizer, Stats
│   ├── pipeline/   Formatter, FormatterStep, FormatResult（输出闸门）
│   ├── cli/        Main
│   └── src/test/resources/fixtures/    47 个 E2E 语料
│
├── groovyfmt-spotless/           Spotless 步骤（编译期与 core 解耦）
├── groovyfmt-gradle-plugin/      Gradle 插件（自动注册步骤）
└── groovyfmt-codenarc/           CodeNarc 自定义规则：格式化规则不得被豁免
```

四个 module 的**编译用 Groovy 版本不同**：`groovyfmt-core` 用 Groovy 4
（需要 `org.apache.groovy.parser.antlr4`），其余用 **Gradle 自带的 Groovy**
（它们要在守护进程里被加载 —— 宿主提供什么 Groovy，它就必须兼容什么）。

### 用 AI coding agent 参与开发

这个项目的相当一部分代码是在 AI agent 参与下写的，也因此积累了一些**具体的**经验。
如果你要让 agent 参与，建议把这些约束显式告诉它：

#### 必须先读的东西

- [docs/design.md](docs/design.md) 的 **§10 / §15 / §16 / §17 / §18** —— 这几章记录的是
  **"为什么不能那样做"**。只看最终代码看不出这些约束，而它们才是真正的设计依据。
  文档里还明确记录了**被证伪的结论**（§11、§15），不要重新踩一遍。

#### 不可违反的硬约束

1. **不得用 `@SuppressWarnings` 豁免任何格式化规则。** 有 CodeNarc 自定义规则
   （`NoFormattingRuleSuppression`）把关，违规会直接让构建失败。
   门禁报警说明**规范不完整** —— 正确的反应是实现格式化特性，不是关掉门禁。
   非格式化规则（`ParameterCount`、`CatchThrowable` 等）可以豁免，但要写明理由。
2. **格式化只改 gap，不改 token。** 任何"新增/删除/移动 token"的想法都要先有新的安全论证。
3. **一个 Slot 一个 owner。** 想改已有位置的行为，只能换 owner 或读 `FormatStyle`。
4. **输出必须是幂等不动点。** fixture 的期望输出会被自动验证"再格式化一次不变"。
5. **拒绝格式化要抛错，不能静默返回原文。** 静默会让门禁报告假的"干净"。
6. **不要让格式化器改名、改字符串或做任何语义变换。**

#### 验证方法学（都是踩出来的）

- **验证你的 oracle 真的会失败。** 我们写过一条"永远通过"的 CodeNarc 规则 ——
  因为把注解全限定名写成了 `groovy.lang.SuppressWarnings`（实际是 `java.lang`）。
  一个不会失败的检查等于没有检查。
- **macOS 自带工具会有假阴性，关键结论用 Python 复核。**
  `grep -P` 在 BSD grep 上不支持（返回"0 个匹配"）；`iconv -f UTF-8 -t UTF-8`
  同进同出时也会误报"非法 UTF-8"。这两个都骗过我们。
- **改坐标 / classpath 这类代码后，检查"侦测"和"使用"是否出自同一个来源。**
  我们曾经把 m2 路径写死成旧 group，而解析用新 group —— 两者静默不一致，
  而且残留的旧 artifact 掩盖了它（见 [design.md §18.4](docs/design.md)）。
- **不要用 `head`/`tail` 截断构建输出做判断**，失败信息常常在中间。
- **长命令先落盘成脚本再执行**，避免引号地狱；并且**中断的命令要检查它留下的副作用**
  （我们被一次中断的测试留在一个源文件里的 `@SuppressWarnings` 骗过好几轮）。
- **改完必须跑 `./build.sh`**，它包含格式检查 —— 你自己的代码也要符合本项目的格式。
  顺手 `./build.sh formatApply` 就好。
- **提交信息里不要写"会破坏消费者"这类未经验证的兼容性理由。** 这个项目还没有真实用户，
  用未验证的理由去拒绝一个改动是错的。

#### 常用命令速查

```bash
./build.sh                                  # 完整闸门（提交前必跑）
./build.sh runTests                         # 测试
./build.sh formatApply                      # 格式化本仓库源码
./gradlew :groovyfmt-core:installDist        # 生成 CLI
./gradlew publishFormatter                   # 发布到本地仓库（改 group/坐标后需要）
groovyfmt-core/build/install/groovyfmt/bin/groovyfmt --help
```

---

## Roadmap

**Stage 2：宽度折行**（最大的能力缺口）

需要 parse tree + Wadler Doc IR + 罚分选断点。难点在于 Groovy 的 `NL` 是语法级 token ——
折行必须建立在 parse tree 而不是 token 流上。做完之后 `LineLength` 才能被自动修复。

**接口补齐**

- 三个空行旋钮的 CLI 参数（`maxBlankLines` / `blankLinesAfterClassOpen` / `blankLinesBeforeClassClose`）
- `case` 子句体的额外缩进
- 顶层无括号续行的缩进规则（目前是保留原文的安全兜底）

**发布与生态**

- 发布到 Maven Central（之后消费者不再需要 `mavenLocal()`）
- 把 `groovyfmt-spotless` 作为正式 `FormatterStep` 贡献给 Spotless
  （用 `JarState` + `FormatterStep.createLazy`，官方对此表示欢迎；见 [design.md §16](docs/design.md)）
- IDE 插件 / LSP `textDocument/formatting`
- 扩到更大语料的回归（Apache Groovy 全量 `.groovy`）

**质量与工程**

- 英文注释与文档（面向开源；目前文档以中文为主）
- 把规则框架的扩展点补齐（目前 `IndentRule` 无法表达"不表态，保留原文"）

---

## 文档

| 文档 | 内容 |
| --- | --- |
| **本 README** | 是什么、怎么用、怎么配、开发者指南、roadmap |
| [docs/design.md](docs/design.md) | **设计记录**：greclipse 体积实测、架构论证、规则抽象、**被证伪的结论**、以及每条结论的实测证据 |
| [docs/README.md](docs/README.md) | 文档目录的定位（README 讲"怎么用"，`docs/` 讲"为什么是这样"） |
| [fixtures/README.md](groovyfmt-core/src/test/resources/fixtures/README.md) | E2E 语料的约定 |
