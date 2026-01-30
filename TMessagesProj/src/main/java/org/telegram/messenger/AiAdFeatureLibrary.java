/*
 * Copyright (C) 2024 Nnngram
 * AI广告关键词特征库管理类
 * 管理广告关键词特征的增删改查
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
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

//wd AI广告关键词特征库管理类
//wd 负责特征库的加载、保存、查询和更新
public class AiAdFeatureLibrary {

    private static volatile AiAdFeatureLibrary instance;

    //wd 特征库存储，使用线程安全的ConcurrentHashMap
    private final Map<String, AiAdKeywordFeature> featureMap;
    private final List<AiAdKeywordFeature> featureList;

    //wd 文件路径
    private static final String FEATURE_LIBRARY_FILE = "ai_ad_filter/ad_feature_library.txt";
    private static final String ASSETS_FEATURE_LIBRARY = "ai_ad_filter/ad_feature_library.txt";

    //wd 加载状态
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final Object loadLock = new Object();

    //wd 上下文
    private Context context;

    //wd 私有构造函数
    private AiAdFeatureLibrary() {
        featureMap = new ConcurrentHashMap<>();
        featureList = new CopyOnWriteArrayList<>();
    }

    //wd 获取单例实例
    public static AiAdFeatureLibrary getInstance() {
        if (instance == null) {
            synchronized (AiAdFeatureLibrary.class) {
                if (instance == null) {
                    instance = new AiAdFeatureLibrary();
                }
            }
        }
        return instance;
    }

    //wd 初始化特征库
    public void init(Context context) {
        this.context = context.getApplicationContext();
        if (!isLoaded.get()) {
            loadFeatures();
        }
    }

    //wd 获取特征库文件路径
    private File getFeatureLibraryFile() {
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
        return new File(aiFilterDir, "ad_feature_library.txt");
    }

    //wd 加载特征库
    public void loadFeatures() {
        synchronized (loadLock) {
            if (isLoaded.get()) {
                return;
            }

            try {
                File featureFile = getFeatureLibraryFile();

                //wd 如果文件不存在，从assets复制
                if (!featureFile.exists()) {
                    copyFeaturesFromAssets(featureFile);
                }

                //wd 读取特征库文件
                if (featureFile.exists()) {
                    loadFeaturesFromFile(featureFile);
                }

                isLoaded.set(true);
                FileLog.d("wd AiAdFeatureLibrary 特征库加载完成，共 " + featureMap.size() + " 个特征");
            } catch (Exception e) {
                FileLog.e("wd AiAdFeatureLibrary 加载特征库失败", e);
            }
        }
    }

    //wd 从文件加载特征
    private void loadFeaturesFromFile(File file) throws IOException {
        featureMap.clear();
        featureList.clear();

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        int lineNum = 0;
        int successCount = 0;

        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();

            //wd 跳过空行和注释行
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            AiAdKeywordFeature feature = AiAdKeywordFeature.fromCsvLine(line);
            if (feature != null) {
                featureMap.put(feature.keyword.toLowerCase(), feature);
                featureList.add(feature);
                successCount++;
            } else {
                FileLog.w("wd AiAdFeatureLibrary 第 " + lineNum + " 行解析失败: " + line);
            }
        }

        reader.close();
        FileLog.d("wd AiAdFeatureLibrary 从文件加载 " + successCount + " 个特征，共 " + lineNum + " 行");
    }

    //wd 从assets复制默认特征库
    private void copyFeaturesFromAssets(File destFile) {
        try {
            InputStream is = context.getAssets().open(ASSETS_FEATURE_LIBRARY);
            FileWriter writer = new FileWriter(destFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }

            reader.close();
            writer.close();
            FileLog.d("wd AiAdFeatureLibrary 已从assets复制默认特征库");
        } catch (IOException e) {
            FileLog.e("wd AiAdFeatureLibrary 从assets复制特征库失败", e);
        }
    }

    //wd 保存特征库到文件
    public boolean saveFeatures() {
        synchronized (loadLock) {
            try {
                File featureFile = getFeatureLibraryFile();
                FileWriter writer = new FileWriter(featureFile);

                //wd 写入文件头注释
                writer.write("# AI广告关键词特征库\n");
                writer.write("# 格式：关键词,权重,频次,分类,来源\n");
                writer.write("# 权重范围：0.0-1.0\n");
                writer.write("# 分类：ad(广告), normal(正常)\n");
                writer.write("# 来源：ai_extracted(AI提取), manual(手动添加)\n");
                writer.write("\n");

                //wd 写入所有特征
                for (AiAdKeywordFeature feature : featureList) {
                    writer.write(feature.toCsvLine() + "\n");
                }

                writer.close();
                FileLog.d("wd AiAdFeatureLibrary 特征库已保存，共 " + featureList.size() + " 个特征");
                return true;
            } catch (IOException e) {
                FileLog.e("wd AiAdFeatureLibrary 保存特征库失败", e);
                return false;
            }
        }
    }

    //wd 添加或更新特征
    public void addOrUpdateFeature(AiAdKeywordFeature feature) {
        if (feature == null || feature.keyword.isEmpty()) {
            return;
        }

        String key = feature.keyword.toLowerCase();
        AiAdKeywordFeature existing = featureMap.get(key);

        if (existing != null) {
            //wd 如果已存在，更新频次和权重（取较高值）
            int newFrequency = existing.frequency + feature.frequency;
            float newWeight = Math.max(existing.weight, feature.weight);
            AiAdKeywordFeature updated = new AiAdKeywordFeature(
                    feature.keyword, newWeight, newFrequency, feature.category, feature.source);
            featureMap.put(key, updated);

            //wd 更新列表
            featureList.remove(existing);
            featureList.add(updated);

            FileLog.d("wd AiAdFeatureLibrary 更新特征: " + feature.keyword + " 频次=" + newFrequency);
        } else {
            //wd 添加新特征
            featureMap.put(key, feature);
            featureList.add(feature);
            FileLog.d("wd AiAdFeatureLibrary 添加新特征: " + feature.keyword);
        }
    }

    //wd 批量添加特征
    public void addFeatures(List<AiAdKeywordFeature> features) {
        if (features == null || features.isEmpty()) {
            return;
        }

        for (AiAdKeywordFeature feature : features) {
            addOrUpdateFeature(feature);
        }

        //wd 自动保存
        saveFeatures();
    }

    //wd 移除特征
    public boolean removeFeature(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }

        String key = keyword.toLowerCase();
        AiAdKeywordFeature removed = featureMap.remove(key);

        if (removed != null) {
            featureList.remove(removed);
            FileLog.d("wd AiAdFeatureLibrary 移除特征: " + keyword);
            return true;
        }

        return false;
    }

    //wd 获取特征
    @Nullable
    public AiAdKeywordFeature getFeature(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        return featureMap.get(keyword.toLowerCase());
    }

    //wd 检查是否包含特征
    public boolean containsFeature(String keyword) {
        return getFeature(keyword) != null;
    }

    //wd 获取所有特征列表
    @NonNull
    public List<AiAdKeywordFeature> getAllFeatures() {
        return new ArrayList<>(featureList);
    }

    //wd 获取广告特征列表
    @NonNull
    public List<AiAdKeywordFeature> getAdFeatures() {
        List<AiAdKeywordFeature> adFeatures = new ArrayList<>();
        for (AiAdKeywordFeature feature : featureList) {
            if (feature.isAdKeyword()) {
                adFeatures.add(feature);
            }
        }
        return adFeatures;
    }

    //wd 获取特征数量
    public int getFeatureCount() {
        return featureMap.size();
    }

    //wd 清空特征库
    public void clearFeatures() {
        featureMap.clear();
        featureList.clear();
        FileLog.d("wd AiAdFeatureLibrary 特征库已清空");
    }

    //wd 重新加载特征库
    public void reloadFeatures() {
        synchronized (loadLock) {
            isLoaded.set(false);
            clearFeatures();
            loadFeatures();
        }
    }

    //wd 计算文本与特征库的匹配得分
    //wd 返回得分（0.0-1.0）和匹配的关键词列表
    public MatchResult calculateMatchScore(String text) {
        if (text == null || text.isEmpty()) {
            return new MatchResult(0f, Collections.emptyMap());
        }

        String lowerText = text.toLowerCase();
        float totalScore = 0f;
        Map<String, Integer> matchedKeywords = new HashMap<>();

        //wd 遍历所有特征，计算匹配得分
        for (AiAdKeywordFeature feature : featureList) {
            if (!feature.isAdKeyword()) {
                continue;
            }

            //wd 统计关键词出现次数
            int count = countOccurrences(lowerText, feature.keyword);
            if (count > 0) {
                matchedKeywords.put(feature.keyword, count);
                //wd 计算得分：有效权重 * 频次^1.2
                float freqFactor = (float) Math.pow(count, 1.2);
                totalScore += feature.getEffectiveWeight() * freqFactor;
            }
        }

        //wd 将得分限制在0-1范围内
        totalScore = Math.min(1f, totalScore);

        return new MatchResult(totalScore, matchedKeywords);
    }

    //wd 统计关键词在文本中出现次数
    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        String lowerKeyword = keyword.toLowerCase();

        while ((index = text.indexOf(lowerKeyword, index)) != -1) {
            count++;
            index += lowerKeyword.length();
        }

        return count;
    }

    //wd 检查是否已加载
    public boolean isLoaded() {
        return isLoaded.get();
    }

    //wd 匹配结果类
    public static class MatchResult {
        public final float score;
        public final Map<String, Integer> matchedKeywords;

        public MatchResult(float score, Map<String, Integer> matchedKeywords) {
            this.score = score;
            this.matchedKeywords = matchedKeywords != null ? matchedKeywords : Collections.emptyMap();
        }

        public boolean isAd() {
            return score >= 0.5f;
        }
    }
}
