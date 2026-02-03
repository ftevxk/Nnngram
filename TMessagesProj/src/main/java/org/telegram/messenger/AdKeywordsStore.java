/*
 * Copyright (C) 2024 Nnngram
 * 广告关键词存储管理类
 * 使用SharedPreferences存储关键词集合
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

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//wd 广告关键词存储管理类
//wd 使用SharedPreferences存储关键词集合
public class AdKeywordsStore {

    private static volatile AdKeywordsStore instance;

    //wd SharedPreferences名称和键
    private static final String PREFS_NAME = "ad_filter_prefs";
    private static final String KEY_AD_KEYWORDS = "ad_keywords";

    //wd 广告关键词集合
    private final Set<String> adKeywords;

    //wd 上下文
    private Context context;

    //wd 类别常量
    public static final String CLASS_AD = "ad";

    //wd 私有构造函数
    private AdKeywordsStore() {
        adKeywords = ConcurrentHashMap.newKeySet();
    }

    //wd 获取单例实例
    public static AdKeywordsStore getInstance() {
        if (instance == null) {
            synchronized (AdKeywordsStore.class) {
                if (instance == null) {
                    instance = new AdKeywordsStore();
                }
            }
        }
        return instance;
    }

    //wd 获取SharedPreferences
    private SharedPreferences getPrefs() {
        if (context == null) {
            FileLog.e("wd AdKeywordsStore context为null");
            return null;
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    //wd 初始化
    public void init(Context context) {
        this.context = context.getApplicationContext();
        loadKeywords();
    }

    //wd 加载关键词
    public void loadKeywords() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            FileLog.e("wd AdKeywordsStore 无法获取SharedPreferences");
            return;
        }

        Set<String> savedKeywords = prefs.getStringSet(KEY_AD_KEYWORDS, null);
        adKeywords.clear();
        if (savedKeywords != null) {
            for (String keyword : savedKeywords) {
                if (keyword != null && !keyword.isEmpty()) {
                    adKeywords.add(keyword.toLowerCase());
                }
            }
        }
        FileLog.d("wd AdKeywordsStore 关键词加载完成，数量=" + adKeywords.size());
    }

    //wd 保存关键词
    public boolean saveKeywords() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            FileLog.e("wd AdKeywordsStore 无法获取SharedPreferences");
            return false;
        }

        Set<String> keywordsToSave = new HashSet<>(adKeywords);
        boolean success = prefs.edit().putStringSet(KEY_AD_KEYWORDS, keywordsToSave).commit();
        if (success) {
            FileLog.d("wd AdKeywordsStore 关键词已保存，数量=" + adKeywords.size());
        } else {
            FileLog.e("wd AdKeywordsStore 保存关键词失败");
        }
        return success;
    }

    //wd 添加广告关键词
    public boolean addAdKeyword(@NonNull String keyword) {
        if (keyword.isEmpty()) {
            return false;
        }

        String lowerKeyword = keyword.toLowerCase().trim();
        if (lowerKeyword.isEmpty()) {
            return false;
        }

        boolean added = adKeywords.add(lowerKeyword);
        if (added) {
            FileLog.d("wd AdKeywordsStore 添加关键词: " + lowerKeyword);
            saveKeywords();
        }
        return added;
    }

    //wd 移除广告关键词
    public boolean removeAdKeyword(@NonNull String keyword) {
        String lowerKeyword = keyword.toLowerCase().trim();
        boolean removed = adKeywords.remove(lowerKeyword);
        if (removed) {
            FileLog.d("wd AdKeywordsStore 移除关键词: " + lowerKeyword);
            saveKeywords();
        }
        return removed;
    }

    //wd 获取广告关键词集合
    @NonNull
    public Set<String> getAdKeywords() {
        return new HashSet<>(adKeywords);
    }

    //wd 获取广告关键词数量
    public int getAdKeywordCount() {
        return adKeywords.size();
    }

    //wd 清空所有关键词
    public void clearAdKeywords() {
        adKeywords.clear();
        saveKeywords();
        FileLog.d("wd AdKeywordsStore 清空所有关键词");
    }

    //wd 检查是否包含关键词
    public boolean containsKeyword(@NonNull String keyword) {
        return adKeywords.contains(keyword.toLowerCase().trim());
    }

    //wd 重新加载关键词
    public void reloadKeywords() {
        loadKeywords();
    }
}
