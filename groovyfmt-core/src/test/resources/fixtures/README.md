# E2E 测试语料（fixtures）

每个 fixture 就是一个 Groovy 语法片段，跑完整 formatter 后比对结果。
**加一个测试用例 = 加一个文件，不用碰任何测试代码。**

## 约定

| 文件 | 含义 |
| --- | --- |
| `<group>/<name>.groovy` | 输入 |
| `<group>/<name>.expected.groovy` | 期望输出（**可省**） |
| `<group>/<name>.invalid.groovy` | 必须被安全闸门**拒绝** |

- 有 `.expected.groovy` → 断言 `format(输入) == 期望`
- **没有** `.expected.groovy` → 断言 `format(输入) == 输入`（"刻意不动"的用例，占一半以上）
- `.invalid.groovy` → 断言被拒绝、且原样返回输入

## 每个 fixture 都会额外过一遍的不变式

不管有没有期望文件，每个正常 fixture 都会独立地检查：

1. **必须真的被格式化了**（`!skipped`）
2. 无损：CST 能逐字节还原原文
3. token 序列完全一致
4. 语法树形状完全一致
5. 幂等：**期望输出本身**是稳定不动点

> 第 1 条是**必需**的，不是锦上添花：
> formatter 是 fail-closed 的，如果它拒绝了这个文件，`output == input`，
> 那么"输入 = 期望"的用例会**假通过** —— 你以为是"正确地没改"，其实是"根本没处理"。

## 各目录的意图

| 目录 | 意图 |
| --- | --- |
| `spacing/` | 单条水平规则的黄金样例 |
| `gstring/` | 插值。含两个真 bug 的回归：`${` 里加空格、裸 `$name` 的 `RollBackOne` 位置调整 |
| `indent/` | 缩进模型（块缩进 / 续行缩进 / 顶层续行保留） |
| `fail-closed/` | 刻意不动的地方，以及必须保真的东西（shebang / CRLF / 行续接符 / 空行） |
| `invalid/` | 必须被安全闸门拒绝的输入 |

## 更新期望

```bash
./gradlew updateFixtures      # 只更新【已存在】的 .expected.groovy
```

**两个刻意的约束**：

1. 本来没有期望文件却发生了变化 → **一律报错**，不自动生成期望文件。
   否则一次盲目的 bless 就会把 bug 固化成"期望"。
2. 更新后会打印 `⚠ 已更新 N 个 .expected.groovy —— 请审查 diff`。
   **golden 记录的是"当前行为"，不是"正确行为"** —— 批量 bless 之后必须人工过一遍 diff。
