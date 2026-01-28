/*
 * Copyright (C) 2024 Nnngram
 * AI 广告消息过滤器 - 两阶段模型架构
 * 阶段1: 主题分析模型 - 理解消息主题和意图
 * 阶段2: 广告分类模型 - 判断是否为广告
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    // 配置参数
    private float threshold = 0.75f;
    private boolean strictMode = false;
    // 主题分析始终启用，作为AI广告过滤的必要步骤
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

    private void loadConfig() {
        threshold = ConfigManager.getFloatOrDefault(Defines.aiAdFilterThreshold, 0.75f);
        threshold = Math.max(0f, Math.min(1f, threshold));
        strictMode = ConfigManager.getBooleanOrDefault(Defines.aiAdFilterStrictMode, false);
        // 主题分析始终启用，不需要从配置读取
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
            FileLog.d("wd MessageAiAdFilter already initialized");
            return;
        }
        
        executor.execute(() -> {
            try {
                FileLog.d("wd MessageAiAdFilter: starting initialization");
                
                // 初始化主题分析器（加载模型）
                if (topicAnalyzer != null) {
                    topicAnalyzer.init(context);
                }
                
                isInitialized.set(true);
                FileLog.d("wd MessageAiAdFilter: initialization completed");
            } catch (Exception e) {
                FileLog.e("wd MessageAiAdFilter initialization error", e);
            }
        });
    }

    public void setThreshold(float threshold) {
        this.threshold = Math.max(0f, Math.min(1f, threshold));
        ConfigManager.putFloat(Defines.aiAdFilterThreshold, this.threshold);
        clearCache();
    }

    public float getThreshold() {
        return threshold;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
        ConfigManager.putBoolean(Defines.aiAdFilterStrictMode, strictMode);
        clearCache();
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    //wd 主题分析始终启用，此方法仅用于兼容性
    public void setTopicAnalysisEnabled(boolean enabled) {
        //wd 主题分析始终启用，忽略设置
        FileLog.d("wd 主题分析始终启用，忽略设置");
    }

    //wd 主题分析始终启用
    public boolean isTopicAnalysisEnabled() {
        return true;
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
            FileLog.d("wd AI ad filter: disabled, skipping message " + messageId);
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            FileLog.d("wd AI ad filter: empty text, skipping message " + messageId);
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
            FileLog.d("wd AI ad filter: FILTERED message " + messageId + " by message filter (regex/keywords)");
            putToCache(cacheKey, new FilterResult(true, 1.0f, "user_filter"));
            return true;
        }

        // 阶段1 & 2: 两阶段模型过滤
        try {
            FilterResult result = performTwoStageFiltering(text, messageId);
            putToCache(cacheKey, result);
            return result.isAd;
        } catch (Exception e) {
            FileLog.e("wd MessageAiAdFilter filtering error", e);
            return false;
        }
    }

    private FilterResult performTwoStageFiltering(String text, long messageId) {
        // 使用主题分析器进行两阶段分析
        MessageTopicAnalyzer.TopicAnalysis analysis = topicAnalyzer.analyze(text);
        
        float finalScore = analysis.adProbability;
        StringBuilder reasonBuilder = new StringBuilder();

        // 只在详细日志模式下输出分析结果
        if (BuildConfig.DEBUG) {
            FileLog.d("wd AI ad filter: analysis for message " + messageId +
                " - topic=" + analysis.topicType +
                ", intent=" + analysis.intentType +
                ", adProb=" + String.format("%.4f", analysis.adProbability));
        }

        // 1. 首先检查主题类型 - 闲聊和咨询类消息通常不是广告
        if (analysis.topicType == MessageTopicAnalyzer.TopicType.CHAT ||
            analysis.topicType == MessageTopicAnalyzer.TopicType.CONSULTATION) {
            // 对于闲聊/咨询类，需要更高的广告概率阈值
            if (analysis.adProbability < 0.6f) {
                return new FilterResult(false, analysis.adProbability, "normal_topic");
            }
            // 即使是闲聊，如果广告概率很高，仍然可能是广告
            finalScore = analysis.adProbability * 0.9f;
        }
        
        // 2. 推广、交易、招聘类主题更可能是广告
        else if (analysis.topicType == MessageTopicAnalyzer.TopicType.PROMOTION ||
                 analysis.topicType == MessageTopicAnalyzer.TopicType.TRANSACTION ||
                 analysis.topicType == MessageTopicAnalyzer.TopicType.RECRUITMENT) {
            // 这些主题类型，稍微降低阈值
            if (analysis.adProbability > 0.4f) {
                finalScore = Math.min(1.0f, analysis.adProbability * 1.1f);
                reasonBuilder.append("suspicious_topic;");
            }
        }

        // 3. 检查意图类型
        if (analysis.intentType == MessageTopicAnalyzer.IntentType.OFFER ||
            analysis.intentType == MessageTopicAnalyzer.IntentType.URGE) {
            // 提供/催促意图增加广告可能性
            if (analysis.adProbability > 0.3f) {
                finalScore = Math.min(1.0f, finalScore * 1.05f);
            }
        }

        // 4. 模型置信度检查
        if (analysis.isFromTopicModel && analysis.isFromAdModel) {
            // 两个模型都有输出，增加可信度
            if (analysis.adProbability > 0.7f) {
                reasonBuilder.append("dual_model_high_confidence;");
            }
        } else if (!analysis.isFromAdModel) {
            // 广告模型没有输出，依赖主题分析结果，提高阈值
            finalScore = finalScore * 0.8f;
            reasonBuilder.append("fallback_to_topic;");
        }

        // 5. 计算最终判断
        boolean isAd = finalScore >= threshold;
        
        // 严格模式：降低阈值，但不过分敏感
        if (strictMode && !isAd && finalScore >= threshold * 0.85f) {
            isAd = true;
            reasonBuilder.append("strict_mode;");
        }

        // 6. 防止过度过滤 - 对于明显正常的消息，即使分数略高也不过滤
        if (isAd && analysis.topicType == MessageTopicAnalyzer.TopicType.CHAT && 
            analysis.adProbability < 0.5f) {
            isAd = false;
            reasonBuilder.append("chat_safety_override;");
        }

        if (reasonBuilder.length() == 0) {
            reasonBuilder.append(isAd ? "ad_detected" : "normal");
        }

        // 只在真正拦截时输出日志，避免循环打印
        if (isAd) {
            FileLog.d("wd AI ad filter: BLOCKED message " + messageId + 
                " score=" + String.format("%.4f", finalScore) + 
                " reason=" + reasonBuilder.toString());
        }

        return new FilterResult(isAd, finalScore, reasonBuilder.toString());
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
                    FileLog.d("wd MessageAiAdFilter: ad content ratio=" + lengthRatio + ", keyword ratio=" + matchRatio);
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
               topicAnalyzer.isAdModelLoaded();
    }
}
