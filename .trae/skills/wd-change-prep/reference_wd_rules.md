# wd 关键规范速查

## 注释

- 必须中文，且以 `//wd` 前缀
- 注释解释“为什么这么做”，不要复述代码

## 字符串

- 用户可见字符串禁止硬编码
- 统一写入 `strings_nullgram.xml`，中英文双语都要有
- 通过 `LocaleController.getString()/formatString()/formatPluralString()` 使用

## 日志

- TAG 统一 `"wd"`
- 文案必须中文
- `Log.e()` 必须带异常堆栈
- 禁止输出敏感信息（token/手机号/聊天内容等）
- 性能敏感路径用 `BuildVars.LOGS_ENABLED` 控制

