/*
 * Copyright (C) 2024 Nnngram
 * AI 广告消息过滤器 - 特征覆盖率架构
 * 阶段1: 主题分析模型 - 总结消息主题内容
 * 阶段2: 特征覆盖率模型 - 计算覆盖率分数
 * 超过阈值即判定为广告
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import xyz.nextalone.nnngram.config.ConfigManager;
import xyz.nextalone.nnngram.utils.Defines;

public class MessageAiAdFilter {
    private static volatile MessageAiAdFilter instance;
    private final Context context;
    private SimpleAdDetector simpleDetector;
    private MessageTopicAnalyzer topicAnalyzer;
    private AtomicBoolean useSimpleDetector = new AtomicBoolean(false);
    private final Map<String, FilterResult> cache = new HashMap<>();
    private final Object cacheLock = new Object();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    //wd 配置参数
    private float coverageThreshold = 0.5f;  //wd AI广告特征覆盖率阈值
    //wd 主题分析始终启用，作为AI广告过滤的必要步骤
    private static final boolean TOPIC_ANALYSIS_ENABLED = true;

    private static final String CACHE_KEY_SEPARATOR = "_";

    private String messageFilterTextCached;
    private Pattern messageFilterPatternCached;
    private ArrayList<String> messageFilterKeywordsCached;
    private final Object filterConfigLock = new Object();

    private static class FilterResult {
        final boolean isAd;
        final float confidence;
        final String reason;
        final long timestamp;

        FilterResult(boolean isAd, float confidence, String reason) {
            this.isAd = isAd;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private MessageAiAdFilter(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
        this.topicAnalyzer = MessageTopicAnalyzer.getInstance();
        this.topicAnalyzer.init(context);
    }

    //wd 加载配置
    private void loadConfig() {
        //wd 使用AI广告特征覆盖率配置
        coverageThreshold = ConfigManager.getFloatOrDefault(Defines.aiAdFeatureCoverageThreshold, 0.5f);
        coverageThreshold = Math.max(0f, Math.min(1f, coverageThreshold));
        //wd 主题分析始终启用，不需要从配置读取
        updateMessageFilterCache();
    }

    public static MessageAiAdFilter getInstance(Context context) {
        if (instance == null) {
            synchronized (MessageAiAdFilter.class) {
                if (instance == null) {
                    instance = new MessageAiAdFilter(context);
                }
            }
        }
        return instance;
    }

    public static MessageAiAdFilter getInstance() {
        return instance;
    }

    /**
     * 初始化 AI 广告过滤器，加载模型
     */
    public void initialize() {
        if (isInitialized.get()) {
            FileLog.d("wd MessageAiAdFilter 已初始化");
            return;
        }

        executor.execute(() -> {
            try {
                FileLog.d("wd MessageAiAdFilter: 开始初始化");

                // 初始化主题分析器（加载模型）
                if (topicAnalyzer != null) {
                    topicAnalyzer.init(context);
                }

                //wd 同步消息过滤器关键词到ad_keywords.txt
                syncMessageFilterToKeywords();

                isInitialized.set(true);
                FileLog.d("wd MessageAiAdFilter: 初始化完成");
            } catch (Exception e) {
                FileLog.e("wd MessageAiAdFilter 初始化错误", e);
            }
        });
    }

    //wd 获取关键词文件路径
    private File getKeywordsFile() {
        File externalDir = context.getExternalFilesDir(null);
        File nnngramFilesDir = new File(externalDir, "Nnngram Files");
        File aiFilterDir = new File(nnngramFilesDir, "ai_ad_filter");
        if (!aiFilterDir.exists()) {
            aiFilterDir.mkdirs();
        }
        return new File(aiFilterDir, "ad_keywords.txt");
    }

    //wd 同步消息过滤器设置的关键词到ad_keywords.txt
    private void syncMessageFilterToKeywords() {
        String filterText = ConfigManager.getStringOrDefault(Defines.messageFilter, "");
        if (TextUtils.isEmpty(filterText)) {
            return;
        }

        //wd 解析关键词（按 | 分割）
        String[] keywords = filterText.split("\\|");

        File keywordsFile = getKeywordsFile();
        try {
            //wd 读取现有内容，避免重复
            Set<String> existingKeywords = loadExistingKeywords(keywordsFile);

            FileWriter writer = new FileWriter(keywordsFile, true); //wd 追加模式
            writer.write("\n# ============================================\n");
            writer.write("# 用户自定义关键词 (来自消息过滤器设置)\n");
            writer.write("# ============================================\n");

            int addedCount = 0;
            for (String keyword : keywords) {
                keyword = keyword.trim();
                //wd 过滤正则表达式，只保留纯文本
                if (!TextUtils.isEmpty(keyword) && !containsRegexMeta(keyword) && !existingKeywords.contains(keyword)) {
                    writer.write(keyword + ",0.9,user_defined\n");
                    existingKeywords.add(keyword);
                    addedCount++;
                }
            }
            writer.close();

            if (addedCount > 0) {
                FileLog.d("wd 已同步 " + addedCount + " 个关键词到 ad_keywords.txt");
                //wd 重新加载关键词
                if (topicAnalyzer != null) {
                    topicAnalyzer.reloadKeywords();
                }
            }
        } catch (IOException e) {
            FileLog.e("wd 同步关键词失败", e);
        }
    }

    //wd 检查文本是否包含正则表达式元字符
    private boolean containsRegexMeta(String text) {
        return text.matches(".*[\\\\.\\*\\+\\?\\^\\$\\[\\]\\(\\)\\{\\}].*");
    }

    //wd 加载已存在的关键词
    private Set<String> loadExistingKeywords(File file) throws IOException {
        Set<String> keywords = new HashSet<>();
        if (!file.exists()) {
            return keywords;
        }

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length > 0) {
                keywords.add(parts[0].trim());
            }
        }
        reader.close();
        return keywords;
    }

    //wd 设置AI广告特征覆盖率阈值
    public void setCoverageThreshold(float threshold) {
        this.coverageThreshold = Math.max(0f, Math.min(1f, threshold));
        ConfigManager.putFloat(Defines.aiAdFeatureCoverageThreshold, this.coverageThreshold);
        clearCache();
    }

    //wd 获取AI广告特征覆盖率阈值
    public float getCoverageThreshold() {
        return coverageThreshold;
    }

    public boolean isEnabled() {
        return ConfigManager.getBooleanOrDefault(Defines.aiAdFilterEnabled, false);
    }

    public boolean shouldFilter(@NonNull MessageObject messageObject) {
        if (!isEnabled()) {
            return false;
        }
        String text = getMessageText(messageObject);
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return shouldFilter(text, messageObject.getDialogId(), messageObject.getId());
    }

    private String getMessageText(@NonNull MessageObject messageObject) {
        if (!TextUtils.isEmpty(messageObject.messageText)) {
            return messageObject.messageText.toString();
        }
        if (!TextUtils.isEmpty(messageObject.caption)) {
            return messageObject.caption.toString();
        }
        return "";
    }

    public boolean shouldFilter(@NonNull String text, long dialogId, long messageId) {
        if (!isEnabled()) {
            FileLog.d("wd AI广告过滤器: 已禁用，跳过消息 " + messageId);
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            FileLog.d("wd AI广告过滤器: 空文本，跳过消息 " + messageId);
            return false;
        }

        String cacheKey = buildCacheKey(dialogId, messageId, text);
        FilterResult cached = getFromCache(cacheKey);
        if (cached != null) {
            // 缓存命中，直接返回结果，不打印日志避免循环
            return cached.isAd;
        }

        // 阶段0: 用户自定义过滤器（最高优先级）
        if (isMessageFilterBlocked(text)) {
            FileLog.d("wd AI广告过滤器: 过滤消息 " + messageId + " 通过消息过滤器(正则/关键词)");
            putToCache(cacheKey, new FilterResult(true, 1.0f, "user_filter"));
            return true;
        }

        //wd 阶段1 & 2: 主题分析 + 特征覆盖率计算
        try {
            FilterResult result = performFiltering(text, messageId);
            putToCache(cacheKey, result);
            return result.isAd;
        } catch (Exception e) {
            FileLog.e("wd MessageAiAdFilter 过滤错误", e);
            return false;
        }
    }

    //wd 执行过滤判断
    //wd 1. 主题分析模型总结主题
    //wd 2. 特征覆盖率模型计算覆盖率
    //wd 3. 规则引擎辅助计算
    //wd 4. 根据主题类型调整阈值
    //wd 5. 综合得分超过阈值即判定为广告
    private FilterResult performFiltering(String text, long messageId) {
        //wd 使用主题分析器进行分析
        MessageTopicAnalyzer.TopicAnalysis analysis = topicAnalyzer.analyze(text);

        float finalCoverage = analysis.adProbability;  //wd 这里存储的是覆盖率分数
        MessageTopicAnalyzer.TopicType topicType = analysis.topicType;
        StringBuilder reasonBuilder = new StringBuilder();

        //wd 根据主题类型动态调整阈值
        float adjustedThreshold = adjustThresholdByTopicType(coverageThreshold, topicType);

        //wd 判断：覆盖率超过调整后的阈值即判定为广告
        boolean isAd = finalCoverage >= adjustedThreshold;

        if (BuildConfig.DEBUG) {
            FileLog.d("wd AI广告过滤器: 消息 " + messageId +
                " 主题=" + topicType +
                " 覆盖率=" + String.format("%.4f", finalCoverage) +
                " 阈值=" + String.format("%.4f", adjustedThreshold) +
                " 是广告=" + isAd);
        }

        if (isAd) {
            reasonBuilder.append("ad_detected");
            //wd 如果主题类型是白名单但被判定为广告，添加标记
            if (isWhitelistTopic(topicType)) {
                reasonBuilder.append("(whitelist_topic)");
            }
        } else {
            reasonBuilder.append("normal");
        }

        //wd 只在真正拦截时输出日志
        if (isAd) {
            FileLog.d("wd AI广告过滤器: 拦截消息 " + messageId +
                " 主题=" + topicType +
                " 覆盖率=" + String.format("%.4f", finalCoverage) +
                " 阈值=" + String.format("%.4f", adjustedThreshold) +
                " 原因=" + reasonBuilder.toString());
        }

        return new FilterResult(isAd, finalCoverage, reasonBuilder.toString());
    }

    //wd 根据主题类型调整阈值
    //wd 白名单主题提高阈值，避免误判
    private float adjustThresholdByTopicType(float baseThreshold, MessageTopicAnalyzer.TopicType topicType) {
        switch (topicType) {
            case CHAT:      //wd 闲聊
            case GREETING:  //wd 问候
                //wd 大幅提高阈值，这些主题几乎不可能是广告
                return Math.min(1.0f, baseThreshold * 1.5f);
            case CONSULTATION: //wd 咨询
                //wd 适度提高阈值
                return Math.min(1.0f, baseThreshold * 1.3f);
            case NOTIFICATION: //wd 通知
                //wd 轻微提高阈值
                return Math.min(1.0f, baseThreshold * 1.1f);
            case PROMOTION:    //wd 推广
            case TRANSACTION:  //wd 交易
            case RECRUITMENT:  //wd 招聘
                //wd 这些主题可能是广告，保持原阈值
                return baseThreshold;
            default:
                return baseThreshold;
        }
    }

    //wd 判断是否为白名单主题
    private boolean isWhitelistTopic(MessageTopicAnalyzer.TopicType topicType) {
        return topicType == MessageTopicAnalyzer.TopicType.CHAT ||
               topicType == MessageTopicAnalyzer.TopicType.GREETING ||
               topicType == MessageTopicAnalyzer.TopicType.CONSULTATION;
    }

    private void updateMessageFilterCache() {
        String filterText = ConfigManager.getStringOrDefault(Defines.messageFilter, "");
        synchronized (filterConfigLock) {
            if (TextUtils.equals(filterText, messageFilterTextCached)) {
                return;
            }

            Pattern pattern = null;
            ArrayList<String> literalKeywords = null;

            if (!TextUtils.isEmpty(filterText)) {
                try {
                    pattern = Pattern.compile(filterText, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    FileLog.e("wd 无效的消息过滤正则表达式: " + filterText, e);
                }

                String[] filterTokens = filterText.split("\\|");
                for (int i = 0; i < filterTokens.length; i++) {
                    String token = filterTokens[i];
                    if (token == null) {
                        continue;
                    }
                    token = token.trim();
                    if (token.isEmpty()) {
                        continue;
                    }

                    boolean hasRegexMeta = false;
                    for (int c = 0; c < token.length(); c++) {
                        char ch = token.charAt(c);
                        if (ch == '\\' || ch == '.' || ch == '*' || ch == '+' || ch == '?' || ch == '^' || ch == '$' || ch == '[' || ch == ']' || ch == '(' || ch == ')' || ch == '{' || ch == '}') {
                            hasRegexMeta = true;
                            break;
                        }
                    }
                    if (hasRegexMeta) {
                        continue;
                    }

                    String normalizedToken = Normalizer.normalize(token, Normalizer.Form.NFKC);
                    String cleanToken = normalizedToken.replaceAll("[\\p{P}\\p{S}\\p{Z}\\s\\p{Cf}]+", "");
                    if (cleanToken.isEmpty()) {
                        continue;
                    }
                    if (literalKeywords == null) {
                        literalKeywords = new ArrayList<>();
                    }
                    literalKeywords.add(cleanToken);
                }
            }

            messageFilterTextCached = filterText;
            messageFilterPatternCached = pattern;
            messageFilterKeywordsCached = literalKeywords;
        }
    }

    private boolean isMessageFilterBlocked(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        synchronized (filterConfigLock) {
            Pattern pattern = messageFilterPatternCached;
            ArrayList<String> keywords = messageFilterKeywordsCached;
            if (pattern == null && (keywords == null || keywords.isEmpty())) {
                return false;
            }

            String normalizedText = Normalizer.normalize(text, Normalizer.Form.NFKC);
            String cleanText = normalizedText.replaceAll("[\\p{P}\\p{S}\\p{Z}\\s\\p{Cf}]+", "");

            if (pattern != null && pattern.matcher(cleanText).find()) {
                return true;
            }

            if (keywords != null && !keywords.isEmpty()) {
                int totalKeywords = keywords.size();
                int matchedKeywords = 0;

                for (String keyword : keywords) {
                    if (!TextUtils.isEmpty(keyword) && cleanText.contains(keyword)) {
                        matchedKeywords++;
                    }
                }

                float matchRatio = (float) matchedKeywords / totalKeywords;
                int totalLength = cleanText.length();
                int adContentLength = 0;

                for (String keyword : keywords) {
                    if (!TextUtils.isEmpty(keyword) && cleanText.contains(keyword)) {
                        int startIndex = 0;
                        while (true) {
                            int index = cleanText.indexOf(keyword, startIndex);
                            if (index == -1) break;
                            adContentLength += keyword.length();
                            startIndex = index + keyword.length();
                        }
                    }
                }

                float lengthRatio = totalLength > 0 ? (float) adContentLength / totalLength : 0f;

                boolean isMostlyAd = matchRatio >= 0.5f || lengthRatio >= 0.3f;
                if (isMostlyAd) {
                    FileLog.d("wd MessageAiAdFilter: 广告内容比例=" + lengthRatio + ", 关键词比例=" + matchRatio);
                }
                return isMostlyAd;
            }

            return false;
        }
    }

    private String buildCacheKey(long dialogId, long messageId, String text) {
        int hash = text.hashCode();
        return dialogId + CACHE_KEY_SEPARATOR + messageId + CACHE_KEY_SEPARATOR + hash;
    }

    @Nullable
    private FilterResult getFromCache(String cacheKey) {
        synchronized (cacheLock) {
            FilterResult result = cache.get(cacheKey);
            if (result != null && System.currentTimeMillis() - result.timestamp < 3600000) {
                return result;
            }
            return null;
        }
    }

    private void putToCache(String cacheKey, FilterResult result) {
        synchronized (cacheLock) {
            if (cache.size() >= 10000) {
                cache.clear();
            }
            cache.put(cacheKey, result);
        }
    }

    public void clearCache() {
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    public void release() {
        executor.shutdownNow();
        if (topicAnalyzer != null) {
            topicAnalyzer.release();
        }
        clearCache();
    }

    /**
     * 获取最后一次主题分析的详细信息（用于调试）
     */
    public MessageTopicAnalyzer.TopicAnalysis getLastTopicAnalysis(String text) {
        if (topicAnalyzer != null) {
            return topicAnalyzer.analyze(text);
        }
        return null;
    }

    /**
     * 检查模型是否已加载
     */
    public boolean isModelsLoaded() {
        return topicAnalyzer != null &&
               topicAnalyzer.isTopicModelLoaded() &&
               topicAnalyzer.isCoverageModelLoaded();
    }
}
