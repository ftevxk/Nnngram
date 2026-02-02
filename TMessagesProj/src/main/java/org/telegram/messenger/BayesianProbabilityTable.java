/*
 * Copyright (C) 2024 Nnngram
 * 贝叶斯概率表管理类
 * 管理朴素贝叶斯分类器的先验概率和条件概率
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

//wd 贝叶斯概率表管理类
//wd 管理先验概率P(类别)和条件概率P(特征|类别)
//wd 使用拉普拉斯平滑处理未登录词
public class BayesianProbabilityTable {

    private static volatile BayesianProbabilityTable instance;

    //wd 先验概率 P(类别)
    //wd key: "ad" 或 "normal", value: 概率值
    private final Map<String, Double> priorProbabilities;

    //wd 条件概率 P(特征|类别)
    //wd 外层key: 类别("ad"/"normal")，内层key: 特征词，value: 概率值
    private final Map<String, Map<String, Double>> conditionalProbabilities;

    //wd 特征词频统计（用于平滑计算）
    //wd 外层key: 类别，内层key: 特征词，value: 出现次数
    private final Map<String, Map<String, Integer>> featureCounts;

    //wd 类别总词数
    private final Map<String, Integer> classTotalCounts;

    //wd 词汇表大小（用于拉普拉斯平滑）
    private int vocabularySize = 0;

    //wd 平滑参数（拉普拉斯平滑）
    private static final double SMOOTHING_ALPHA = 1.0;

    //wd 上下文
    private Context context;

    //wd 加载状态
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final Object loadLock = new Object();

    //wd 文件路径
    private static final String PROB_TABLE_FILE = "bayesian_prob_table.json";
    private static final String ASSETS_PROB_TABLE = "ai_ad_filter/bayesian_prob_table.json";

    //wd 类别常量
    public static final String CLASS_AD = "ad";
    public static final String CLASS_NORMAL = "normal";

    //wd 私有构造函数
    private BayesianProbabilityTable() {
        priorProbabilities = new ConcurrentHashMap<>();
        conditionalProbabilities = new ConcurrentHashMap<>();
        featureCounts = new ConcurrentHashMap<>();
        classTotalCounts = new ConcurrentHashMap<>();

        //wd 初始化条件概率存储
        conditionalProbabilities.put(CLASS_AD, new ConcurrentHashMap<>());
        conditionalProbabilities.put(CLASS_NORMAL, new ConcurrentHashMap<>());
        featureCounts.put(CLASS_AD, new ConcurrentHashMap<>());
        featureCounts.put(CLASS_NORMAL, new ConcurrentHashMap<>());
        classTotalCounts.put(CLASS_AD, 0);
        classTotalCounts.put(CLASS_NORMAL, 0);
    }

    //wd 获取单例实例
    public static BayesianProbabilityTable getInstance() {
        if (instance == null) {
            synchronized (BayesianProbabilityTable.class) {
                if (instance == null) {
                    instance = new BayesianProbabilityTable();
                }
            }
        }
        return instance;
    }

    //wd 初始化概率表
    public void init(Context context) {
        this.context = context.getApplicationContext();
        if (!isLoaded.get()) {
            loadProbabilities();
        }
    }

    //wd 获取概率表文件路径
    private File getProbTableFile() {
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
        return new File(aiFilterDir, PROB_TABLE_FILE);
    }

    //wd 加载概率表
    public void loadProbabilities() {
        synchronized (loadLock) {
            if (isLoaded.get()) {
                return;
            }

            try {
                File probFile = getProbTableFile();

                //wd 如果文件不存在，从assets复制
                if (!probFile.exists()) {
                    copyProbTableFromAssets(probFile);
                }

                //wd 从JSON文件加载概率表
                if (probFile.exists()) {
                    loadProbabilitiesFromJson(probFile);
                }

                isLoaded.set(true);
                FileLog.d("wd BayesianProbabilityTable 概率表加载完成，词汇表大小=" + vocabularySize);
            } catch (Exception e) {
                FileLog.e("wd BayesianProbabilityTable 加载概率表失败", e);
                //wd 初始化空概率表
                initializeEmptyTable();
            }
        }
    }

    //wd 从JSON文件加载概率表
    private void loadProbabilitiesFromJson(File file) throws IOException, JSONException {
        StringBuilder jsonBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        reader.close();

        JSONObject root = new JSONObject(jsonBuilder.toString());

        //wd 加载先验概率
        JSONObject priors = root.optJSONObject("priorProbabilities");
        if (priors != null) {
            priorProbabilities.put(CLASS_AD, priors.optDouble(CLASS_AD, 0.5));
            priorProbabilities.put(CLASS_NORMAL, priors.optDouble(CLASS_NORMAL, 0.5));
        }

        //wd 加载条件概率
        JSONObject conditionals = root.optJSONObject("conditionalProbabilities");
        if (conditionals != null) {
            JSONObject adProbs = conditionals.optJSONObject(CLASS_AD);
            if (adProbs != null) {
                loadConditionalProbs(CLASS_AD, adProbs);
            }

            JSONObject normalProbs = conditionals.optJSONObject(CLASS_NORMAL);
            if (normalProbs != null) {
                loadConditionalProbs(CLASS_NORMAL, normalProbs);
            }
        }

        //wd 加载词频统计
        JSONObject counts = root.optJSONObject("featureCounts");
        if (counts != null) {
            JSONObject adCounts = counts.optJSONObject(CLASS_AD);
            if (adCounts != null) {
                loadFeatureCounts(CLASS_AD, adCounts);
            }

            JSONObject normalCounts = counts.optJSONObject(CLASS_NORMAL);
            if (normalCounts != null) {
                loadFeatureCounts(CLASS_NORMAL, normalCounts);
            }
        }

        //wd 加载类别总词数
        JSONObject totals = root.optJSONObject("classTotalCounts");
        if (totals != null) {
            classTotalCounts.put(CLASS_AD, totals.optInt(CLASS_AD, 0));
            classTotalCounts.put(CLASS_NORMAL, totals.optInt(CLASS_NORMAL, 0));
        }

        //wd 计算词汇表大小
        calculateVocabularySize();
    }

    //wd 加载条件概率
    private void loadConditionalProbs(String className, JSONObject probs) throws JSONException {
        Map<String, Double> probMap = conditionalProbabilities.get(className);
        JSONArray names = probs.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String feature = names.getString(i);
                double prob = probs.getDouble(feature);
                probMap.put(feature, prob);
            }
        }
    }

    //wd 加载特征词频
    private void loadFeatureCounts(String className, JSONObject counts) throws JSONException {
        Map<String, Integer> countMap = featureCounts.get(className);
        JSONArray names = counts.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String feature = names.getString(i);
                int count = counts.getInt(feature);
                countMap.put(feature, count);
            }
        }
    }

    //wd 从assets复制默认概率表
    private boolean copyProbTableFromAssets(File destFile) {
        try {
            InputStream is = context.getAssets().open(ASSETS_PROB_TABLE);
            FileWriter writer = new FileWriter(destFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }

            reader.close();
            writer.close();
            FileLog.d("wd BayesianProbabilityTable 已从assets复制默认概率表");
            return true;
        } catch (IOException e) {
            FileLog.w("wd BayesianProbabilityTable assets中无默认概率表，将初始化空表");
            return false;
        }
    }

    //wd 初始化空概率表
    private void initializeEmptyTable() {
        FileLog.d("wd BayesianProbabilityTable 初始化空概率表");

        //wd 清空现有数据
        priorProbabilities.clear();
        conditionalProbabilities.get(CLASS_AD).clear();
        conditionalProbabilities.get(CLASS_NORMAL).clear();
        featureCounts.get(CLASS_AD).clear();
        featureCounts.get(CLASS_NORMAL).clear();

        //wd 设置默认先验概率
        priorProbabilities.put(CLASS_AD, 0.5);
        priorProbabilities.put(CLASS_NORMAL, 0.5);

        //wd 设置默认类别总词数
        classTotalCounts.put(CLASS_AD, 0);
        classTotalCounts.put(CLASS_NORMAL, 0);

        //wd 计算词汇表大小
        calculateVocabularySize();

        isLoaded.set(true);
        FileLog.d("wd BayesianProbabilityTable 空概率表初始化完成");
    }

    //wd 计算词汇表大小
    private void calculateVocabularySize() {
        java.util.Set<String> vocab = new java.util.HashSet<>();
        vocab.addAll(featureCounts.get(CLASS_AD).keySet());
        vocab.addAll(featureCounts.get(CLASS_NORMAL).keySet());
        vocabularySize = vocab.size();
    }

    //wd 重新计算条件概率
    private void recalculateConditionalProbabilities() {
        //wd 计算广告类别的条件概率
        int adTotal = classTotalCounts.get(CLASS_AD);
        Map<String, Double> adProbs = conditionalProbabilities.get(CLASS_AD);
        adProbs.clear();

        for (Map.Entry<String, Integer> entry : featureCounts.get(CLASS_AD).entrySet()) {
            String feature = entry.getKey();
            int count = entry.getValue();
            //wd 拉普拉斯平滑: (count + alpha) / (total + alpha * vocab_size)
            double prob = (count + SMOOTHING_ALPHA) / (adTotal + SMOOTHING_ALPHA * vocabularySize);
            adProbs.put(feature, prob);
        }

        //wd 计算正常类别的条件概率
        int normalTotal = classTotalCounts.get(CLASS_NORMAL);
        Map<String, Double> normalProbs = conditionalProbabilities.get(CLASS_NORMAL);
        normalProbs.clear();

        for (Map.Entry<String, Integer> entry : featureCounts.get(CLASS_NORMAL).entrySet()) {
            String feature = entry.getKey();
            int count = entry.getValue();
            double prob = (count + SMOOTHING_ALPHA) / (normalTotal + SMOOTHING_ALPHA * vocabularySize);
            normalProbs.put(feature, prob);
        }
    }

    //wd 保存概率表到文件
    public boolean saveProbabilities() {
        synchronized (loadLock) {
            try {
                JSONObject root = new JSONObject();

                //wd 保存先验概率
                JSONObject priors = new JSONObject();
                priors.put(CLASS_AD, priorProbabilities.getOrDefault(CLASS_AD, 0.5));
                priors.put(CLASS_NORMAL, priorProbabilities.getOrDefault(CLASS_NORMAL, 0.5));
                root.put("priorProbabilities", priors);

                //wd 保存条件概率
                JSONObject conditionals = new JSONObject();
                conditionals.put(CLASS_AD, new JSONObject(conditionalProbabilities.get(CLASS_AD)));
                conditionals.put(CLASS_NORMAL, new JSONObject(conditionalProbabilities.get(CLASS_NORMAL)));
                root.put("conditionalProbabilities", conditionals);

                //wd 保存词频统计
                JSONObject counts = new JSONObject();
                counts.put(CLASS_AD, new JSONObject(featureCounts.get(CLASS_AD)));
                counts.put(CLASS_NORMAL, new JSONObject(featureCounts.get(CLASS_NORMAL)));
                root.put("featureCounts", counts);

                //wd 保存类别总词数
                JSONObject totals = new JSONObject();
                totals.put(CLASS_AD, classTotalCounts.get(CLASS_AD));
                totals.put(CLASS_NORMAL, classTotalCounts.get(CLASS_NORMAL));
                root.put("classTotalCounts", totals);

                //wd 写入文件
                File probFile = getProbTableFile();
                FileWriter writer = new FileWriter(probFile);
                writer.write(root.toString(2));
                writer.close();

                FileLog.d("wd BayesianProbabilityTable 概率表已保存");
                return true;
            } catch (Exception e) {
                FileLog.e("wd BayesianProbabilityTable 保存概率表失败", e);
                return false;
            }
        }
    }

    //wd 获取先验概率 P(类别)
    public double getPriorProbability(String className) {
        return priorProbabilities.getOrDefault(className, 0.5);
    }

    //wd 设置先验概率
    public void setPriorProbability(String className, double probability) {
        priorProbabilities.put(className, Math.max(0.0, Math.min(1.0, probability)));
    }

    //wd 获取条件概率 P(特征|类别)
    //wd 如果特征不存在，返回平滑后的默认概率
    public double getConditionalProbability(String feature, String className) {
        Map<String, Double> probs = conditionalProbabilities.get(className);
        if (probs != null && probs.containsKey(feature)) {
            return probs.get(feature);
        }

        //wd 未登录词，使用拉普拉斯平滑计算默认概率
        int totalCount = classTotalCounts.getOrDefault(className, 0);
        if (totalCount == 0) {
            return SMOOTHING_ALPHA / (SMOOTHING_ALPHA * vocabularySize);
        }
        return SMOOTHING_ALPHA / (totalCount + SMOOTHING_ALPHA * vocabularySize);
    }

    //wd 获取特征词频
    public int getFeatureCount(String feature, String className) {
        Map<String, Integer> counts = featureCounts.get(className);
        if (counts != null) {
            return counts.getOrDefault(feature, 0);
        }
        return 0;
    }

    //wd 更新特征词频（用于在线学习）
    public void updateFeatureCount(String feature, String className, int delta) {
        Map<String, Integer> counts = featureCounts.get(className);
        if (counts != null) {
            int newCount = counts.getOrDefault(feature, 0) + delta;
            if (newCount > 0) {
                counts.put(feature, newCount);
            } else {
                counts.remove(feature);
            }

            //wd 更新类别总词数
            classTotalCounts.put(className, classTotalCounts.getOrDefault(className, 0) + delta);

            //wd 重新计算词汇表大小
            calculateVocabularySize();

            //wd 重新计算条件概率
            recalculateConditionalProbabilities();
        }
    }

    //wd 设置特征词频（用于编辑器）
    public void setFeatureCount(String feature, String className, int count) {
        if (feature == null || feature.isEmpty() || count < 0) {
            return;
        }

        Map<String, Integer> counts = featureCounts.get(className);
        if (counts != null) {
            String lowerFeature = feature.toLowerCase();
            int oldCount = counts.getOrDefault(lowerFeature, 0);
            int delta = count - oldCount;

            if (count > 0) {
                counts.put(lowerFeature, count);
            } else {
                counts.remove(lowerFeature);
            }

            //wd 更新类别总词数
            classTotalCounts.put(className, classTotalCounts.getOrDefault(className, 0) + delta);

            //wd 重新计算词汇表大小和条件概率
            calculateVocabularySize();
            recalculateConditionalProbabilities();

            FileLog.d("wd BayesianProbabilityTable 设置特征词频: " + lowerFeature + "=" + count);
        }
    }

    //wd 移除特征（用于编辑器）
    public boolean removeFeature(String feature, String className) {
        if (feature == null || feature.isEmpty()) {
            return false;
        }

        Map<String, Integer> counts = featureCounts.get(className);
        if (counts != null) {
            String lowerFeature = feature.toLowerCase();
            Integer removed = counts.remove(lowerFeature);
            if (removed != null) {
                //wd 更新类别总词数
                classTotalCounts.put(className, classTotalCounts.getOrDefault(className, 0) - removed);

                //wd 重新计算词汇表大小和条件概率
                calculateVocabularySize();
                recalculateConditionalProbabilities();

                FileLog.d("wd BayesianProbabilityTable 移除特征: " + lowerFeature);
                return true;
            }
        }
        return false;
    }

    //wd 清空某类所有特征（用于编辑器）
    public void clearClassFeatures(String className) {
        Map<String, Integer> counts = featureCounts.get(className);
        if (counts != null) {
            counts.clear();
            classTotalCounts.put(className, 0);

            calculateVocabularySize();
            recalculateConditionalProbabilities();

            FileLog.d("wd BayesianProbabilityTable 清空类别特征: " + className);
        }
    }

    //wd 获取某类所有特征及其词频（用于编辑器）
    @NonNull
    public Map<String, Integer> getFeaturesByClass(String className) {
        Map<String, Integer> counts = featureCounts.get(className);
        if (counts != null) {
            return new HashMap<>(counts);
        }
        return new HashMap<>();
    }

    //wd 获取类别总词数
    public int getClassTotalCount(String className) {
        return classTotalCounts.getOrDefault(className, 0);
    }

    //wd 获取词汇表大小
    public int getVocabularySize() {
        return vocabularySize;
    }

    //wd 获取所有特征词
    @NonNull
    public Set<String> getAllFeatures() {
        Set<String> allFeatures = new java.util.HashSet<>();
        allFeatures.addAll(featureCounts.get(CLASS_AD).keySet());
        allFeatures.addAll(featureCounts.get(CLASS_NORMAL).keySet());
        return allFeatures;
    }

    //wd 检查是否已加载
    public boolean isLoaded() {
        return isLoaded.get();
    }

    //wd 获取平滑参数
    public double getSmoothingAlpha() {
        return SMOOTHING_ALPHA;
    }

    //wd 重新加载概率表（用于编辑器保存后刷新）
    public void reloadProbabilities() {
        synchronized (loadLock) {
            isLoaded.set(false);
            priorProbabilities.clear();
            conditionalProbabilities.get(CLASS_AD).clear();
            conditionalProbabilities.get(CLASS_NORMAL).clear();
            featureCounts.get(CLASS_AD).clear();
            featureCounts.get(CLASS_NORMAL).clear();
            classTotalCounts.put(CLASS_AD, 0);
            classTotalCounts.put(CLASS_NORMAL, 0);
            vocabularySize = 0;
            loadProbabilities();
        }
    }
}
