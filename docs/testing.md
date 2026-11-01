# 测试与验证

## 三层，各管各的

| 层 | 在哪跑 | 管什么 | 多久 |
| --- | --- | --- | --- |
| 单元测试 | 每次 push 的 CI（`build-branch.yml`） | 纯逻辑、DAO、序列化、排序规则 | ~4 分钟 |
| 构建验证 | 同上 | 能不能编译、能不能打出可安装的包 | 同上 |
| 真机验证 | 测试机（USB + adb） | 布局、手势、滚动、真实文件/数据库行为 | 几十秒一轮 |

**三层的顺序不能颠倒。** 逻辑问题不该靠真机发现，真机不该用来验证"这个函数算得对不对"。

## CI 做了什么

| 工作流 | 触发 | 产出 |
| --- | --- | --- |
| `build-branch.yml` | 任何分支 push、手动 dispatch | 编译 + 单元测试 + **`app-debug.apk` 产物（保留 14 天）** |
| `build-signed-release-apk.yml` | 打 tag | 签名 APK + Release，并**断言 APK 内版本号与 tag 一致** |

`build-branch.yml` 产出的 debug 包**用 release keystore 签名**，所以能直接覆盖安装到
已装正式版的手机上、不丢数据。没有密钥的环境（如来自 fork 的 PR）自动退回默认
debug key，仍然能编译和测试。

> 装 debug 包时记得带 `-d`：它的 `versionCode` 默认是 1，比正式版低。

## 真机验证怎么做

不需要人工截图。测试机（`YAL-AL00` / Android 10 / 720×1560）连着 USB 时：

```bash
adb install -r -d app-debug.apk
adb shell am start -n me.mudkip.moememos/.MainActivity
adb exec-out screencap -p > shot.png          # 抓屏
adb shell uiautomator dump /sdcard/ui.xml     # 读屏幕上的文本与坐标（断言用）
adb shell input tap X Y / input text / input keyevent 4
adb logcat -d -v brief                        # 看崩溃与 Timber 日志
```

**断言用 `uiautomator dump` 读坐标，不要靠肉眼看截图。** 这次的滚动回归就吃过一次亏：
截图对比显示"差了两张卡片"，换成读 `bounds` 后才发现前后首个可见项都是同一个备忘，
只差 42px。截图适合看"长什么样"，不适合判断"有没有变"。

## 现有测试（`app/src/test/`，21 个文件）

| 类别 | 文件 |
| --- | --- |
| 数据层 | `MemoDaoTest`、`MigrationsTest`、`SettingsSerializerTest` |
| 仓库/同步 | `SyncingRepositoryTest`、`DeferredPushSchedulerTest` |
| 列表逻辑 | `MemoListUpdateTest`、`MemoSortTest`、`MemoContentMatchTest`、`MemoQueryTest`、`MemoSelectionTest` |
| 模型/设置 | `ExploreLayoutTest`、`FontScaleTest`、`ImageQualityTest`、`ThemeModeTest`、`SettingsAccessTest`、`SearchHistoryTest` |
| 附件顺序 | `ResourceOrderTest` |
| 编辑器/渲染 | `SeedContentForTagTest`、`MarkdownTest` |
| 其它 | `LocalNetworkTest` |

## 已知缺口（待补）

这些是**已经踩过的坑，但还没有测试保护**。任何一条被补上就算赢一个：

- [ ] `updateMemo` 不得删除未在传入列表中的附件（2026-10 丢图事故的根因）
- [ ] `createMemo` 落库后，关联到该备忘的附件数 == 传入数（半成品备忘）
- [ ] `planMemoListUpdate` 在 `resources` 变化时必须替换对象
      （`MemoEntity.resources` 是类体属性，`equals` 不看它）
- [ ] 设置读取不得用编造的默认值渲染第一帧
      （`CREATED` → `TITLE` 那一跳导致列表重排）
- [ ] 滚动位置：返回列表后首个可见备忘不变（目前只有人工验证）

前四条都能用纯 JVM 测试写，不需要真机。

## 什么不该进单元测试

- 像素级视觉（间距好不好看、颜色对不对）→ 人工判断，必要时截图存档
- 真机特有的行为（HarmonyOS/EMUI 的省电策略、通知限制）→ 真机验证
- 第三方服务的真实网络行为 → 用假的 repository 替代
