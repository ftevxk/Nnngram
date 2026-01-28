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
    private boolean topicAnalysisEnabled = true;
    private float topicWeight = 0.7f;
    private float adModelWeight = 0.3f;

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
        topicAnalysisEnabled = ConfigManager.getBooleanOrDefault(Defines.aiAdFilterTopicAnalysisEnabled, true);
        topicWeight = ConfigManager.getFloatOrDefault(Defines.aiAdFilterTopicWeight, 0.7f);
        adModelWeight = ConfigManager.getFloatOrDefault(Defines.aiAdFilterModelWeight, 0.3f);
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

    public void setTopicAnalysisEnabled(boolean enabled) {
        this.topicAnalysisEnabled = enabled;
        ConfigManager.putBoolean(Defines.aiAdFilterTopicAnalysisEnabled, enabled);
        clearCache();
    }

    public boolean isTopicAnalysisEnabled() {
        return topicAnalysisEnabled;
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
            FileLog.d("wd AI ad filter: cache hit for message " + messageId + ", isAd=" + cached.isAd + ", reason=" + cached.reason);
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

            if (result.isAd) {
                FileLog.d("wd AI ad filter: FILTERED message " + messageId + " with confidence " +
                    String.format("%.4f", result.confidence) + ", reason=" + result.reason);
            }

            return result.isAd;
        } catch (Exception e) {
            FileLog.e("wd MessageAiAdFilter filtering error", e);
            return false;
        }
    }

    private FilterResult performTwoStageFiltering(String text, long messageId) {
        // 使用主题分析器进行两阶段分析
        MessageTopicAnalyzer.TopicAnalysis analysis = topicAnalyzer.analyze(text);
        
        float finalScore = 0f;
        StringBuilder reasonBuilder = new StringBuilder();

        FileLog.d("wd AI ad filter: two-stage analysis for message " + messageId +
            " - topic=" + analysis.topicType +
            ", intent=" + analysis.intentType +
            ", adProb=" + String.format("%.4f", analysis.adProbability) +
            ", fromTopicModel=" + analysis.isFromTopicModel +
            ", fromAdModel=" + analysis.isFromAdModel);

        // 根据主题类型快速判断
        if (analysis.topicType == MessageTopicAnalyzer.TopicType.CHAT ||
            analysis.topicType == MessageTopicAnalyzer.TopicType.CONSULTATION) {
            // 闲聊或咨询类消息，大幅降低广告概率
            if (analysis.adProbability < 0.3f) {
                return new FilterResult(false, analysis.adProbability, "topic_chat_or_consultation");
            }
        }

        // 计算最终分数
        if (topicAnalysisEnabled) {
            // 主题分析为主
            finalScore = analysis.adProbability;
            
            // 如果主题模型和广告模型都认为是广告，增加置信度
            if (analysis.isFromTopicModel && analysis.isFromAdModel && analysis.adProbability > 0.5f) {
                finalScore = Math.min(1.0f, finalScore + 0.1f);
                reasonBuilder.append("dual_model_agreement;");
            }

            // 如果主题分析强烈认为是正常内容，降低分数
            if (analysis.topicType == MessageTopicAnalyzer.TopicType.CHAT ||
                analysis.topicType == MessageTopicAnalyzer.TopicType.CONSULTATION) {
                if (analysis.adProbability < 0.2f) {
                    finalScore = finalScore * 0.5f;
                    reasonBuilder.append("topic_normal_override;");
                }
            }
        } else {
            // 禁用主题分析，只用广告模型结果
            finalScore = analysis.adProbability;
        }

        // 严格模式调整
        boolean isAd = finalScore >= threshold;
        if (strictMode && finalScore >= threshold * 0.75f) {
            isAd = true;
            reasonBuilder.append("strict_mode;");
        }

        if (reasonBuilder.length() == 0) {
            reasonBuilder.append("normal_threshold");
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
