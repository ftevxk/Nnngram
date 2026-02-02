/*
 * Copyright (C) 2024 Nnngram
 * 广告关键词存储管理类
 * 只存储广告关键词集合
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

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

//wd 广告关键词存储管理类
//wd 只存储广告关键词集合，不存储词频、概率等复杂信息
public class AdKeywordsStore {

    private static volatile AdKeywordsStore instance;

    //wd 广告关键词集合
    private final Set<String> adKeywords;

    //wd 上下文
    private Context context;

    //wd 加载状态
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final Object loadLock = new Object();

    //wd 文件路径
    private static final String KEYWORDS_FILE = "ad_keywords.json";
    private static final String ASSETS_KEYWORDS = "ai_ad_filter/ad_keywords.json";

    //wd 类别常量（保留用于兼容）
    public static final String CLASS_AD = "ad";
    public static final String CLASS_NORMAL = "normal";

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

    //wd 初始化
    public void init(Context context) {
        this.context = context.getApplicationContext();
        if (!isLoaded.get()) {
            loadKeywords();
        }
    }

    //wd 获取存储文件
    private File getStorageFile() {
        File nnngramFilesDir = null;
        try {
            nnngramFilesDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES);
        } catch (Exception e) {
            FileLog.e("wd FileLoader.getDirectory() 失败，使用降级路径", e);
        }
        if (nnngramFilesDir == null) {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                nnngramFilesDir = new File(new File(externalDir, "Nnngram"), "Nnngram Files");
            } else {
                nnngramFilesDir = new File(context.getFilesDir(), "Nnngram Files");
            }
        }
        File aiFilterDir = new File(nnngramFilesDir, "ai_ad_filter");
        if (!aiFilterDir.exists()) {
            aiFilterDir.mkdirs();
        }
        return new File(aiFilterDir, KEYWORDS_FILE);
    }

    //wd 加载关键词
    public void loadKeywords() {
        synchronized (loadLock) {
            if (isLoaded.get()) {
                return;
            }

            try {
                File file = getStorageFile();

                //wd 如果文件不存在，从assets复制
                if (!file.exists()) {
                    copyKeywordsFromAssets(file);
                }

                //wd 从JSON文件加载关键词
                if (file.exists()) {
                    loadKeywordsFromJson(file);
                }

                isLoaded.set(true);
                FileLog.d("wd AdKeywordsStore 关键词加载完成，数量=" + adKeywords.size());
            } catch (Exception e) {
                FileLog.e("wd AdKeywordsStore 加载关键词失败", e);
                //wd 初始化默认关键词
                initializeDefaultKeywords();
            }
        }
    }

    //wd 从JSON文件加载关键词
    private void loadKeywordsFromJson(File file) throws IOException, JSONException {
        StringBuilder jsonBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        reader.close();

        JSONObject root = new JSONObject(jsonBuilder.toString());

        //wd 加载广告关键词
        JSONArray keywordsArray = root.optJSONArray("adKeywords");
        if (keywordsArray != null) {
            for (int i = 0; i < keywordsArray.length(); i++) {
                String keyword = keywordsArray.optString(i);
                if (keyword != null && !keyword.isEmpty()) {
                    adKeywords.add(keyword.toLowerCase());
                }
            }
        }

        //wd 兼容旧格式：从featureCounts.ad加载
        JSONObject featureCounts = root.optJSONObject("featureCounts");
        if (featureCounts != null) {
            JSONObject adCounts = featureCounts.optJSONObject(CLASS_AD);
            if (adCounts != null) {
                Iterator<String> keys = adCounts.keys();
                while (keys.hasNext()) {
                    String keyword = keys.next();
                    if (keyword != null && !keyword.isEmpty()) {
                        adKeywords.add(keyword.toLowerCase());
                    }
                }
                FileLog.d("wd AdKeywordsStore 从旧格式加载了 " + adKeywords.size() + " 个关键词");
            }
        }
    }

    //wd 从assets复制默认关键词
    private boolean copyKeywordsFromAssets(File destFile) {
        try {
            InputStream is = context.getAssets().open(ASSETS_KEYWORDS);
            FileWriter writer = new FileWriter(destFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }

            reader.close();
            writer.close();
            FileLog.d("wd AdKeywordsStore 已从assets复制默认关键词");
            return true;
        } catch (IOException e) {
            FileLog.w("wd AdKeywordsStore assets中无默认关键词文件，将初始化默认关键词");
            return false;
        }
    }

    //wd 初始化默认关键词
    private void initializeDefaultKeywords() {
        FileLog.d("wd AdKeywordsStore 初始化默认关键词");

        //wd 添加一些常见的广告关键词
        String[] defaultKeywords = {
            "赌博", "博彩", "赌球", "彩票", "六合彩",
            "色情", "黄片", "av", "porn", "成人",
            "刷单", "兼职", "赚钱", "返利", "投资",
            "贷款", "借款", "信用卡", "套现", "黑户",
            "代考", "办证", "学历", "证书", "假证",
            "毒品", "冰毒", "大麻", "吸毒", "贩毒",
            "枪支", "弹药", "武器", "爆炸物", "违禁品",
            "诈骗", "骗子", "传销", "非法集资", "洗钱"
        };

        for (String keyword : defaultKeywords) {
            adKeywords.add(keyword);
        }

        saveKeywords();
        isLoaded.set(true);
        FileLog.d("wd AdKeywordsStore 默认关键词初始化完成，数量=" + adKeywords.size());
    }

    //wd 保存关键词到文件
    public boolean saveKeywords() {
        synchronized (loadLock) {
            try {
                JSONObject root = new JSONObject();

                //wd 保存广告关键词数组
                JSONArray keywordsArray = new JSONArray();
                for (String keyword : adKeywords) {
                    keywordsArray.put(keyword);
                }
                root.put("adKeywords", keywordsArray);
                root.put("version", 2);
                root.put("lastUpdate", System.currentTimeMillis());

                //wd 写入文件
                File file = getStorageFile();
                FileWriter writer = new FileWriter(file);
                writer.write(root.toString(2));
                writer.close();

                FileLog.d("wd AdKeywordsStore 关键词已保存，数量=" + adKeywords.size());
                return true;
            } catch (Exception e) {
                FileLog.e("wd AdKeywordsStore 保存关键词失败", e);
                return false;
            }
        }
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
        }
        return added;
    }

    //wd 移除广告关键词
    public boolean removeAdKeyword(@NonNull String keyword) {
        String lowerKeyword = keyword.toLowerCase().trim();
        boolean removed = adKeywords.remove(lowerKeyword);
        if (removed) {
            FileLog.d("wd AdKeywordsStore 移除关键词: " + lowerKeyword);
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
        FileLog.d("wd AdKeywordsStore 清空所有关键词");
    }

    //wd 检查是否包含关键词
    public boolean containsKeyword(@NonNull String keyword) {
        return adKeywords.contains(keyword.toLowerCase().trim());
    }

    //wd 检查是否已加载
    public boolean isLoaded() {
        return isLoaded.get();
    }

    //wd 重新加载关键词
    public void reloadKeywords() {
        synchronized (loadLock) {
            isLoaded.set(false);
            adKeywords.clear();
            loadKeywords();
        }
    }

    // ========== 以下方法为兼容旧版本保留 ==========

    //wd 获取某类所有特征及其词频（兼容方法，返回空Map）
    @NonNull
    @Deprecated
    public Map<String, Integer> getFeaturesByClass(String className) {
        if (CLASS_AD.equals(className)) {
            Map<String, Integer> result = new ConcurrentHashMap<>();
            for (String keyword : adKeywords) {
                result.put(keyword, 1);
            }
            return result;
        }
        return new ConcurrentHashMap<>();
    }

    //wd 设置特征词频（兼容方法，实际只添加关键词）
    @Deprecated
    public void setFeatureCount(String feature, String className, int count) {
        if (CLASS_AD.equals(className) && count > 0) {
            addAdKeyword(feature);
        }
    }

    //wd 更新特征词频（兼容方法，实际只添加关键词）
    @Deprecated
    public void updateFeatureCount(String feature, String className, int delta) {
        if (CLASS_AD.equals(className) && delta > 0) {
            addAdKeyword(feature);
        }
    }

    //wd 移除特征（兼容方法）
    @Deprecated
    public boolean removeFeature(String feature, String className) {
        if (CLASS_AD.equals(className)) {
            return removeAdKeyword(feature);
        }
        return false;
    }

    //wd 清空某类所有特征（兼容方法）
    @Deprecated
    public void clearClassFeatures(String className) {
        if (CLASS_AD.equals(className)) {
            clearAdKeywords();
        }
    }

    //wd 获取先验概率（兼容方法，返回固定值）
    @Deprecated
    public double getPriorProbability(String className) {
        return CLASS_AD.equals(className) ? 0.5 : 0.5;
    }

    //wd 获取条件概率（兼容方法，返回固定值）
    @Deprecated
    public double getConditionalProbability(String feature, String className) {
        if (CLASS_AD.equals(className)) {
            return adKeywords.contains(feature.toLowerCase()) ? 0.8 : 0.1;
        }
        return 0.5;
    }

    //wd 获取特征词频（兼容方法）
    @Deprecated
    public int getFeatureCount(String feature, String className) {
        if (CLASS_AD.equals(className)) {
            return adKeywords.contains(feature.toLowerCase()) ? 1 : 0;
        }
        return 0;
    }

    //wd 获取类别总词数（兼容方法）
    @Deprecated
    public int getClassTotalCount(String className) {
        return CLASS_AD.equals(className) ? adKeywords.size() : 0;
    }

    //wd 获取词汇表大小（兼容方法）
    @Deprecated
    public int getVocabularySize() {
        return adKeywords.size();
    }

    //wd 重新加载概率表（兼容方法）
    @Deprecated
    public void reloadProbabilities() {
        reloadKeywords();
    }

    //wd 获取平滑参数（兼容方法）
    @Deprecated
    public double getSmoothingAlpha() {
        return 1.0;
    }

    //wd 保存概率（兼容方法）
    @Deprecated
    public boolean saveProbabilities() {
        return saveKeywords();
    }
}
