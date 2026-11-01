# 项目文档

这个目录是这个 fork 的**过程资产**，不是代码注释的替代品。目的只有一个：
让「想加个功能」和「怕改坏」这两件事能同时成立。

## 从这里开始

| 我想… | 看这个 |
| --- | --- |
| **知道现在这个 App 到底能做什么** | [`requirements/01-current-baseline-2.1.0.md`](requirements/01-current-baseline-2.1.0.md) |
| 提一个需求 | [`requirements/TEMPLATE.md`](requirements/TEMPLATE.md)，复制成 `R-xxx-标题.md` |
| 知道需求为什么这么定 | [`adr/`](adr/) —— 架构决策记录，一条决策一个文件 |
| 改代码前知道会碰到哪些上游文件 | [`upstream-divergence.md`](upstream-divergence.md) |
| 发版前检查什么 | [`release-checklist.md`](release-checklist.md) |
| 改动怎么验证、CI 管什么 | [`testing.md`](testing.md) |

## 约定

1. **需求条目是唯一的"要做什么"来源。** 聊天记录、issue、口头约定都不算数，
   落成 `requirements/R-xxx-*.md` 才算。
2. **每条需求都必须有可验收的标准**（AC-1、AC-2…）。写不出可测的验收标准，
   说明需求还没想清楚，先别开工。
3. **能自动验证的验收标准，必须落成测试。** 做不到的要写明原因（比如"需要真机
   手感和视觉判断"）。
4. **决策进 ADR，不进需求文档。** 需求说"做什么"，ADR 说"为什么这么选、放弃了什么"。
5. 文档跟代码一起进 PR。需求改了行为，PR 里要同时改需求条目。

## 目录

```
docs/
  README.md                      ← 你在这里
  requirements/
    README.md                    需求条目的写法与状态机
    TEMPLATE.md                  复制这个来写新需求
    01-current-baseline-2.1.0.md 2.1.0 现状说明书（能做什么、限制在哪）
    00-baseline-2026-10-01.md    历史基线（fork 初期对上游代码的全面梳理，供追溯）
  adr/
    README.md                    ADR 是什么、什么时候写
    ADR-001-滚动位置锚点按页面与布局分桶.md
  upstream-divergence.md         相对上游改了什么、升级上游时怎么核对
  release-checklist.md           发版前的冒烟清单
  testing.md                     测试策略与验证手段
```
