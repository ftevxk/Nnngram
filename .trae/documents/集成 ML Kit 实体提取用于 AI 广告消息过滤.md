## 目标与成功标准
- 目标：在构建 `MessageObject` 时用 AI 模型判定“博彩/充值推广类广告”，命中则直接跳过该消息（不入列表、不落库、不进未读/推送队列），替代当前手动正则消息过滤。
- 成功标准：
  - 新消息：`MessagesController.processUpdates(...)` 中 `new MessageObject(...)` 之后立刻完成判定并过滤。[MessagesController.java:L17499-L17703](file:///c:/Users/dell/StudioProjects/Nnngram/TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java#L17499-L17703)
  - 历史/搜索/对话预览：其它关键构建入口（历史加载、dialogs 预览替换、搜索结果、推送未读恢复等）一致过滤。
  - 性能：不阻塞主线程；模型未就绪/推理失败不崩溃，自动降级为“不过滤”。

## 现状与可复用评估
- 现有“手动过滤”在 `MessageObject.isBlockedMessage()`（sponsored/拉黑/正则）。[MessageObject.java:L11735-L11775](file:///c:/Users/dell/StudioProjects/Nnngram/TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java#L11735-L11775)
- 项目内“AI 总结/摘要”是服务端 `messages.summarizeText`（`TranslateController`），触发时机与约束不适合用于“构建即过滤”，不建议直接复用为广告识别；但可复用其“控制器去重 + 回调通知 + 持久化派生字段”的架构思路。

## 技术方案（主方案）：本地文本分类模型（面向博彩/充值推广）
### 1) 模型形态
- 使用 TensorFlow Lite 文本分类模型（TFLite Task Text `NLClassifier`）对消息文本输出 `ad_prob`（或 label=ad/normal）。
- 模型目标域：博彩、上分/下分、充值返利、盘口、代理、群控、跑分等推广话术（含变体、错别字、夹杂符号）。

### 2) 训练与交付策略（让模型可迭代）
- 在仓库新增训练资料目录（例如 `tools/ml_ad_filter/`）：
  - 提供 Python/Colab 脚本（基于 TFLite Model Maker 的 text_classifier），把一份 CSV（text,label）训练并导出 `model.tflite + labels.txt + vocab.txt`。
  - 预置一份“种子数据集”（小规模但覆盖博彩/充值推广常见模式）用于首版可跑通；后续你们可持续追加“误杀/漏判样本”再训练替换模型文件。
- App 侧把模型文件放入 `src/main/assets/ai_ad_filter/`，使得“更新模型”只需替换 assets 文件，不改业务代码。

### 3) 推理与性能
- 新增 `MessageAiAdFilter`（建议放 `org.telegram.messenger`）：
  - 单例持有 `NLClassifier`，延迟初始化 + 预热加载（App 启动后后台初始化）。
  - 推理只在非主线程执行；缓存判定结果（`dialogId + messageId + textHash`）。
  - 可配置阈值：例如 `ad_prob >= threshold` 过滤；提供“严格模式”用于群/频道更激进过滤。
- 降级：模型加载失败/推理异常/超时 → 返回“不过滤”。

### 4) 过滤接入点（满足“构建 MessageObject 时过滤掉”）
- 在下列入口 `new MessageObject(...)` 后立即调用 `MessageAiAdFilter.shouldFilter(obj)`，命中则 `continue` 不加入集合：
  - `MessagesController.processUpdates(...)`（新消息/计划消息）。
  - `MessagesController.loadMessagesInternal(...)`（历史加载与落库链路）。
  - `MessagesController.reloadMessages(...)`（dialogs 预览替换）。
  - `MessagesStorage.searchMessagesByText(...)`、`searchSavedByTag(...)`（搜索结果）。
  - `MediaDataController.loadMoreHistoryForSearch(...)`、`HashtagSearchController`。
  - `MessagesStorage` 推送未读恢复（构建 `pushMessages`）。
- 同时改造 `MessageObject.isBlockedMessage()`：当“AI 广告过滤”开启时，不再读取手动正则（实现“完全通过 AI 模型替代手动过滤器”）。

## 辅助方案（可选增强，不作为主判定）
- 可选接入 ML Kit Entity Extraction 作为“特征增强/回退信号”（如 URL/金额/电话等实体），用于：
  - 模型输出接近阈值时辅助决策；
  - 模型缺失/未初始化时提供一个弱过滤能力。
- 但最终以 TFLite 文本分类模型为主，确保“博彩/充值推广”方向的召回与适配。

## 配置与 UI
- 新增配置项（复用现有 Config 生成机制）：
  - `aiAdFilterEnabled`（总开关）
  - `aiAdFilterThreshold`（阈值）
  - `aiAdFilterStrictMode`（严格模式，可选）
- 设置入口：在 `ChatSettingActivity` 里将“Message Filter”替换/旁路为“AI 广告过滤（博彩/充值）”，开启时隐藏/禁用原手动正则设置入口。
- 用户可见字符串写入 `strings_nullgram.xml`（中英文），用 `LocaleController` 引用。

## 日志与隐私
- 全程本地推理，不上传消息内容。
- 日志仅记录 messageId/dialogId/耗时/判定结果；使用 `FileLog.d/e` 且以“wd ”开头，不记录原文与敏感信息。

## 验证计划（Windows + 真机）
- 构建安装：`./gradlew clean` → `./gradlew :TMessagesProj:assembleDebug` → `./gradlew :TMessagesProj:installDebug`
- 真机用例：
  - 博彩/充值推广样例文本：应被过滤（聊天页/会话预览/搜索/未读/通知都不出现）。
  - 正常闲聊/通知类文本：不应误杀。
  - 模型文件缺失/损坏：不崩溃，自动降级为“不过滤”。

## 回滚预案
- 快速回滚：设置中关闭“AI 广告过滤”立即恢复原有（手动正则/其它）逻辑。
- 代码回滚：AI 逻辑集中在 `MessageAiAdFilter` 与少量构建入口的 `continue`，可快速撤销。
- 模型回滚：替换 assets 中的 `model.tflite` 为上一版本即可。
