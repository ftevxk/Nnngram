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
import java.io.IOException;
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
    
    // 广告分类模型
    private Interpreter adInterpreter;
    private MappedByteBuffer adModelBuffer;
    private List<String> adLabels;
    private final AtomicBoolean adModelLoaded = new AtomicBoolean(false);
    
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

    // 模型路径
    private static final String TOPIC_MODEL_PATH = "ai_ad_filter/topic_model.tflite";
    private static final String TOPIC_LABELS_PATH = "ai_ad_filter/topic_labels.txt";
    private static final String TOPIC_VOCAB_PATH = "ai_ad_filter/topic_vocabulary.txt";
    private static final String TOPIC_IDF_PATH = "ai_ad_filter/topic_idf.txt";
    private static final String AD_MODEL_PATH = "ai_ad_filter/model.tflite";
    private static final String AD_LABELS_PATH = "ai_ad_filter/labels.txt";
    private static final String AD_VOCAB_PATH = "ai_ad_filter/ad_vocabulary.txt";
    private static final String AD_IDF_PATH = "ai_ad_filter/ad_idf.txt";
    
    // TF-IDF 向量化器
    private TfidfVectorizer topicVectorizer;
    private TfidfVectorizer adVectorizer;

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
        // 加载主题分析模型
        loadTopicModel();
        // 加载广告分类模型
        loadAdModel();
        isInitializing.set(false);
    }

    private void loadTopicModel() {
        synchronized (modelLock) {
            try {
                FileLog.d("wd MessageTopicAnalyzer: loading topic model from " + TOPIC_MODEL_PATH);
                topicModelBuffer = loadModelFile(TOPIC_MODEL_PATH);
                if (topicModelBuffer == null) {
                    FileLog.e("wd MessageTopicAnalyzer: topic model file not found");
                    return;
                }

                topicLabels = loadLabels(TOPIC_LABELS_PATH);
                if (topicLabels == null || topicLabels.isEmpty()) {
                    FileLog.e("wd MessageTopicAnalyzer: failed to load topic labels");
                    return;
                }
                FileLog.d("wd MessageTopicAnalyzer: loaded " + topicLabels.size() + " topic labels: " + topicLabels);

                // 加载 TF-IDF 向量化器
                try {
                    topicVectorizer = new TfidfVectorizer(context, TOPIC_VOCAB_PATH, TOPIC_IDF_PATH);
                    FileLog.d("wd MessageTopicAnalyzer: topic vectorizer loaded, feature size=" + topicVectorizer.getFeatureSize());
                } catch (IOException e) {
                    FileLog.e("wd MessageTopicAnalyzer: failed to load topic vectorizer", e);
                    return;
                }

                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(2);
                topicInterpreter = new Interpreter(topicModelBuffer, options);

                topicModelLoaded.set(true);
                FileLog.d("wd MessageTopicAnalyzer: topic model loaded successfully");
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: topic model initialization error", e);
            }
        }
    }

    private void loadAdModel() {
        synchronized (modelLock) {
            try {
                FileLog.d("wd MessageTopicAnalyzer: loading ad model from " + AD_MODEL_PATH);
                adModelBuffer = loadModelFile(AD_MODEL_PATH);
                if (adModelBuffer == null) {
                    FileLog.e("wd MessageTopicAnalyzer: ad model file not found");
                    return;
                }

                adLabels = loadLabels(AD_LABELS_PATH);
                if (adLabels == null || adLabels.isEmpty()) {
                    FileLog.e("wd MessageTopicAnalyzer: failed to load ad labels");
                    return;
                }
                FileLog.d("wd MessageTopicAnalyzer: loaded " + adLabels.size() + " ad labels: " + adLabels);

                // 加载 TF-IDF 向量化器
                try {
                    adVectorizer = new TfidfVectorizer(context, AD_VOCAB_PATH, AD_IDF_PATH);
                    FileLog.d("wd MessageTopicAnalyzer: ad vectorizer loaded, feature size=" + adVectorizer.getFeatureSize());
                } catch (IOException e) {
                    FileLog.e("wd MessageTopicAnalyzer: failed to load ad vectorizer", e);
                    return;
                }

                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(2);
                adInterpreter = new Interpreter(adModelBuffer, options);

                adModelLoaded.set(true);
                FileLog.d("wd MessageTopicAnalyzer: ad model loaded successfully");
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: ad model initialization error", e);
            }
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
            FileLog.e("wd MessageTopicAnalyzer: failed to load labels from " + path, e);
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
                FileLog.d("wd MessageTopicAnalyzer: topic classified by model: " + topicType);
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: topic model classification failed", e);
            }
        }
        
        // 如果主题模型失败，使用规则引擎
        if (topicType == TopicType.UNKNOWN) {
            topicType = analyzeTopicTypeRuleBased(normalizedText.toLowerCase(), extractEntities(normalizedText));
        }
        
        // 阶段2: 广告分类
        float adProbability = 0f;
        boolean fromAdModel = false;
        
        if (adModelLoaded.get() && adInterpreter != null) {
            try {
                adProbability = classifyAd(normalizedText);
                fromAdModel = true;
                FileLog.d("wd MessageTopicAnalyzer: ad probability from model: " + adProbability);
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: ad model classification failed", e);
            }
        }
        
        // 如果广告模型失败，使用规则引擎
        if (!fromAdModel) {
            adProbability = calculateAdProbabilityRuleBased(topicType, normalizedText.toLowerCase());
        }
        
        // 提取实体和特征
        Set<String> entities = extractEntities(normalizedText);
        Map<String, Float> features = extractFeatures(normalizedText, entities);
        
        // 计算推广分数
        float promotionScore = calculatePromotionScore(normalizedText.toLowerCase(), entities);
        
        // 分析意图
        IntentType intentType = analyzeIntentTypeRuleBased(normalizedText.toLowerCase());
        
        // 生成摘要
        String summary = generateSummary(normalizedText, topicType, entities);

        return new TopicAnalysis(
            topicType, intentType, promotionScore, adProbability,
            entities, features, summary, fromTopicModel, fromAdModel);
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
                    FileLog.e("wd MessageTopicAnalyzer: invalid input shape from vectorizer");
                    return TopicType.UNKNOWN;
                }
                
                // 准备输出缓冲区
                float[][] output = new float[1][topicLabels.size()];
                
                // 运行模型
                topicInterpreter.run(input, output);
                
                // 验证输出
                if (output == null || output.length == 0 || output[0].length == 0) {
                    FileLog.e("wd MessageTopicAnalyzer: model returned empty output");
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
                
                FileLog.d("wd MessageTopicAnalyzer: topic classification result - index=" + maxIndex + 
                    ", prob=" + maxProb + ", label=" + (maxIndex < topicLabels.size() ? topicLabels.get(maxIndex) : "unknown"));
                
                // 转换为TopicType
                if (maxIndex < topicLabels.size()) {
                    return parseTopicType(topicLabels.get(maxIndex));
                }
                return TopicType.UNKNOWN;
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: topic classification failed", e);
                return TopicType.UNKNOWN;
            }
        }
    }

    private float classifyAd(String text) {
        synchronized (modelLock) {
            if (adInterpreter == null || adLabels == null || adVectorizer == null) {
                FileLog.w("wd MessageTopicAnalyzer: ad model not ready");
                return 0f;
            }

            try {
                // 使用 TF-IDF 向量化器编码文本
                float[][] input = adVectorizer.transform(text);
                
                // 验证输入形状
                if (input == null || input.length == 0 || input[0].length == 0) {
                    FileLog.e("wd MessageTopicAnalyzer: invalid input shape from ad vectorizer");
                    return 0f;
                }
                
                // 准备输出缓冲区
                float[][] output = new float[1][adLabels.size()];
                
                // 运行模型
                adInterpreter.run(input, output);
                
                // 验证输出
                if (output == null || output.length == 0 || output[0].length == 0) {
                    FileLog.e("wd MessageTopicAnalyzer: ad model returned empty output");
                    return 0f;
                }
                
                // 找到ad类别的概率
                for (int i = 0; i < adLabels.size(); i++) {
                    if ("ad".equalsIgnoreCase(adLabels.get(i))) {
                        float adProb = output[0][i];
                        FileLog.d("wd MessageTopicAnalyzer: ad classification result - prob=" + adProb);
                        return adProb;
                    }
                }
                return 0f;
            } catch (Exception e) {
                FileLog.e("wd MessageTopicAnalyzer: ad classification failed", e);
                return 0f;
            }
        }
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

        switch (topicType) {
            case PROMOTION: probability += 0.4f; break;
            case TRANSACTION: probability += 0.35f; break;
            case CONSULTATION: probability -= 0.2f; break;
            case CHAT: probability -= 0.3f; break;
        }

        String[] normalWords = {"我在", "我是", "我们", "讨论", "请问", "求助"};
        for (String word : normalWords) {
            if (lowerText.contains(word)) {
                probability -= 0.15f;
                break;
            }
        }

        return Math.max(0f, Math.min(1f, probability));
    }

    private String generateSummary(String text, TopicType topicType, Set<String> entities) {
        return String.format("主题:%s, 实体数:%d, 预览:%s",
            topicType, entities.size(),
            text.length() > 30 ? text.substring(0, 30) + "..." : text);
    }

    private int countKeywords(String text, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) count++;
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

    public boolean isAdModelLoaded() {
        return adModelLoaded.get() && adInterpreter != null;
    }

    public void release() {
        executor.shutdownNow();
        synchronized (modelLock) {
            if (topicInterpreter != null) {
                topicInterpreter.close();
                topicInterpreter = null;
            }
            if (adInterpreter != null) {
                adInterpreter.close();
                adInterpreter = null;
            }
            topicModelBuffer = null;
            adModelBuffer = null;
        }
        topicModelLoaded.set(false);
        adModelLoaded.set(false);
    }
}
