1. 注释统一用中文 //wd 风格，保持简洁
2. 新增字符串统一写入 strings_nullgram.xml，英文和中文是必须的
3. 普通日志：android.util.Log.d("wd", "msg")，禁止拼写错误
4. 错误日志：android.util.Log.e("wd", "带堆栈信息的错误描述")
5. 开发环境：Win + WSL；adb 已映射，直连真机
   每次任务完成后：
    - 用./gradlew :TMessagesProj:lintDebug检查代码
    - 电脑配置不足不要直接执行编译运行验证
    - 我输入编译运行时再执行./gradlew :TMessagesProj:assembleDebug 安装到真机
6. 优先查阅 TGwiki 定位实现细节
7. 再对照官方 Telegram API 文档确认字段与行为
