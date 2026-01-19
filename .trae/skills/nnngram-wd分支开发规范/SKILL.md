---
name: Nnngram wd分支开发规范
description: 本规范文件为Nnngram wd分支开发规范准则，确保所有代码贡献符合项目统一标准。
---

# Nnngram wd分支开发规范

本规范文件为Nnngram wd分支开发规范准则，确保所有代码贡献符合项目统一标准。

## 1 注释与字符串规范

### 1.1 注释要求

所有代码注释必须使用中文，并以 `//wd` 作为前缀标识。注释的核心目的是解释"为什么"做这个决定，而非描述代码在做什么。

**正确示例：**
```java
//wd 为了避免内存泄漏，需要在 Activity 销毁时取消网络请求
@Override
protected void onDestroy() {
    cancellable.cancel();
}
```

**错误示例：**
```java
// 这个方法用来取消请求（不应这样写）
@Override
protected void onDestroy() {
    cancellable.cancel();
}
```

注释应放置在被注释代码的上方，避免在代码行末添加冗长注释。每段逻辑应有明确的中文注释说明其设计意图和业务背景。

### 1.2 字符串资源规范

所有用户可见的字符串必须写入 `strings_nullgram.xml` 文件，严禁在 Java 或 Kotlin 代码中硬编码字符串。每个字符串必须包含中文和英文两种语言的翻译。

**字符串命名规则：**
- 使用 snake_case 命名法（小写下划线分隔）
- 名称应具有描述性，能表达字符串用途
- 避免使用缩写，保持清晰易懂

**正确示例：**
```xml
<!-- values/strings_nullgram.xml -->
<string name="allow_screenshot_on_no_forward_chat">Bypass Screenshot Limit</string>
<string name="allow_screenshot_on_no_forward_chat_warning">It will also allow you to bypass screenshot limit in SecureChat!</string>

<!-- values-zh/strings_nullgram.xml -->
<string name="allow_screenshot_on_no_forward_chat">绕过屏幕截图限制</string>
<string name="allow_screenshot_on_no_forward_chat_warning">它还将绕过私密聊天的截图限制！</string>
```

**错误示例：**
```java
// 在代码中硬编码字符串
textView.setText("绕过截图限制");
```

### 1.3 字符串使用方式

本项目使用 `LocaleController` 类来管理字符串资源，这是 Telegram 官方推荐的方式。`LocaleController` 提供了字符串的本地化支持和格式化功能。

#### 1.3.1 基本字符串引用

使用 `LocaleController.getString()` 方法引用字符串资源，需要传入字符串的键名和资源 ID：

```java
// 基本字符串引用（推荐方式）
textView.setText(LocaleController.getString("AllowScreenshotOnNoForwardChat", R.string.AllowScreenshotOnNoForwardChat));

// 在 Dialog 或 Builder 中使用
new AlertDialog.Builder(context)
    .setTitle(LocaleController.getString("Done", R.string.Done))
    .setMessage(LocaleController.getString("Cancel", R.string.Cancel))
    .setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {})
    .create()
    .show();

// 在列表项中使用
menu.addItem(1, LocaleController.getString("ProxySettings", R.string.ProxySettings));
```

#### 1.3.2 带占位符的字符串

当字符串包含占位符（如 `%1$s`、`%2$d`）时，使用 `LocaleController.formatString()` 方法，并按顺序传入参数：

```java
// 带占位符的字符串格式化
String message = LocaleController.formatString("LastUpdateDateFormatted", R.string.LastUpdateDateFormatted, date);
textView.setText(message);

// 多参数格式化
String info = LocaleController.formatString("NullgramVersion", R.string.NullgramVersion, appVersion, tgVersion);

// 在 AlertDialog 中使用占位符
new AlertDialog.Builder(context)
    .setMessage(LocaleController.formatString("DeleteAllFromSelfAlert", R.string.DeleteAllFromSelfAlert))
    .setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {})
    .create()
    .show();
```

#### 1.3.3 复数字符串

对于需要根据数量变化显示不同文本的情况（如"1 条消息"、"5 条消息"），使用 `LocaleController.formatPluralString()` 方法：

```java
// 复数字符串
LocaleController.formatPluralString("SaveToDownloadCount", count);
LocaleController.formatPluralString("MessageCount", messageCount);
```

#### 1.3.4 在 Kotlin 中的使用

在 Kotlin 文件中，同样使用 `LocaleController` 类，语法与 Java 一致：

```kotlin
// Kotlin 中引用字符串
textView.text = LocaleController.getString("AllowScreenshotOnNoForwardChat", R.string.AllowScreenshotOnNoForwardChat)

// 带占位符的字符串
val message = LocaleController.formatString("LastUpdateDateFormatted", R.string.LastUpdateDateFormatted, date)

// 复数字符串
LocaleController.formatPluralString("SaveToDownloadCount", count)
```

#### 1.3.5 注意事项

- **必须传入资源 ID**：`LocaleController.getString()` 和 `LocaleController.formatString()` 方法必须同时传入字符串键名和对应的资源 ID
- **避免直接使用 `getString()`**：除非在特定场景下，否则优先使用 `LocaleController` 的方法
- **占位符顺序**：格式化参数时，占位符的顺序必须与字符串资源中的顺序一致
- **查找字符串键名**：所有字符串的键名定义在 `strings_nullgram.xml` 文件中，资源 ID 在 `R.java` 文件中自动生成

## 2 日志规范

### 2.1 日志标签统一

所有日志必须使用 `"wd"` 作为日志标签，保持日志输出一致性，便于在 Logcat 中过滤和查找。

### 2.2 普通日志格式

普通调试日志使用 `Log.d()` 方法，日志信息必须为中文，描述当前执行的操作或状态变化：

```java
// 正确格式
Log.d("wd", "视频URI修正-开始 " + getSafeUriInfo(uri));
Log.d("wd", "视频URI修正-content可读 直接使用 " + getSafeUriInfo(uri));
Log.d("wd", "视频URI修正-content不可读 回退=" + (fallbackUri != null) + " 原=" + getSafeUriInfo(uri));

// 错误格式（禁止）
Log.d("TAG", "some english message");  // 标签错误
Log.d("wd", "Video URI fix started");  // 信息应为中文
```

### 2.3 错误日志格式

错误日志使用 `Log.e()` 方法，必须包含中文描述和完整的异常堆栈信息：

```java
// 正确格式
try {
    // 可能抛出异常的代码
    refreshSearchResults();
} catch (Exception e) {
    Log.e("wd", "刷新搜索结果失败", e);
}

// 错误格式（禁止）
Log.e("wd", "Refresh failed");  // 信息应为中文
Log.e("wd", "刷新搜索结果失败");  // 缺少异常参数
```

### 2.4 日志安全规范

严格禁止在日志中输出任何敏感信息，包括但不限于：

- 用户个人数据（手机号、用户名、聊天内容等）
- API 密钥、Token、密码等认证凭据
- 文件路径中包含的真实用户名或设备标识
- 网络请求中的敏感 Headers 或 Payload

```java
// 错误示例（禁止）
Log.d("wd", "用户登录成功 token=" + userToken);  // 泄露Token
Log.d("wd", "用户手机号=" + phoneNumber);  // 泄露用户信息
```

### 2.5 性能日志建议

对于可能影响性能的代码路径，建议使用条件判断控制日志输出：

```java
if (BuildVars.LOGS_ENABLED) {
    Log.d("wd", "耗时操作开始执行");
}
try {
    // 耗时操作
} finally {
    if (BuildVars.LOGS_ENABLED) {
        Log.d("wd", "耗时操作完成，耗时=" + (System.currentTimeMillis() - start) + "ms");
    }
}
```

## 3 编译运行规范

### 3.1 构建环境

本项目使用 Gradle 进行构建，开发环境要求如下：

- **操作系统**：Windows PowerShell 环境
- **构建工具**：Gradle（通过 `gradlew` 脚本执行）
- **Android SDK**：确保已正确配置 ANDROID_HOME 环境变量

### 3.2 编译命令

执行完整的编译、安装和启动流程，使用以下命令序列：

```powershell
 编译并安装 Debug# 版本到设备
.\gradlew :TMessagesProj:installDebug

# 启动应用
adb shell am start -n xyz.nextalone.nnngram/org.telegram.ui.LaunchActivity
```

### 3.3 常用 Gradle 任务

项目支持以下常用 Gradle 任务：

```powershell
# 清理构建产物
.\gradlew clean

# 编译 Debug 版本
.\gradlew assembleDebug

# 编译 Release 版本
.\gradlew assembleRelease

# 运行单元测试
.\gradlew test

# 执行 lint 检查
.\gradlew lint
```

### 3.4 构建问题排查

遇到构建失败时，按以下步骤排查：

1. **检查 Gradle 同步**：确保 Android Studio 完成 Gradle 同步
2. **清理并重建**：执行 `.\gradlew clean` 后重新编译
3. **检查依赖**：确认网络连接正常，依赖仓库可访问
4. **查看错误日志**：仔细阅读 Gradle 控制台输出的错误信息

## 4 代码风格规范

### 4.1 缩进与空格

- 使用 **4 个空格** 进行缩进
- **严禁使用 Tab 字符**
- 每行代码不超过 **120 个字符**
- 方法参数、链式调用等长行需要合理换行对齐

**正确示例：**
```java
// 4空格缩进
public void exampleMethod(String longParameter1, String longParameter2,
                          String longParameter3, int longParameter4) {
    if (condition1 && condition2 && condition3 && 
        condition4 && condition5) {
        doSomething();
    }
}

// 链式调用换行
ImageLoader.getInstance().loadImage(url, imageView, 
    options, callback);
```

### 4.2 花括号风格

所有代码块必须使用花括号，即使只有单行语句：

```java
// 正确
if (condition) {
    doSomething();
} else {
    doOtherThing();
}

// 错误（禁止）
if (condition)
    doSomething();
```

### 4.3 导包规范

- 所有使用的类必须完整导入
- 禁止使用通配符导入（如 `import java.util.*`）
- 导入语句按以下顺序分组：
  1. Android 相关导入
  2. Kotlin 标准库导入
  3. 第三方库导入
  4. 项目内部导入
- 移除所有未使用的导入

**使用 Android Studio 的 "Optimize Imports" 功能（Ctrl+Alt+O）自动整理导入。**

### 4.4 命名规范

- **类名**：使用 PascalCase（如 `DialogsActivity`、`MessageHelper`）
- **方法名、变量名**：使用 camelCase（如 `getUserId`、`isLoading`）
- **常量**：使用全大写加下划线（如 `MAX_RETRY_COUNT`）
- **布局资源文件**：使用 snake_case（如 `activity_dialog.xml`）

### 4.5 代码格式化

使用 Android Studio 默认的代码格式化规则：

- **快捷键**：Ctrl+Alt+L（Windows/Linux）
- **格式化范围**：选中代码后使用快捷键，或格式化整个文件
- **格式化时机**：提交代码前务必格式化

## 5 修改规范

### 5.1 修改前检查

在进行代码修改前，确保：

1. **理解现有代码**：阅读相关代码及其上下文，理解其设计意图
2. **遵循项目规范**：检查现有代码的命名、注释、日志风格
3. **准备修改方案**：明确修改的范围和影响，避免意外破坏功能

### 5.2 代码块结构

修改时确保代码块结构正确：

- 花括号必须配对正确
- 条件语句和循环语句必须有执行块
- 嵌套层级不宜过深（建议不超过 3 层）
- 使用空行分隔逻辑代码块

```java
// 逻辑清晰的代码块示例
public void processMessage(Message message) {
    if (message == null) {
        Log.w("wd", "收到空消息，跳过处理");
        return;
    }

    // 验证消息
    if (!validateMessage(message)) {
        Log.e("wd", "消息验证失败", new IllegalArgumentException("Invalid message"));
        return;
    }

    // 处理消息
    handleMessage(message);
}
```

### 5.3 整理导入

**每次代码修改后，必须使用 "Optimize Imports" 功能整理导入：**

1. 打开修改后的文件
2. 使用快捷键 **Ctrl+Alt+O** 优化导入
3. 确认无未使用的导入被移除
4. 确认必要的导入已添加

### 5.4 语法错误检查

修改完成后检查以下常见语法错误：

- 括号和引号是否配对
- 逗号和分号是否正确
- 变量是否已声明和初始化
- 方法调用参数是否正确
- 空指针安全检查

### 5.5 提交与回滚

对于大型修改：

1. **分步提交**：将大型修改拆分为多个小提交，每个提交完成独立功能
2. **清晰提交信息**：提交信息使用中文描述修改内容
3. **便于回滚**：每个提交应能独立回滚而不影响其他功能
4. **同步更新**：修改涉及多个关联文件时，确保所有相关文件同步更新

### 5.6 多文件修改

当修改涉及多个文件时：

- 确保修改一致性：所有文件遵循相同风格
- 同步更新相关文件：如修改了布局文件，同步更新对应的 Activity/Fragment
- 检查资源引用：确保字符串、颜色、尺寸等资源引用正确
- 更新文档：如有必要，更新相关注释和文档

## 6 代码审查要点

在提交代码前，自查以下要点：

### 6.1 注释检查
- [ ] 所有注释使用中文 `//wd` 前缀
- [ ] 注释解释了"为什么"而非"是什么"
- [ ] 复杂逻辑有必要的注释说明

### 6.2 字符串检查
- [ ] 无硬编码的用户可见字符串
- [ ] 新字符串已在 `strings_nullgram.xml` 添加中英文翻译
- [ ] 字符串命名使用 snake_case

### 6.3 日志检查
- [ ] 所有日志使用 `"wd"` 作为 TAG
- [ ] 日志信息为中文描述
- [ ] 错误日志包含异常堆栈
- [ ] 无敏感信息泄露

### 6.4 格式化检查
- [ ] 使用 4 空格缩进，无 Tab 字符
- [ ] 每行不超过 120 字符
- [ ] 花括号风格一致
- [ ] 已执行 "Optimize Imports"

### 6.5 功能检查
- [ ] 代码逻辑正确实现需求
- [ ] 边界情况和异常已处理
- [ ] 性能无明显退化
- [ ] 与现有功能无冲突

## 7 常用快捷键

| 操作 | Windows/Linux |
|------|---------------|
| 格式化代码 | Ctrl+Alt+L |
| 优化导入 | Ctrl+Alt+O |
| 快速修复 | Alt+Enter |
| 代码补全 | Ctrl+Space |
| 查找文件 | Ctrl+Shift+N |
| 全局搜索 | Ctrl+Shift+F |

## 8 参考资源

- [Android 官方编码规范](https://developer.android.com/kotlin/style-guide)
- [Google Java 风格指南](https://google.github.io/styleguide/javaguide.html)
- [项目 GitHub 仓库](https://github.com/qwq233/Nnngram)
- [Telegram API 文档](https://core.telegram.org/api)
- [TGwiki - Telegram知识库](https://wiki.tgnav.org/guide.html)

---

**遵循以上规范，确保代码质量和项目一致性。**