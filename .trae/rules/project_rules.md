1、注释统一用中文 //wd 风格，保持简洁  
2、字符串添加到strings_nullgram
3、日志统一写 android.util.Log.d("wd", "msg")，禁止拼写错误  
4、错误日志统一写 android.util.Log.e("wd", "带堆栈信息的错误描述")
5、Win 主系统 + WSL 项目目录；adb 已映射到 Win，可直连真机。每次任务完成后：  
   - 自动编译Debug包安装到真机运行  
   - 自动打开 Logcat 并过滤 tag:wd *:E  
   - 通过 Logcat 排查日志信息，定位问题  
6、优先查阅 TGwiki（Telegram 知识库）定位实现细节  
7、再对照官方 Telegram API 文档确认接口字段与行为
