# Nnngram — Project Guide

Telegram Android fork。直接基于 **Telegram official Android client** (`DrKLO/Telegram` 上游)，跟随 upstream `master@official` tag 周期 merge；包名 `xyz.nextalone.nnngram`。

> 历史上 fork 链是 `Telegram → Nullgram → Nnngram`，但目前 **不再依赖 Nullgram 仓库**，所有 fork-only 代码在本仓库内独立维护，merge 只对接 upstream Telegram。

## 仓库结构

| 路径 | 角色 |
|---|---|
| `TMessagesProj/` | 主 module (APK 产物来源)。`namespace=org.telegram.messenger`, `applicationId=xyz.nextalone.nnngram` |
| `TMessagesProj/src/main/java/org/telegram/**` | 上游代码 (merge 时尽量按 upstream，不主动改) |
| `TMessagesProj/src/main/java/xyz/nextalone/nnngram/**` | fork-only Java/Kotlin |
| `TMessagesProj/src/main/res/values{,-zh,-zh-rTW}/strings_nullgram.xml` | fork-only 字符串 (3 语言对齐) |
| `libs/{tcp2ws,pangu,ksp}` | 子 module；`libs/ksp` 是配置代码生成器 |
| `.github/scripts/upload_ci.py` | CI 发包到 Telegram 的脚本 (caption / changelog 渲染) |
| `.github/workflows/{ci,pr,revert-stable-commit}.yml` | 构建/发布管线 |

## 工具链
- JDK 21+, Android SDK 36 (build-tools 36.0.0), NDK 27+ (CMake 3.22+)
- Gradle 9.x via `./gradlew` wrapper
- adb (`adb devices` 必须见序列号才能 `installDebug`)

## 构建 / 安装
- Debug APK + 安装: `./gradlew :TMessagesProj:installDebug`
- Debug APK (仅编译): `./gradlew :TMessagesProj:assembleDebug`
- Release APK: `./gradlew :TMessagesProj:assembleRelease`
- 产物: `TMessagesProj/build/outputs/apk/{debug,release}/Nnngram-v<ver>-<sha>-<abi>.apk`
- 卸载: `adb uninstall xyz.nextalone.nnngram`
- 启动: `adb shell monkey -p xyz.nextalone.nnngram -c android.intent.category.LAUNCHER 1`
- 清理: `./gradlew clean`
- Lint: `./gradlew :TMessagesProj:lintDebug`
- 任务列表: `./gradlew :TMessagesProj:tasks`

## 版本
`gradle.properties` 是 single source of truth：
- `APP_VERSION_CODE` (整数)
- `APP_VERSION_NAME` (semver)

merge upstream 后这两个值需要同步成 upstream 的 vsersion。

## Config 系统 (KSP 生成)

fork 配置项不手写 boilerplate，全部走 `libs/ksp` 自动生成：

1. `TMessagesProj/src/main/java/xyz/nextalone/nnngram/utils/Defines.kt` 加常量：
   ```kotlin
   @BooleanConfig const val myToggle = "myToggle"            // 默认 false
   @BooleanConfig(true) const val myToggleOn = "myToggleOn"  // 默认 true
   @IntConfig(default = 5) const val myInt = "myInt"
   @StringConfig const val myStr = "myStr"
   ```
2. build 后 `xyz.nextalone.gen.Config` 自动包含 `myToggle` getter + `toggleMyToggle()` mutator (boolean) / `setMyInt(int)` (int) 等。
3. UI 入口：对应分类的 `xyz/nextalone/nnngram/activity/*SettingActivity.java` 里加 row, 调 generated mutator。
4. 字符串：`strings_nullgram.xml` 三语言全配。

## Fork 模式约定

- **upstream-touching 改动**：能不改 `org.telegram.**` 就别改 — merge 冲突成本高。优先把逻辑放到 `xyz.nextalone.nnngram.*` 帮助类，在 upstream 文件里只插**一行**调用。
- **upstream listener 覆盖陷阱**：上游用 `setOnXxxListener` 多次注册时，**最后一次胜出**。fork 在同 view 已注册 listener，merge 后必复查是否被上游新增的同 setter 覆盖（典型例：`MainTabsActivity` 长按 chat tab）。
- **drawable / icon 系列**：fork 用 `nagram_*` 自定义图标，不引入 upstream 新增的 `icon_2/4/5/6` 启动器系列。
- **CMakeLists**：保留 fork 的 cpp20/ccache/-Bsymbolic；NDK 27+ warning flag 跟 upstream。
- **3 语言强约束**：`values/`、`values-zh/`、`values-zh-rTW/` 三份 `strings_nullgram.xml` 必须同步新增/删除字符串，缺一会被 lint 拒绝。

## Commit 规范

**Conventional Commits**：subject 首词必须是 `feat|fix|perf|refactor|docs|test|build|ci|style|chore|revert|merge` 之一 (CI 按这个分组渲染发布消息)。

格式：
```
<type>(<scope>)?: <subject>
<空行>
<body — 多段, 解释 why, 含失败案例 / 关联 issue>

<空行>
<trailer 区, 类似 Co-Authored-By 风格>
```

### `Setting-Path` trailer (新增功能开关时强制)

加新 Config toggle / setting 时，**commit body 末尾必须**加 `Setting-Path:` trailer 指明 in-app 路径与 deep-link。CI 会把它**直接渲染到发到 Telegram 的 caption / changelog 里**，给用户一个一眼定位开关、且**可点击直接跳转**的索引。

格式：
```
Setting-Path: <UI 文案路径> | <deep-link URL>
```

- `|` 前后两段都 trim, anchor 给用户看, URL 给点击跳转
- 一个 commit 可以加多条 `Setting-Path:` (一个功能开多个 toggle 时)
- 路径用 `→` 分隔层级
- URL 可省略（向后兼容）：仅 `Setting-Path: <text>` → 渲染为斜体不带链接

#### Deep-link URL 形式

fork 已通过 `SettingsHelper.processDeepLink` (`xyz.nextalone.nnngram.helpers`) 暴露设置项 deep-link 路由。两种等价 URL 都可用：

| 形式 | 例子 | 说明 |
|---|---|---|
| `https://t.me/nnnsettings/<cat>?r=<row_key>` | `https://t.me/nnnsettings/c?r=showRecentChatsOnTabLongPress` | **推荐**。Bot API HTML 模式必过 filter，点击通过 `t.me` intent filter 被 Telegram 客户端接管，转给 `LaunchActivity` → `SettingsHelper` |
| `tg://nnn/<cat>?r=<row_key>` | `tg://nnn/c?r=showRecentChatsOnTabLongPress` | 直接 scheme，`LaunchActivity` 处会改写到 `nnnsettings`。`tg://` link 在 Bot API HTML 模式的兼容性较差，**不推荐发到频道** |

`<cat>` 由目标 `SettingActivity.getKey()` 决定：

| key | Activity |
|---|---|
| `c` (或 `chat` / `chats`) | `ChatSettingActivity` |
| `g` (或 `general`) | `GeneralSettingActivity` |
| `e` (或 `experimental`) | `ExperimentSettingActivity` |
| `` (空) | `MainSettingActivity` (nnn 设置主页) |
| `<passcode key>` | `PasscodeSettingActivity` |

`<row_key>` = 在对应 `SettingActivity.updateRows()` 中 `addRow("<key>")` 传入的 key，**等同 `Defines.kt` 里的 const name**（因为 KSP 把 `Defines` 的常量值用作 row key）。SettingsHelper 解析 query 参数后调 `BaseActivity.scrollToRow(key)` 自动滚到对应位置。

#### 示例

```
feat(chat-menu): 长按聊天标签弹最近会话 toggle

恢复 12.7 merge 后被 openFoldersSelector 覆盖的 fork 入口,
新增 Config 网关让用户在两套行为间切换.

Setting-Path: nnn 设置 → 聊天 → 长按聊天标签显示最近会话 | https://t.me/nnnsettings/c?r=showRecentChatsOnTabLongPress
```

渲染后 caption (Telegram) 形如：
```
✨ Features
• [abc1234] feat(chat-menu): 长按聊天标签弹最近会话 toggle
  ↳ ⚙ nnn 设置 → 聊天 → 长按聊天标签显示最近会话      ← 可点击, 直接跳进 app 对应开关
```

非"加新开关"的 commit (fix/refactor/ci/...) **不要**加 `Setting-Path:`，避免噪音。

## CI 发布流 (`upload_ci.py`)

1. `git log LAST_SENT_SHA..HEAD` 拿待发 commits (含失败 build 残留)
2. POST 完整 changelog 到 `METADATA_CHANNEL`
3. POST arm64 APK + caption 到 `CHAT_ID` (主频道)
4. POST 同 APK 到 `APK_CHANNEL`, 记 `message_id - 1` 作为 `start_id` 关联 changelog
5. 缓存 HEAD SHA → 下次 build 用

caption budget = 1024B (减去 chip / footer 余量), 超出按 group 倒序丢尾条。`Setting-Path` 渲染在 subject 同 entry 内，被丢掉时连开关路径一起丢——所以**控制 commit 数量**保证不溢出。
