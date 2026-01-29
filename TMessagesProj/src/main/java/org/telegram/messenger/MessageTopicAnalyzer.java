/*
 * Copyright (C) 2024 Nnngram
 * 消息主题分析器 - 本地TFLite模型版
 * 使用本地TensorFlow Lite模型进行语义分析，提取消息主题、意图和关键特征
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

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageTopicAnalyzer {
    private static volatile MessageTopicAnalyzer instance;
    private Context context;
    
    // 主题分析模型
    private Interpreter topicInterpreter;
    private MappedByteBuffer topicModelBuffer;
    private List<String> topicLabels;
    private final AtomicBoolean topicModelLoaded = new AtomicBoolean(false);
    
    //wd 特征覆盖率回归模型
    private Interpreter coverageInterpreter;
    private MappedByteBuffer coverageModelBuffer;
    private final AtomicBoolean coverageModelLoaded = new AtomicBoolean(false);

    //wd 关键词配置文件路径（在Nnngram Files目录下，方便用户自定义）
    private static final String KEYWORDS_ASSETS_PATH = "ai_ad_filter/ad_keywords.txt";
    private List<KeywordEntry> adKeywords;        //wd 广告关键词
    private List<KeywordEntry> consultKeywords;   //wd 咨询关键词
    private List<KeywordEntry> chatKeywords;      //wd 闲聊关键词
    private List<KeywordEntry> greetingKeywords;  //wd 问候关键词
    private final AtomicBoolean keywordsLoaded = new AtomicBoolean(false);

    //wd 获取外部存储的关键词文件路径
    private File getKeywordsFile() {
        File externalDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        File nnngramFilesDir = new File(externalDir, "Nnngram Files");
        File aiFilterDir = new File(nnngramFilesDir, "ai_ad_filter");
        if (!aiFilterDir.exists()) {
            aiFilterDir.mkdirs();
        }
        return new File(aiFilterDir, "ad_keywords.txt");
    }

    //wd 确保关键词文件存在，不存在则从assets复制
    private void ensureKeywordsFile() {
        File keywordsFile = getKeywordsFile();
        if (!keywordsFile.exists()) {
            copyKeywordsFromAssets(keywordsFile);
        }
    }

    //wd 从assets复制关键词文件到外部存储
    private void copyKeywordsFromAssets(File destFile) {
        try {
            InputStream is = context.getAssets().open(KEYWORDS_ASSETS_PATH);
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            is.close();
            fos.close();
            FileLog.d("wd 关键词文件已复制到: " + destFile.getAbsolutePath());
        } catch (IOException e) {
            FileLog.e("wd 复制关键词文件失败", e);
        }
    }
    
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object modelLock = new Object();

    // 主题类型
    public enum TopicType {
        UNKNOWN,        // 未知
        CHAT,           // 闲聊
        CONSULTATION,   // 咨询/询问
        PROMOTION,      // 推广/营销
        NOTIFICATION,   // 通知/公告
        TRANSACTION,    // 交易/转账
        RECRUITMENT,    // 招聘/招募
        GREETING        // 问候/打招呼
    }

    // 意图类型
    public enum IntentType {
        UNKNOWN,
        INFORM,         // 告知
        REQUEST,        // 请求
        OFFER,          // 提供
        INVITE,         // 邀请
        URGE            // 催促/诱导
    }

    //wd 关键词条目
    private static class KeywordEntry {
        final String word;
        final float weight;
        KeywordEntry(String word, float weight) {
            this.word = word;
            this.weight = weight;
        }
    }

    // 分析结果
    public static class TopicAnalysis {
        public final TopicType topicType;
        public final IntentType intentType;
        public final float promotionScore;      // 推广意图分数 0-1
        public final float adProbability;       // 广告概率 0-1
        public final Set<String> entities;      // 提取的实体
        public final Map<String, Float> features; // 特征分数
        public final String summary;            // 内容摘要
        public final boolean isFromTopicModel;  // 是否来自主题模型
        public final boolean isFromAdModel;     // 是否来自广告模型

        public TopicAnalysis(TopicType topicType, IntentType intentType,
                            float promotionScore, float adProbability,
                            Set<String> entities, Map<String, Float> features,
                            String summary, boolean isFromTopicModel, boolean isFromAdModel) {
            this.topicType = topicType;
            this.intentType = intentType;
            this.promotionScore = promotionScore;
            this.adProbability = adProbability;
            this.entities = entities;
            this.features = features;
            this.summary = summary;
            this.isFromTopicModel = isFromTopicModel;
            this.isFromAdModel = isFromAdModel;
        }

        @Override
        public String toString() {
            return String.format("TopicAnalysis{topic=%s, intent=%s, adProb=%.2f, topicModel=%s, adModel=%s}",
                topicType, intentType, adProbability, isFromTopicModel, isFromAdModel);
        }
    }

    //wd 模型路径
    private static final String TOPIC_MODEL_PATH = "ai_ad_filter/topic_model.tflite";
    private static final String TOPIC_LABELS_PATH = "ai_ad_filter/topic_labels.txt";
    private static final String TOPIC_VOCAB_PATH = "ai_ad_filter/topic_vocabulary.txt";
    private static final String TOPIC_IDF_PATH = "ai_ad_filter/topic_idf.txt";
    private static final String COVERAGE_MODEL_PATH = "ai_ad_filter/coverage_model.tflite";
    private static final String COVERAGE_VOCAB_PATH = "ai_ad_filter/coverage_vocabulary.txt";
    private static final String COVERAGE_IDF_PATH = "ai_ad_filter/coverage_idf.txt";

    //wd TF-IDF 向量化器
    private TfidfVectorizer topicVectorizer;
    private TfidfVectorizer coverageVectorizer;

    private MessageTopicAnalyzer() {
    }

    public static MessageTopicAnalyzer getInstance() {
        if (instance == null) {
            synchronized (MessageTopicAnalyzer.class) {
                if (instance == null) {
                    instance = new MessageTopicAnalyzer();
                }
            }
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        if (!isInitializing.getAndSet(true)) {
            executor.execute(this::loadModels);
        }
    }

    private void loadModels() {
        //wd 加载主题分析模型
        loadTopicModel();
        //wd 加载特征覆盖率模型
        loadCoverageModel();
        //wd 加载关键词配置文件
        loadKeywords();
        isInitializing.set(false);
    }

    private void loadTopicModel() {
        synchronized (modelLock) {
            try {
                FileLog.d("wd MessageTopicAnalyzer: 加载主题模型从 " + TOPIC_MODEL_PATH);
                topicModelBuffer = loadModelFile(TOPIC_MODEL_PATH);
                if (topicModelBuffer == null) {
                    FileLog.e("wd MessageTopicAnalyzer: 主题模型文件未找到");
                    return;
                }

                topicLabels = loadLabels(TOPIC_LABELS_PATH);
                if (topicLabels == null || topicLabels.isEmpty()) {
                    FileLog.e("wd MessageTopicAnalyzer: 加载主题标签失败");
                    return;
                }
                FileLog.d("wd MessageTopicAnalyzer: 已加载 " + topicLabels.size() + " 个主题标签: " + topicLabels);

                // 加载 TF-IDF 向量化器
                try {
                    topicVectorizer = new TfidfVectorizer(context, TOPIC_VOCAB_PATH, TOPIC_IDF_PATH);
                    FileLog.d("wd MessageTopicAnalyzer: 主题向量化器已加载, 特征大小=" + topicVectorizer.getFeatureSize());
                } catch (IOException e) {
                    FileLog.e("wd MessageTopicAnalyzer: 加载主题向量化器失败", e);
                    return;
                }

                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(2);
                topicInterpreter = new Interpreter(topicModelBuffer, options);

                topicModelLoaded.set(true);
                FileLog.d("wd MessageTopicAnalyzer: 主题模型加载成功");
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: 主题模型初始化错误", e);
            }
        }
    }

    //wd 加载特征覆盖率回归模型
    private void loadCoverageModel() {
        synchronized (modelLock) {
            try {
                FileLog.d("wd MessageTopicAnalyzer: 加载覆盖率模型从 " + COVERAGE_MODEL_PATH);
                coverageModelBuffer = loadModelFile(COVERAGE_MODEL_PATH);
                if (coverageModelBuffer == null) {
                    FileLog.e("wd MessageTopicAnalyzer: 覆盖率模型文件未找到");
                    return;
                }

                //wd 加载 TF-IDF 向量化器
                try {
                    coverageVectorizer = new TfidfVectorizer(context, COVERAGE_VOCAB_PATH, COVERAGE_IDF_PATH);
                    FileLog.d("wd MessageTopicAnalyzer: 覆盖率向量化器已加载, 特征大小=" + coverageVectorizer.getFeatureSize());
                } catch (IOException e) {
                    FileLog.e("wd MessageTopicAnalyzer: 加载覆盖率向量化器失败", e);
                    return;
                }

                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(2);
                coverageInterpreter = new Interpreter(coverageModelBuffer, options);

                coverageModelLoaded.set(true);
                FileLog.d("wd MessageTopicAnalyzer: 覆盖率模型加载成功");
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: 覆盖率模型初始化错误", e);
            }
        }
    }

    //wd 加载关键词配置文件
    private void loadKeywords() {
        synchronized (modelLock) {
            if (keywordsLoaded.get()) {
                return;
            }

            try {
                //wd 确保关键词文件存在
                ensureKeywordsFile();

                File keywordsFile = getKeywordsFile();
                FileLog.d("wd MessageTopicAnalyzer: 加载关键词文件 " + keywordsFile.getAbsolutePath());

                BufferedReader reader = new BufferedReader(new FileReader(keywordsFile));

                adKeywords = new ArrayList<>();
                consultKeywords = new ArrayList<>();
                chatKeywords = new ArrayList<>();
                greetingKeywords = new ArrayList<>();

                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length < 3) {
                        continue;
                    }

                    String word = parts[0].trim();
                    float weight;
                    try {
                        weight = Float.parseFloat(parts[1].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String category = parts[2].trim().toLowerCase();

                    KeywordEntry entry = new KeywordEntry(word, weight);

                    switch (category) {
                        case "ad":
                            adKeywords.add(entry);
                            break;
                        case "consult":
                            consultKeywords.add(entry);
                            break;
                        case "chat":
                            chatKeywords.add(entry);
                            break;
                        case "greeting":
                            greetingKeywords.add(entry);
                            break;
                    }
                }

                reader.close();

                FileLog.d("wd MessageTopicAnalyzer: 关键词加载完成 - 广告词:" + adKeywords.size() +
                    ", 咨询词:" + consultKeywords.size() +
                    ", 闲聊词:" + chatKeywords.size() +
                    ", 问候词:" + greetingKeywords.size());

                keywordsLoaded.set(true);
            } catch (IOException e) {
                FileLog.e("wd MessageTopicAnalyzer: 加载关键词文件失败", e);
            }
        }
    }

    //wd 重新加载关键词（用于同步后刷新）
    public void reloadKeywords() {
        synchronized (modelLock) {
            keywordsLoaded.set(false);
            loadKeywords();
        }
    }

    private MappedByteBuffer loadModelFile(String path) throws IOException {
        try (android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(path);
             java.io.FileInputStream inputStream = afd.createInputStream();
             java.nio.channels.FileChannel channel = inputStream.getChannel()) {
            return channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.getStartOffset(), afd.getLength());
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }

    private List<String> loadLabels(String path) {
        List<String> labelList = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    labelList.add(line);
                }
            }
            reader.close();
        } catch (IOException e) {
            FileLog.e("wd MessageTopicAnalyzer: 从 " + path + " 加载标签失败", e);
            return null;
        }
        return labelList;
    }

    @NonNull
    public TopicAnalysis analyze(@NonNull String text) {
        if (TextUtils.isEmpty(text)) {
            return createEmptyAnalysis();
        }

        String normalizedText = normalizeText(text);
        
        // 阶段1: 主题分析
        TopicType topicType = TopicType.UNKNOWN;
        boolean fromTopicModel = false;
        
        if (topicModelLoaded.get() && topicInterpreter != null) {
            try {
                topicType = classifyTopic(normalizedText);
                fromTopicModel = true;
                FileLog.d("wd MessageTopicAnalyzer: 主题模型分类结果: " + topicType);
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: 主题模型分类失败", e);
            }
        }
        
        //wd 如果主题模型失败，使用规则引擎
        if (topicType == TopicType.UNKNOWN) {
            topicType = analyzeTopicTypeRuleBased(normalizedText.toLowerCase(), extractEntities(normalizedText));
        }

        //wd 阶段2: 生成主题总结
        String topicSummary = generateTopicSummary(normalizedText, topicType);
        FileLog.d("wd MessageTopicAnalyzer: 主题摘要: " + topicSummary);

        //wd 阶段3: 计算特征覆盖率（AI模型 + 规则引擎辅助）
        float aiCoverage = 0f;
        float ruleCoverage = 0f;
        boolean fromCoverageModel = false;

        //wd 使用AI模型计算覆盖率
        if (coverageModelLoaded.get() && coverageInterpreter != null) {
            try {
                aiCoverage = calculateCoverage(normalizedText);
                fromCoverageModel = true;
                FileLog.d("wd MessageTopicAnalyzer: AI覆盖率 = " + aiCoverage);
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: 覆盖率计算失败", e);
            }
        }

        //wd 规则引擎辅助计算
        ruleCoverage = calculateRuleBasedCoverage(normalizedText, topicType);
        FileLog.d("wd MessageTopicAnalyzer: 规则覆盖率 = " + ruleCoverage);

        //wd 根据主题类型调整AI覆盖率
        //wd 白名单主题（闲聊、咨询、问候）降低AI覆盖率
        float adjustedAiCoverage = adjustCoverageByTopicType(aiCoverage, topicType);
        FileLog.d("wd MessageTopicAnalyzer: 调整后AI覆盖率 = " + adjustedAiCoverage);

        //wd 综合得分 = 调整后AI覆盖率 * 0.7 + 规则引擎得分 * 0.3
        //wd 增加规则引擎权重，降低AI模型权重
        float finalCoverage = adjustedAiCoverage * 0.7f + ruleCoverage * 0.3f;
        FileLog.d("wd MessageTopicAnalyzer: 最终覆盖率 = " + finalCoverage);
        
        //wd 提取实体和特征
        Set<String> entities = extractEntities(normalizedText);
        Map<String, Float> features = extractFeatures(normalizedText, entities);

        //wd 计算推广分数
        float promotionScore = calculatePromotionScore(normalizedText.toLowerCase(), entities);

        //wd 分析意图
        IntentType intentType = analyzeIntentTypeRuleBased(normalizedText.toLowerCase());

        //wd 生成摘要（包含主题总结）
        String summary = generateSummary(normalizedText, topicType, entities, topicSummary);

        return new TopicAnalysis(
            topicType, intentType, promotionScore, finalCoverage,
            entities, features, summary, fromTopicModel, fromCoverageModel);
    }

    private TopicType classifyTopic(String text) {
        synchronized (modelLock) {
            if (topicInterpreter == null || topicLabels == null || topicVectorizer == null) {
                FileLog.w("wd MessageTopicAnalyzer: topic model not ready");
                return TopicType.UNKNOWN;
            }

            try {
                // 使用 TF-IDF 向量化器编码文本
                float[][] input = topicVectorizer.transform(text);
                
                // 验证输入形状
                if (input == null || input.length == 0 || input[0].length == 0) {
                    FileLog.e("wd MessageTopicAnalyzer: 向量化器输入形状无效");
                    return TopicType.UNKNOWN;
                }
                
                // 准备输出缓冲区
                float[][] output = new float[1][topicLabels.size()];
                
                // 运行模型
                topicInterpreter.run(input, output);
                
                // 验证输出
                if (output == null || output.length == 0 || output[0].length == 0) {
                    FileLog.e("wd MessageTopicAnalyzer: 模型返回空输出");
                    return TopicType.UNKNOWN;
                }
                
                // 找到最大概率的类别
                int maxIndex = 0;
                float maxProb = output[0][0];
                for (int i = 1; i < output[0].length; i++) {
                    if (output[0][i] > maxProb) {
                        maxProb = output[0][i];
                        maxIndex = i;
                    }
                }
                
                FileLog.d("wd MessageTopicAnalyzer: 主题分类结果 - 索引=" + maxIndex +
                    ", 概率=" + maxProb + ", 标签=" + (maxIndex < topicLabels.size() ? topicLabels.get(maxIndex) : "未知"));
                
                // 转换为TopicType
                if (maxIndex < topicLabels.size()) {
                    return parseTopicType(topicLabels.get(maxIndex));
                }
                return TopicType.UNKNOWN;
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: 主题分类失败", e);
                return TopicType.UNKNOWN;
            }
        }
    }

    //wd 特征覆盖率核心方法 - 使用回归模型计算
    //wd 输入：原始消息文本
    //wd 输出：覆盖率分数（0.0 - 1.0）
    private float calculateCoverage(String text) {
        synchronized (modelLock) {
            if (coverageInterpreter == null || coverageVectorizer == null) {
                FileLog.w("wd MessageTopicAnalyzer: coverage model not ready");
                return 0f;
            }

            try {
                //wd 使用 TF-IDF 向量化器编码文本
                float[][] input = coverageVectorizer.transform(text);

                //wd 验证输入
                if (input == null || input.length == 0 || input[0].length == 0) {
                    FileLog.w("wd MessageTopicAnalyzer: invalid input for coverage model");
                    return 0f;
                }

                //wd 准备输出缓冲区（回归模型输出单个值）
                float[][] output = new float[1][1];

                //wd 运行模型
                coverageInterpreter.run(input, output);

                //wd 获取覆盖率分数（0-1之间）
                float coverage = output[0][0];

                FileLog.d("wd MessageTopicAnalyzer: AI覆盖率计算结果 = " + coverage);
                return coverage;
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: coverage calculation failed", e);
                return 0f;
            }
        }
    }

    //wd 根据主题类型调整AI覆盖率
    //wd 白名单主题降低覆盖率，避免误判正常消息
    private float adjustCoverageByTopicType(float aiCoverage, TopicType topicType) {
        switch (topicType) {
            case CHAT:      //wd 闲聊
            case GREETING:  //wd 问候
                //wd 大幅降低覆盖率，这些主题几乎不可能是广告
                return aiCoverage * 0.3f;
            case CONSULTATION: //wd 咨询
                //wd 适度降低覆盖率
                return aiCoverage * 0.5f;
            case NOTIFICATION: //wd 通知
                //wd 轻微降低
                return aiCoverage * 0.7f;
            case PROMOTION:    //wd 推广
            case TRANSACTION:  //wd 交易
            case RECRUITMENT:  //wd 招聘
                //wd 这些主题可能是广告，保持原值
                return aiCoverage;
            default:
                return aiCoverage;
        }
    }

    //wd 规则引擎辅助计算特征覆盖率
    //wd 基于关键词文件动态计算得分，支持多词叠加，带组合判断和安全机制
    //wd 修复：同一关键词多次出现会累加得分
    private float calculateRuleBasedCoverage(String text, TopicType topicType) {
        String lowerText = text.toLowerCase();
        float rawScore = 0f;
        int highWeightCount = 0;    //wd 高权重词计数 (>=0.8)
        int mediumWeightCount = 0;  //wd 中权重词计数 (0.5-0.8)
        int lowWeightCount = 0;     //wd 低权重词计数 (<0.5)
        int normalWordCount = 0;    //wd 正常词计数

        //wd 如果关键词文件已加载，使用动态关键词
        if (keywordsLoaded.get() && adKeywords != null) {
            //wd 广告关键词 - 动态叠加得分并分类计数（支持同一关键词多次出现累加）
            for (KeywordEntry entry : adKeywords) {
                //wd 统计关键词出现次数
                int count = countKeywordOccurrences(lowerText, entry.word);
                if (count > 0) {
                    //wd 每次出现都累加得分
                    rawScore += entry.weight * count;
                    //wd 按权重分类计数（按出现次数计数）
                    if (entry.weight >= 0.8) {
                        highWeightCount += count;
                    } else if (entry.weight >= 0.5) {
                        mediumWeightCount += count;
                    } else {
                        lowWeightCount += count;
                    }
                    FileLog.d("wd 关键词命中: " + entry.word + " 次数=" + count + " 权重=" + entry.weight + " 累加得分=" + (entry.weight * count));
                }
            }

            //wd 咨询关键词 - 针对consultation主题减分
            if (topicType == TopicType.CONSULTATION && consultKeywords != null) {
                for (KeywordEntry entry : consultKeywords) {
                    if (lowerText.contains(entry.word)) {
                        rawScore -= entry.weight;
                        normalWordCount++;
                    }
                }
            }

            //wd 闲聊关键词 - 针对chat主题减分
            if (topicType == TopicType.CHAT && chatKeywords != null) {
                for (KeywordEntry entry : chatKeywords) {
                    if (lowerText.contains(entry.word)) {
                        rawScore -= entry.weight;
                        normalWordCount++;
                    }
                }
            }

            //wd 问候关键词 - 针对greeting主题减分
            if (topicType == TopicType.GREETING && greetingKeywords != null) {
                for (KeywordEntry entry : greetingKeywords) {
                    if (lowerText.contains(entry.word)) {
                        rawScore -= entry.weight;
                        normalWordCount++;
                    }
                }
            }
        } else {
            //wd 回退到硬编码关键词（兼容旧版本）
            //wd 强广告关键词（高权重）
            String[] strongAdWords = {"博彩", "菠菜", "赌博", "赌球", "casino", "bet365", "百家乐", "老虎机",
                "跑分", "上分", "下分", "usdt", "虚拟货币", "合约带单", "币圈", "空投", "刷单", "返利", "充值"};
            for (String word : strongAdWords) {
                if (lowerText.contains(word)) {
                    rawScore += 0.4f;
                    highWeightCount++;
                }
            }

            //wd 跑分/收款码相关关键词（高权重）
            String[] paymentAdWords = {"收款码", "代收", "代付", "码商", "码子", "收款", "支付", "转账",
                "支付宝", "微信", "qq", "都能做", "一单一结", "几分钟", "结账", "零风险", "0风险"};
            for (String word : paymentAdWords) {
                if (lowerText.contains(word)) {
                    rawScore += 0.35f;
                    mediumWeightCount++;
                }
            }

            //wd 中等广告关键词（中权重）
            String[] mediumAdWords = {"首充", "代理招募", "月入过万", "稳赚不赔", "高额返佣",
                "日赚", "秒到账", "信誉担保", "内部消息", "限时优惠", "机不可失", "利润", "福利红包", "做单"};
            for (String word : mediumAdWords) {
                if (lowerText.contains(word)) {
                    rawScore += 0.25f;
                    mediumWeightCount++;
                }
            }

            //wd 轻微广告关键词（低权重）
            String[] lightAdWords = {"联系", "加好友", "私聊"};
            for (String word : lightAdWords) {
                if (lowerText.contains(word)) {
                    rawScore += 0.1f;
                    lowWeightCount++;
                }
            }

            //wd 咨询类关键词（减分）
            if (topicType == TopicType.CONSULTATION) {
                String[] consultWords = {"请问", "问一下", "咨询", "了解", "怎么", "如何", "多少", "什么",
                    "哪里", "吗？", "？", "能否", "可以吗", "行吗", "建议", "推荐"};
                for (String word : consultWords) {
                    if (lowerText.contains(word)) {
                        rawScore -= 0.3f;
                        normalWordCount++;
                    }
                }
            }

            //wd 闲聊类关键词（减分）
            if (topicType == TopicType.CHAT) {
                String[] chatWords = {"你好", "在吗", "最近怎么样", "天气", "周末", "谢谢", "感谢",
                    "哈哈", "呵呵", "好的", "ok", "嗯嗯", "是的", "没错", "对的"};
                for (String word : chatWords) {
                    if (lowerText.contains(word)) {
                        rawScore -= 0.25f;
                        normalWordCount++;
                    }
                }
            }

            //wd 问候类关键词（减分）
            if (topicType == TopicType.GREETING) {
                String[] greetingWords = {"早安", "晚安", "早上好", "晚上好", "你好", "您好", "大家好",
                    "节日快乐", "生日快乐", "周末愉快", "新年快乐"};
                for (String word : greetingWords) {
                    if (lowerText.contains(word)) {
                        rawScore -= 0.25f;
                        normalWordCount++;
                    }
                }
            }
        }

        //wd 组合判断逻辑 - 避免仅凭低权重词判定
        float adjustedScore;
        if (highWeightCount >= 1) {
            //wd 有高权重词，使用原始得分（强广告特征）
            adjustedScore = rawScore;
        } else if (mediumWeightCount >= 2) {
            //wd 2个以上中权重词
            adjustedScore = rawScore * 0.8f;
        } else if (mediumWeightCount >= 1 && lowWeightCount >= 2) {
            //wd 1个中权重词 + 2个以上低权重词
            adjustedScore = rawScore * 0.6f;
        } else if (lowWeightCount >= 3) {
            //wd 只有3个以上低权重词，大幅降低
            adjustedScore = rawScore * 0.3f;
        } else {
            //wd 不满足条件，视为正常
            adjustedScore = rawScore * 0.1f;
        }

        //wd 安全机制：如果正常词多于广告词，强制降低
        int adWordCount = highWeightCount + mediumWeightCount + lowWeightCount;
        if (normalWordCount > adWordCount) {
            adjustedScore = adjustedScore * 0.2f;
        }

        //wd 根据主题类型额外调整
        float finalScore;
        switch (topicType) {
            case CHAT:
            case GREETING:
                //wd 闲聊和问候主题，大幅降低得分
                finalScore = adjustedScore * 0.3f;
                break;
            case CONSULTATION:
                //wd 咨询主题，降低得分
                finalScore = adjustedScore * 0.5f;
                break;
            case NOTIFICATION:
                //wd 通知主题，轻微降低
                finalScore = adjustedScore * 0.7f;
                break;
            default:
                finalScore = adjustedScore;
                break;
        }

        FileLog.d("wd MessageTopicAnalyzer: 规则引擎得分 - raw=" + String.format("%.2f", rawScore) +
            " high=" + highWeightCount + " medium=" + mediumWeightCount + " low=" + lowWeightCount +
            " normal=" + normalWordCount + " final=" + String.format("%.2f", finalScore));

        //wd 确保分数在0-1范围内
        return Math.max(0f, Math.min(1f, finalScore));
    }

    //wd 生成主题总结
    //wd 基于主题类型和关键词提取核心内容
    private String generateTopicSummary(String text, TopicType topicType) {
        StringBuilder summary = new StringBuilder();
        summary.append(topicType.toString().toLowerCase());

        //wd 提取关键词
        String lowerText = text.toLowerCase();
        String[] keywords = {"博彩", "跑分", "上分", "充值", "返利", "微信", "qq", "优惠", "兼职", "赚钱"};
        for (String keyword : keywords) {
            if (lowerText.contains(keyword)) {
                summary.append(", ").append(keyword);
            }
        }

        return summary.toString();
    }

    // ========== 规则引擎方法（作为降级方案）==========

    private String normalizeText(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        normalized = normalized.replaceAll("[\\p{Cf}]+", "");
        normalized = normalized.replaceAll("[\\p{Z}]+", " ");
        // 处理常见异型字
        normalized = normalized.replace("薇", "微")
                              .replace("丄", "上")
                              .replace("沖", "冲")
                              .replace("値", "值")
                              .replace("玳", "代")
                              .replace("菠", "博")
                              .replace("菜", "彩");
        return normalized.trim();
    }

    private Set<String> extractEntities(String text) {
        Set<String> entities = new HashSet<>();
        
        Pattern[] patterns = {
            Pattern.compile("(?:微信|VX|wx|WeChat|薇信)[：:]?\\s*([a-zA-Z0-9_\\-]+)"),
            Pattern.compile("(?:QQ|qq)[：:]?\\s*([0-9]+)"),
            Pattern.compile("(?:TG|Telegram|电报)[@：:]?\\s*([a-zA-Z0-9_]+)"),
            Pattern.compile("@([a-zA-Z0-9_]{5,32})"),
            Pattern.compile("(https?://[\\w\\-./?%&=]+)"),
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group(1);
                if (match != null && !match.isEmpty()) {
                    entities.add("CONTACT:" + match);
                }
            }
        }

        return entities;
    }

    private TopicType analyzeTopicTypeRuleBased(String lowerText, Set<String> entities) {
        String[] promoKeywords = {"优惠", "折扣", "特价", "促销", "活动", "免费",
            "赠送", "福利", "返利", "充值", "首充", "代理", "加盟", "赚钱", "兼职"};
        String[] transKeywords = {"上分", "下分", "出分", "跑分", "USDT", "OTC", "费率"};
        String[] consultKeywords = {"请问", "问一下", "咨询", "了解", "怎么", "如何"};

        int promoCount = countKeywords(lowerText, promoKeywords);
        int transCount = countKeywords(lowerText, transKeywords);
        int consultCount = countKeywords(lowerText, consultKeywords);

        boolean hasContact = entities.stream().anyMatch(e -> e.startsWith("CONTACT:"));

        if (promoCount >= 2 || (promoCount >= 1 && hasContact)) {
            return TopicType.PROMOTION;
        }
        if (transCount >= 1) return TopicType.TRANSACTION;
        if (consultCount >= 1 && promoCount == 0) return TopicType.CONSULTATION;

        return TopicType.UNKNOWN;
    }

    private IntentType analyzeIntentTypeRuleBased(String lowerText) {
        String[] urgeWords = {"马上", "立即", "速来", "快来", "抓紧", "机不可失"};
        String[] offerWords = {"提供", "可以", "能", "支持"};
        String[] inviteWords = {"加入", "联系", "加", "私聊"};

        for (String word : urgeWords) if (lowerText.contains(word)) return IntentType.URGE;
        for (String word : offerWords) if (lowerText.contains(word)) return IntentType.OFFER;
        for (String word : inviteWords) if (lowerText.contains(word)) return IntentType.INVITE;

        return IntentType.UNKNOWN;
    }

    private float calculatePromotionScore(String lowerText, Set<String> entities) {
        float score = 0f;
        String[] promoWords = {"优惠", "折扣", "免费", "赠送", "返利", "充值", "代理", "赚钱"};

        for (String word : promoWords) {
            if (lowerText.contains(word)) score += 0.1f;
        }

        if (entities.stream().anyMatch(e -> e.startsWith("CONTACT:"))) score += 0.2f;

        return Math.min(1.0f, score);
    }

    private Map<String, Float> extractFeatures(String text, Set<String> entities) {
        Map<String, Float> features = new HashMap<>();
        features.put("text_length", (float) text.length());
        features.put("contact_count", (float) entities.stream()
            .filter(e -> e.startsWith("CONTACT:")).count());
        return features;
    }

    private float calculateAdProbabilityRuleBased(TopicType topicType, String lowerText) {
        float probability = 0f;

        // 根据主题类型设置基础概率
        switch (topicType) {
            case PROMOTION: probability += 0.35f; break;
            case TRANSACTION: probability += 0.30f; break;
            case CONSULTATION: probability -= 0.25f; break;
            case CHAT: probability -= 0.35f; break;
            case GREETING: probability -= 0.30f; break;
            case NOTIFICATION: probability -= 0.20f; break;
        }

        // 强广告关键词（大幅加分）
        String[] strongAdWords = {"博彩", "菠菜", "赌博", "赌球", " casino", "bet365", "百家乐", "老虎机",
            "跑分", "上分", "下分", "USDT", "虚拟货币", "合约带单", "币圈", "空投"};
        for (String word : strongAdWords) {
            if (lowerText.contains(word)) {
                probability += 0.25f;
                break;
            }
        }

        // 中等广告关键词（适度加分）
        String[] mediumAdWords = {"充值返利", "首充", "代理招募", "月入过万", "稳赚不赔", "高额返佣",
            "兼职刷单", "日赚", "秒到账", "信誉担保", "内部消息", "限时优惠", "机不可失"};
        for (String word : mediumAdWords) {
            if (lowerText.contains(word)) {
                probability += 0.15f;
                break;
            }
        }

        // 正常聊天词汇（大幅减分）
        String[] normalWords = {"我在", "我是", "我们", "讨论", "请问", "求助", "谢谢", "你好",
            "最近怎么样", "天气", "周末", "计划", "项目", "进度", "会议", "报告",
            "文件", "邮件", "收到", "知道了", "辛苦了", "晚安", "早安", "生日快乐"};
        for (String word : normalWords) {
            if (lowerText.contains(word)) {
                probability -= 0.20f;
                break;
            }
        }

        // 个人生活词汇（减分）
        String[] personalWords = {"搬家", "婚礼", "感冒", "生病", "孩子", "宠物", "猫咪", "狗狗",
            "医院", "医生", "牙医", "看病", "吃药", "减肥", "健身", "电影", "演唱会",
            "高铁", "机票", "酒店", "旅游", "爬山", "烘焙", "做饭", "买菜"};
        for (String word : personalWords) {
            if (lowerText.contains(word)) {
                probability -= 0.15f;
                break;
            }
        }

        // 疑问词（咨询类特征，减分）
        String[] questionWords = {"吗？", "？", "多少", "几点", "哪里", "怎么", "如何", "什么",
            "为什么", "能否", "可以", "行吗"};
        int questionCount = 0;
        for (String word : questionWords) {
            if (lowerText.contains(word)) {
                questionCount++;
            }
        }
        if (questionCount >= 1) {
            probability -= 0.10f;
        }

        return Math.max(0f, Math.min(1f, probability));
    }

    //wd 生成消息摘要
    //wd 包含主题类型、实体数量和内容预览
    private String generateSummary(String text, TopicType topicType, Set<String> entities, String topicSummary) {
        return String.format("主题:%s, 总结:%s, 实体数:%d, 预览:%s",
            topicType, topicSummary, entities.size(),
            text.length() > 30 ? text.substring(0, 30) + "..." : text);
    }

    private int countKeywords(String text, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) count++;
        }
        return count;
    }

    //wd 统计单个关键词在文本中出现次数
    //wd 用于支持同一关键词多次出现的累加得分机制
    private int countKeywordOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    private TopicType parseTopicType(String topic) {
        switch (topic.toLowerCase()) {
            case "chat": return TopicType.CHAT;
            case "consultation": return TopicType.CONSULTATION;
            case "promotion": return TopicType.PROMOTION;
            case "notification": return TopicType.NOTIFICATION;
            case "transaction": return TopicType.TRANSACTION;
            case "recruitment": return TopicType.RECRUITMENT;
            case "greeting": return TopicType.GREETING;
            default: return TopicType.UNKNOWN;
        }
    }

    private TopicAnalysis createEmptyAnalysis() {
        return new TopicAnalysis(
            TopicType.UNKNOWN, IntentType.UNKNOWN,
            0f, 0f, new HashSet<>(), new HashMap<>(),
            "", false, false
        );
    }

    public boolean isTopicModelLoaded() {
        return topicModelLoaded.get() && topicInterpreter != null;
    }

    //wd 检查覆盖率模型是否已加载
    public boolean isCoverageModelLoaded() {
        return coverageModelLoaded.get() && coverageInterpreter != null;
    }

    //wd 释放模型资源
    public void release() {
        executor.shutdownNow();
        synchronized (modelLock) {
            if (topicInterpreter != null) {
                topicInterpreter.close();
                topicInterpreter = null;
            }
            if (coverageInterpreter != null) {
                coverageInterpreter.close();
                coverageInterpreter = null;
            }
            topicModelBuffer = null;
            coverageModelBuffer = null;
        }
        topicModelLoaded.set(false);
        coverageModelLoaded.set(false);
    }
}
