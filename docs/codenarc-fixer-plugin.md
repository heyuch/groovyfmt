# CodeNarc 驱动的格式化 Gradle 插件（构想记录）

> 状态：**构想已定，实现延后**。等 groovyfmt 本身成熟（折行与风格可配置化完成）之后再动手。
> 本文记录的是**为什么这样设计**、以及动手前必须定下的几件事 —— 避免将来重新推导一遍。

## 一句话

一个 Gradle 插件：**读项目的 `codenarc.xml` → 翻译成 groovyfmt 的配置 → 经 Gradle 跑 CodeNarc 拿报告
→ 按报告里出现的规则，对相关文件定向调用 groovyfmt → 重跑 CodeNarc 验证**。

## 分工（关键：修复逻辑不重复实现）

| 关注点 | 归属 |
| --- | --- |
| **哪里错**（规范的具体门槛）| CodeNarc（经 Gradle 执行）|
| **怎么修**（空白 / 缩进 / 折行）| **groovyfmt** |
| **两者的对应关系**（配置翻译）| 插件的映射表 |

> 曾经设想过"在插件里为每条 CodeNarc 规则写一个 fixer"，**已否决**：
> 那等于把格式化器重写一遍，而且每个 fixer 都要自己重新判断"这行在什么构造里"。
> 现在这个设计把 CodeNarc 限定在"发现问题"，修复能力全部复用 groovyfmt。

## 必须定下的四件事（都来自已经踩过的坑）

### ① 要读规则的【属性】，不能只读规则名

| CodeNarc 规则 | 属性 | 例 |
| --- | --- | --- |
| `SpaceAroundMapEntryColon` | `characterBeforeColon` / `characterAfterColon` | 默认是 `key : value`，而实测项目用的是 **`key: value`** —— 这就是本项目那 831 条违规的全部来源 |
| `Indentation` | `spacesPerIndentLevel` 等 | 决定 groovyfmt 的 `indentWidth` |
| `LineLength` | `length` | 决定折行宽度 |

⇒ 插件必须解析 `codenarc.xml` 里的规则属性（含 `<rule-config>`）。只判断"规则开没开"是不够的。

### ② 绝不自己引入 CodeNarc 依赖

踩过的坑：Gradle 的 codenarc 插件通过 `defaultDependencies` 提供工具，我们当时"只加自己的规则"
就把 CodeNarc 顶掉了，结果是 `ClassNotFoundException: org.codenarc.ant.CodeNarcTask`。

⇒ 正确做法就是构想的思路：**通过 Gradle 执行**，产出 **XML 报告**，再解析报告
（报告中带规则名 —— 实测文本报告里 `Rule=Indentation` 这类信息是可直接取的）。

### ③ 定向修复，而不是整文件格式化

groovyfmt 的槽位机制**天然支持**这一点：关掉某条规则 = 那些 gap **原样保留**，
而不是"改成别的样子"。所以只需给 groovyfmt 加一个**按规则 id 的开关**。

定向的好处：
- 语义与 CodeNarc 报告**一一对应**，好解释、好验收；
- 不会顺手引入项目**没要求**的改动（整文件格式化会）；
- 修复前后能用**同一份配置**对比。

### ④ 未映射的规则必须显式报错

若某条**已启用**的格式化规则在 groovyfmt 里还没有对应实现，插件应当**明确失败并列出它**，
而不是默默留下违规。

这样做的额外收益：**映射表直接变成路线图**。当前第一条就是 `LineLength`（实测 453 处，需要折行）。

## 插件能"免费"拿到的两个强保证

因为底层是 groovyfmt 而不是文本打补丁，插件可以直接断言：

| 保证 | 来源 |
| --- | --- |
| **只改空白** | groovyfmt 的无损 CST + 输出闸门 ⇒ 可逐文件校验"去掉空白后逐字符一致" |
| **幂等** | groovyfmt 是稳定不动点 ⇒ 再跑一次应无改动 |

⇒ 每次修复后可自动三连验证：**违规数下降**、**无新增违规**、**非空白差异为 0**。

## 可行性已经手工验证过

过去几轮在真实项目上手工跑的，就是这个流程的每一步：

```
跑 CodeNarc（仅格式化规则）→ 按规则聚合 → 定位缺失能力 → 改 groovyfmt
→ 重新格式化 → 重跑 CodeNarc 对比
```

实测成效（saas-service `output` 模块，207 个文件）：

| 规则 | 起点 | 现状 |
| --- | --- | --- |
| `Indentation` | 557 | **0** |
| `MissingBlankLineBeforeAnnotatedField` | 82 | **0** |
| `BlockStarts/EndsWithBlankLine` | 34 | **0** |
| `SpaceInsideParentheses` | 4 | **0** |
| package / imports 空行 | 2 | **0** |
| `SpaceAroundMapEntryColon` | 831 | 831（**配置差异，非缺陷**）|
| `LineLength` | 467 | 453（**待折行**）|

⇒ 在项目自身配置下，剩余唯一缺口就是 `LineLength`。

## 插件侧实现步骤（将来照着做）

1. 解析 `codenarc.xml`（含 `ruleset-ref` 到 `rulesets/formatting.xml` 的展开 ——
   我们的自定义规则 `NoFormattingRuleSuppression` 已经在读这个文件，技术已验证）
2. 把规则**属性**翻译成 groovyfmt 配置（缩进宽度、行宽、冒号风格……）
3. 以 XML 报告形式执行 Gradle 的 CodeNarc 任务
4. 按"文件 + 规则"聚合违规
5. 对每个文件调用 groovyfmt，**只启用映射到的规则**
6. 重跑 CodeNarc 验证；未映射的规则 → 报错

## groovyfmt 侧的前置条件

1. **按规则 id 的开关**（启用/禁用）—— 槽位机制已支持"关掉 = 保留"
2. **规则 id ↔ CodeNarc 规则名**的映射表（独立资源文件，供插件读取）
3. **折行**（`LineLength`）—— 目前唯一的能力缺口

## 待定问题（动手前需要确认）

- **映射的粒度**：一条 CodeNarc 规则可能对应 groovyfmt 的多条规则（例如 `Indentation`
  对应块缩进 + 续行缩进 + 闭合括号对齐）。映射表按"可整体开关的规则组"来划是否合适？
- **属性超出表达能力时怎么办**：CodeNarc 有些属性 groovyfmt 表达不了（例如 `Indentation`
  的一堆开关）。是报错、还是只对齐能对齐的部分？倾向**报错**，与第 ④ 条一致。
- **插件的触发方式**：独立任务（`groovyfmtFix`）还是挂在 `codenarcMain` 之后自动跑？
  倾向前者：显式、可控、便于先看报告再决定。
- **与 Spotless 的关系**：项目若同时用 Spotless，插件与它的 `importOrder` 等步骤的作用域
  是**不相交**的（Spotless 管 import 顺序，groovyfmt 管空白），可以共存；但需要在文档里写清。
