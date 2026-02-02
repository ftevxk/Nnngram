/*
 * Copyright (C) 2024 Nnngram
 * AI 广告消息过滤器 - 关键词特征库架构
 * 新架构：
 * 步骤1: AI模型提取内容多个关键词及关键词频次
 * 步骤2: AI模型对内容的多个关键词进行整合判断是否为广告内容
 * 步骤3: 结合AI广告关键词特征库处理
 * 步骤4: 用户可通过长按提取广告关键词并加入特征库
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import xyz.nextalone.nnngram.config.ConfigManager;
import xyz.nextalone.nnngram.utils.Defines;

//wd AI广告消息过滤器
//wd 基于关键词特征库的新架构
public class MessageAiAdFilter {
    private static volatile MessageAiAdFilter instance;
    private final Context context;
    private AiAdContentAnalyzer contentAnalyzer;
    private final Map<String, FilterResult> cache = new HashMap<>();
    private final Object cacheLock = new Object();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    //wd 配置参数
    private float threshold = 0.5f;  //wd AI广告判定阈值

    private static final String CACHE_KEY_SEPARATOR = "_";

    //wd 过滤结果类
    private static class FilterResult {
        final boolean isAd;
        final float score;
        final String reason;
        final long timestamp;

        FilterResult(boolean isAd, float score, String reason) {
            this.isAd = isAd;
            this.score = score;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private MessageAiAdFilter(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
        this.contentAnalyzer = AiAdContentAnalyzer.getInstance();
        this.contentAnalyzer.init(context);
    }

    //wd 加载配置 - 使用固定阈值0.65，移除可配置选项
    private void loadConfig() {
        threshold = 0.65f;
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
     * 初始化 AI 广告过滤器
     */
    public void initialize() {
        if (isInitialized.get()) {
            FileLog.d("wd MessageAiAdFilter 已初始化");
            return;
        }

        executor.execute(() -> {
            try {
                FileLog.d("wd MessageAiAdFilter: 开始初始化");

                //wd 初始化内容分析器
                if (contentAnalyzer != null) {
                    contentAnalyzer.init(context);
                }

                isInitialized.set(true);
                FileLog.d("wd MessageAiAdFilter: 初始化完成");
            } catch (Exception e) {
                FileLog.e("wd MessageAiAdFilter 初始化错误", e);
            }
        });
    }

    //wd 设置AI广告判定阈值 - 已移除配置选项，此方法保留但仅更新内存值
    public void setThreshold(float threshold) {
        this.threshold = 0.65f; //wd 固定阈值，忽略传入值
        if (contentAnalyzer != null) {
            contentAnalyzer.setThreshold(this.threshold);
        }
        clearCache();
    }

    //wd 获取AI广告判定阈值
    public float getThreshold() {
        return threshold;
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
            FileLog.d("wd AI广告过滤器: 已禁用，跳过消息 " + truncateText(text));
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            FileLog.d("wd AI广告过滤器: 空文本，跳过消息 " + messageId);
            return false;
        }

        String cacheKey = buildCacheKey(dialogId, messageId, text);
        FilterResult cached = getFromCache(cacheKey);
        if (cached != null) {
            //wd 缓存命中，直接返回结果
            return cached.isAd;
        }

        //wd 执行过滤判断
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
    //wd 步骤1: AI模型提取关键词及频次
    //wd 步骤2: 与特征库比对打分
    //wd 步骤3: 频次加权计算最终得分
    //wd 步骤4: 超过阈值判定为广告
    private FilterResult performFiltering(String text, long messageId) {
        //wd 使用内容分析器进行分析
        AiAdContentAnalyzer.AnalysisResult analysisResult = contentAnalyzer.analyze(text);

        boolean isAd = analysisResult.isAd;
        float score = analysisResult.score;

        //wd 调试日志
        if (BuildConfig.DEBUG) {
            FileLog.d("wd AI广告过滤器: 消息 " + truncateText(text) +
                    " 得分=" + String.format("%.4f", score) +
                    " 阈值=" + String.format("%.4f", threshold) +
                    " 是广告=" + isAd +
                    " 匹配关键词数=" + analysisResult.matchedKeywords.size());
        }

        //wd 只在真正拦截时输出日志
        if (isAd) {
            FileLog.d("wd AI广告过滤器: 拦截消息 " + truncateText(text) +
                    " 得分=" + String.format("%.4f", score) +
                    " 原因=" + analysisResult.reason);
        }

        return new FilterResult(isAd, score, analysisResult.reason);
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

    //wd 刷新过滤器配置
    public void refreshFilterConfig() {
        loadConfig();
        if (contentAnalyzer != null) {
            contentAnalyzer.setThreshold(threshold);
        }
        clearCache();
        FileLog.d("wd MessageAiAdFilter: 配置已刷新");
    }

    public void release() {
        executor.shutdownNow();
        if (contentAnalyzer != null) {
            //wd 释放资源
        }
        clearCache();
    }

    //wd 截断文本用于日志输出
    private String truncateText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 50) {
            return trimmed;
        }
        return trimmed.substring(0, 50) + "...";
    }

    /**
     * 获取最后一次分析的详细信息（用于调试）
     */
    public AiAdContentAnalyzer.AnalysisResult getLastAnalysis(String text) {
        if (contentAnalyzer != null) {
            return contentAnalyzer.analyze(text);
        }
        return null;
    }

    /**
     * 检查分析器是否已就绪
     */
    public boolean isAnalyzerReady() {
        return contentAnalyzer != null && contentAnalyzer.isReady();
    }
}
