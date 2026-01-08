# 项目开发规范

## 1. 注释与字符串
- 注释使用中文 //wd 风格，解释"为什么"而非"是什么"
- 所有字符串写入 strings_nullgram.xml，中英文翻译必不可少
- 字符串命名采用 snake_case 格式

## 2. 日志规范
- 普通日志：Log.d("wd", "信息")，禁止拼写错误
- 错误日志：Log.e("wd", "描述", exception)，必须包含堆栈
- 禁止记录敏感信息（用户数据、密钥等）

## 3. 编译运行
- Gradle 编译，PowerShell 环境
- 编译安装：./gradlew :TMessagesProj:installDebug; adb shell am start -n xyz.nextalone.nnngram/org.telegram.ui.LaunchActivity
- 提交前确保本地编译通过

## 4. 代码风格
- 遵循 Android Studio 默认格式化规则
- 缩进4个空格，禁止 Tab
- 每行不超过120字符
- 导包完整，移除未使用的导包

## 5. 修改规范
- 检查代码块结构，确保花括号匹配
- 修改后使用 "Optimize Imports" 整理导包
- 避免复制粘贴导致的语法错误
- 大型修改分步提交，便于回滚
- 多文件修改确保关联文件同步更新
