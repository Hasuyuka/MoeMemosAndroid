# 相对上游的差异

这个仓库 fork 自 [`mudkipme/MoeMemosAndroid`](https://github.com/mudkipme/MoeMemosAndroid)
（远端名 `upstream`）。本文件记录**我们改了什么**，目的是升级上游时能逐条核对，
而不是让 `git log` 变成唯一线索。

## 现状数字（重新生成方式见下）

| 项 | 数值 |
| --- | --- |
| 我们领先上游 | **138 个提交** |
| 上游领先我们（待合入） | **21 个提交** |
| 相对上游改动的文件 | **82 个**，+6914 / −374 行 |

> 这三个数字会随时变化，**别把它当事实引用**，用下面命令现算。

```bash
git fetch upstream main
git rev-list --count upstream/main..main      # 我们领先多少
git rev-list --count main..upstream/main      # 上游领先多少
git diff --stat upstream/main...main          # 改了哪些文件
git log --oneline upstream/main..main         # 逐条看我们做了什么
```

## 升级上游的标准流程

1. `git fetch upstream main`
2. 先看 **上游领先多少**。超过 30 个提交就分几次合，别攒着。
3. `git diff upstream/main...main -- <某个模块>` 看这个模块我们改过什么。
4. 合的时候按下面的「升级风险」列优先核对。
5. 合完必跑：`./gradlew testDebugUnitTest`（CI 会跑），以及真机冒烟
   （见 [`release-checklist.md`](release-checklist.md)）。
6. 合完更新本文件的数字。

## 我们的改动（按模块）

> 「升级风险」= 上游如果也改了这个文件，合并时容易冲突或语义打架的程度。

| 模块 | 我们的改动 | 主要文件 | 升级风险 |
| --- | --- | --- | --- |
| 列表布局 | 新增大卡片/两列/三列三档，可持久化切换 | `ui/page/memos/MemosList.kt`<br>`data/model/Appearance.kt` | **高** |
| 列表滚动 | 锚点按「页面+布局」分桶；回顶只在布局真变时执行 | `MemosViewModel.kt`<br>`MemosList.kt` | **高** |
| 列表性能 | 原地更新（`planMemoListUpdate`）、卡片图片上限 9 张、`remember` key 不再整表复制 | `util/MemoListUpdate.kt`<br>`MemosViewModel.kt` | **高** |
| 列表排序 | 修改时间/创建时间/字母 × 升降；字母用 `Collator`（中文按拼音）；置顶恒优先 | `util/MemoSort.kt` | 中 |
| 多选 | 长按进入批量编辑，移除顶栏批量入口与设置里的长按手势 | `ui/util/MemoSelectionState.kt` | 中 |
| 编辑器 | 图片可调整顺序（缩略图对话框），顺序落库为附件创建时间 | `ui/page/memoinput/`<br>`util/ResourceOrder.kt` | 中 |
| 附件可靠性 | 建备忘与关联附件同事务；`updateMemo` 只写不删；`resources` 参与变更检测 | `data/local/dao/MemoDao.kt`<br>`data/repository/*.kt` | **高** |
| 搜索 | 支持 `tag:` / `is:` / `visibility:` / `after:` / `before:` 前缀查询 | `util/MemoQuery.kt` | 中 |
| 设置 | 字体缩放、图像质量与格式、主题模式、动态取色、手势配置 | `data/model/`、`SettingsSerializer` | 中 |
| 安全 | 应用锁、mTLS 客户端证书、token AES/GCM 存储、备份规则收紧 | `ui/security/`、`data/mtls/`、`res/xml/*rules.xml` | 中 |
| 小组件 | 锁屏时隐藏内容 | `widget/WidgetLockedContent.kt` | 低 |
| 本地化 | `values-zh-rCN` / `values-zh-rTW` 补全 | `res/values*/strings.xml` | 低（但新增字符串必须三处都加） |
| 测试 | 21 个 JVM 测试文件（Room/Robolectric + 纯函数） | `app/src/test/` | 低 |
| CI | 版本号由 tag 推导并断言 APK badging；分支 CI 产出 release 签名的 debug 包 | `.github/workflows/`、`app/build.gradle` | 低 |

## 升级上游时最容易踩的三件事

1. **字符串必须加三处**：`values`、`values-zh-rCN`、`values-zh-rTW`。
   上游新增的字符串我们没跟上，会导致中文界面缺字。
2. **`MemosList.kt` 是冲突重灾区**。布局、滚动、性能、多选、排序全在同一个文件。
   合之前先 `git diff` 看清楚，必要时先把它拆成多个文件再合。
3. **`Settings` 的数据结构**带 `schemaVersion`，我们加过字段。上游如果也加字段，
   必须核对 `SettingsSerializer` 的兼容处理，否则老用户升级会丢设置。
