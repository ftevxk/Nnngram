/*
 * Copyright (C) 2024 Nnngram
 * 广告消息过滤器 - 关键词匹配架构
 * 命中2个及以上广告关键词即判定为广告
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
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import xyz.nextalone.nnngram.config.ConfigManager;
import xyz.nextalone.nnngram.utils.Defines;

//wd 广告消息过滤器
//wd 基于关键词匹配的简单架构，命中2个及以上关键词即判定为广告
public class AdFilter {
    private static volatile AdFilter instance;
    private final Context context;
    private final Map<String, FilterResult> cache = new HashMap<>();
    private final Object cacheLock = new Object();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    //wd 广告关键词集合
    private Set<String> adKeywords = new HashSet<>();

    //wd 匹配阈值：命中2个及以上关键词判定为广告
    private static final int MATCH_THRESHOLD = 2;

    private static final String CACHE_KEY_SEPARATOR = "_";

    //wd 过滤结果类
    private static class FilterResult {
        final boolean isAd;
        final int matchCount;
        final Set<String> matchedKeywords;
        final long timestamp;

        FilterResult(boolean isAd, int matchCount, Set<String> matchedKeywords) {
            this.isAd = isAd;
            this.matchCount = matchCount;
            this.matchedKeywords = matchedKeywords;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private AdFilter(Context context) {
        this.context = context.getApplicationContext();
        //wd 确保 AdKeywordsStore 已初始化
        AdKeywordsStore store = AdKeywordsStore.getInstance();
        if (store != null) {
            store.init(this.context);
        }
        loadKeywords();
    }

    public static AdFilter getInstance(Context context) {
        if (instance == null) {
            synchronized (AdFilter.class) {
                if (instance == null) {
                    instance = new AdFilter(context);
                }
            }
        }
        return instance;
    }

    public static AdFilter getInstance() {
        return instance;
    }

    /**
     * 初始化广告过滤器
     */
    public void initialize() {
        if (isInitialized.get()) {
            FileLog.d("wd AdFilter 已初始化");
            return;
        }

        executor.execute(() -> {
            try {
                FileLog.d("wd AdFilter: 开始初始化");
                loadKeywords();
                isInitialized.set(true);
                FileLog.d("wd AdFilter: 初始化完成，关键词数量=" + adKeywords.size());
            } catch (Exception e) {
                FileLog.e("wd AdFilter 初始化错误", e);
            }
        });
    }

    //wd 加载关键词
    private void loadKeywords() {
        AdKeywordsStore store = AdKeywordsStore.getInstance();
        adKeywords = store.getAdKeywords();
        FileLog.d("wd AdFilter 加载关键词，数量=" + adKeywords.size());
    }

    public boolean isEnabled() {
        return ConfigManager.getBooleanOrDefault(Defines.adFilterEnabled, false);
    }

    public boolean shouldFilter(@NonNull MessageObject messageObject) {
        //wd 使用会话级别配置检查是否启用广告过滤
        if (!shouldFilterForChat(messageObject.getDialogId())) {
            return false;
        }
        String text = getMessageText(messageObject);
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return shouldFilter(text, messageObject.getDialogId(), messageObject.getId());
    }

    //wd 获取消息文本
    private String getMessageText(@NonNull MessageObject messageObject) {
        return MessageTextExtractor.getInstance().extractAllText(messageObject);
    }

    public boolean shouldFilter(@NonNull String text, long dialogId, long messageId) {
        //wd 使用会话级别配置检查是否启用广告过滤
        if (!shouldFilterForChat(dialogId)) {
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        String cacheKey = buildCacheKey(dialogId, messageId, text);
        FilterResult cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached.isAd;
        }

        try {
            FilterResult result = performFiltering(text);
            putToCache(cacheKey, result);
            return result.isAd;
        } catch (Exception e) {
            FileLog.e("wd AdFilter 过滤错误", e);
            return false;
        }
    }

    //wd 执行过滤判断 - 关键词匹配
    private FilterResult performFiltering(String text) {
        //wd 每次都从存储获取最新关键词
        AdKeywordsStore store = AdKeywordsStore.getInstance();
        Set<String> currentKeywords = store.getAdKeywords();

        //wd 将文本转为小写进行匹配
        String lowerText = text.toLowerCase();
        Set<String> matchedKeywords = new HashSet<>();
        Map<String, Integer> keywordCountMap = new HashMap<>();

        //wd 统计匹配的关键词数量和每个关键词的出现次数
        for (String keyword : currentKeywords) {
            String lowerKeyword = keyword.toLowerCase();
            int count = countOccurrences(lowerText, lowerKeyword);
            if (count > 0) {
                matchedKeywords.add(keyword);
                keywordCountMap.put(keyword, count);
            }
        }

        //wd 获取配置阈值
        int multiKeywordThreshold = ConfigManager.getIntOrDefault(Defines.adFilterMultiKeywordThreshold, 2);
        int repeatKeywordThreshold = ConfigManager.getIntOrDefault(Defines.adFilterRepeatKeywordThreshold, 3);

        int matchCount = matchedKeywords.size();
        int maxRepeatCount = 0;
        for (int count : keywordCountMap.values()) {
            if (count > maxRepeatCount) {
                maxRepeatCount = count;
            }
        }

        //wd 任一条件满足即判定为广告
        boolean isAd = matchCount >= multiKeywordThreshold || maxRepeatCount >= repeatKeywordThreshold;

        //wd 只在真正拦截时输出日志
        if (isAd) {
            FileLog.d("wd 广告过滤器: 拦截消息 " + truncateText(text) +
                    " 匹配关键词数=" + matchCount + " 最大重复次数=" + maxRepeatCount +
                    " 关键词=" + matchedKeywords);
        }

        return new FilterResult(isAd, matchCount, matchedKeywords);
    }

    //wd 统计关键词在文本中出现次数
    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
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
        AdKeywordsStore store = AdKeywordsStore.getInstance();
        store.reloadKeywords();
        loadKeywords();
        clearCache();
        FileLog.d("wd AdFilter: 配置已刷新，关键词数量=" + adKeywords.size());
    }

    public void release() {
        executor.shutdownNow();
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
     * 添加广告关键词
     * @param keyword 要添加的关键词
     * @return 是否添加成功
     */
    public boolean addAdKeyword(@NonNull String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return false;
        }

        String lowerKeyword = keyword.trim().toLowerCase();
        if (lowerKeyword.isEmpty()) {
            return false;
        }

        //wd 添加到关键词存储
        AdKeywordsStore store = AdKeywordsStore.getInstance();
        store.addAdKeyword(lowerKeyword);
        store.saveKeywords();

        //wd 刷新本地缓存
        loadKeywords();
        clearCache();

        FileLog.d("wd AdFilter: 添加广告关键词 " + lowerKeyword);
        return true;
    }

    /**
     * 批量添加广告关键词
     * @param keywords 要添加的关键词列表
     * @return 添加成功的数量
     */
    public int addAdKeywords(@NonNull Set<String> keywords) {
        int addedCount = 0;
        for (String keyword : keywords) {
            if (addAdKeyword(keyword)) {
                addedCount++;
            }
        }
        return addedCount;
    }

    /**
     * 获取当前广告关键词数量
     */
    public int getAdKeywordCount() {
        return adKeywords.size();
    }

    /**
     * 获取广告关键词集合
     */
    public Set<String> getAdKeywords() {
        return new HashSet<>(adKeywords);
    }

    /**
     * 检查过滤器是否已就绪
     */
    public boolean isReady() {
        return isInitialized.get();
    }

    //wd 获取会话广告过滤配置键
    private String getChatAdFilterKey(long dialogId) {
        return Defines.adFilterChatPrefix + dialogId;
    }

    //wd 获取SharedPreferences
    private SharedPreferences getPrefs() {
        if (context == null) {
            FileLog.e("wd AdFilter context为null");
            return null;
        }
        return context.getSharedPreferences("ad_filter_prefs", Context.MODE_PRIVATE);
    }

    /**
     * 检查指定会话是否启用了广告过滤
     * wd 如果全局广告过滤开启，默认返回true
     * wd 如果全局关闭，检查会话级别配置
     */
    public boolean isChatAdFilterEnabled(long dialogId) {
        //wd 如果全局广告过滤已开启，默认启用
        if (isEnabled()) {
            return true;
        }
        //wd 否则检查会话级别配置
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return false;
        }
        return prefs.getBoolean(getChatAdFilterKey(dialogId), false);
    }

    /**
     * 设置指定会话的广告过滤状态
     * wd 持久化存储到SharedPreferences
     */
    public void setChatAdFilterEnabled(long dialogId, boolean enabled) {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            FileLog.e("wd AdFilter 无法获取SharedPreferences");
            return;
        }
        prefs.edit().putBoolean(getChatAdFilterKey(dialogId), enabled).apply();
        FileLog.d("wd AdFilter 设置会话 " + dialogId + " 广告过滤状态: " + enabled);
    }

    /**
     * 检查是否应该过滤指定会话的消息
     * wd 优先检查会话级别配置，如果没有则使用全局配置
     */
    public boolean shouldFilterForChat(long dialogId) {
        //wd 如果全局广告过滤已开启，直接返回true
        if (isEnabled()) {
            return true;
        }
        //wd 否则检查会话级别配置
        return isChatAdFilterEnabled(dialogId);
    }
}
