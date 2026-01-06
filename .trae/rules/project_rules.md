// 1. 注释统一用中文 //wd 风格，保持简洁
// 2. 新增字符串统一写入 strings_nullgram.xml
// 3. 普通日志：android.util.Log.d("wd", "msg")，禁止拼写错误
// 4. 错误日志：android.util.Log.e("wd", "带堆栈信息的错误描述")
// 5. 开发环境：Win + WSL；adb 已映射，直连真机
//    每次任务完成后：
//    - ./gradlew :TMessagesProj:assembleDebug 安装到真机
//    - 自动打开 Logcat，过滤 tag:wd *:E
//    - 通过日志定位问题
// 6. 优先查阅 TGwiki 定位实现细节
// 7. 再对照官方 Telegram API 文档确认字段与行为
