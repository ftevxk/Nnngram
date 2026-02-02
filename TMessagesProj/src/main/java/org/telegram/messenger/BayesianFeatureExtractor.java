/*
 * Copyright (C) 2024 Nnngram
 * 贝叶斯特征提取器
 * 从文本中提取TF-IDF加权的n-gram特征
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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//wd 贝叶斯特征提取器
//wd 从文本中提取n-gram特征，并计算TF-IDF权重
public class BayesianFeatureExtractor {

    private static volatile BayesianFeatureExtractor instance;

    //wd 上下文
    private Context context;

    //wd n-gram范围
    private static final int MIN_NGRAM = 1;
    private static final int MAX_NGRAM = 3;

    //wd IDF值缓存
    private Map<String, Double> idfCache;

    //wd 文档频率统计（用于计算IDF）
    private Map<String, Integer> documentFrequency;

    //wd 总文档数
    private int totalDocuments = 0;

    //wd 私有构造函数
    private BayesianFeatureExtractor() {
        idfCache = new HashMap<>();
        documentFrequency = new HashMap<>();
    }

    //wd 获取单例实例
    public static BayesianFeatureExtractor getInstance() {
        if (instance == null) {
            synchronized (BayesianFeatureExtractor.class) {
                if (instance == null) {
                    instance = new BayesianFeatureExtractor();
                }
            }
        }
        return instance;
    }

    //wd 初始化提取器
    public void init(Context context) {
        this.context = context.getApplicationContext();
        //wd 从概率表加载IDF值
        loadIdfFromProbabilityTable();
    }

    //wd 从概率表加载IDF值
    private void loadIdfFromProbabilityTable() {
        BayesianProbabilityTable probTable = BayesianProbabilityTable.getInstance();
        if (!probTable.isLoaded()) {
            return;
        }

        //wd 使用概率表中的词频计算IDF
        //wd IDF = log(总文档数 / 包含该词的文档数)
        //wd 这里简化为使用类别分布计算
        int totalFeatures = probTable.getVocabularySize();
        if (totalFeatures == 0) {
            return;
        }

        for (String feature : probTable.getAllFeatures()) {
            int adCount = probTable.getFeatureCount(feature, BayesianProbabilityTable.CLASS_AD);
            int normalCount = probTable.getFeatureCount(feature, BayesianProbabilityTable.CLASS_NORMAL);
            int docCount = (adCount > 0 ? 1 : 0) + (normalCount > 0 ? 1 : 0);

            //wd 简化IDF计算
            double idf = Math.log((2.0 + 1.0) / (docCount + 1.0)) + 1.0;
            idfCache.put(feature, idf);
        }

        FileLog.d("wd BayesianFeatureExtractor IDF缓存加载完成，共 " + idfCache.size() + " 个词");
    }

    //wd 提取特征
    //wd 返回特征列表，包含特征词和TF-IDF权重
    @NonNull
    public List<Feature> extractFeatures(String text) {
        if (TextUtils.isEmpty(text)) {
            return Collections.emptyList();
        }

        //wd 标准化文本
        String normalizedText = normalizeText(text);

        //wd 分词
        List<String> tokens = tokenize(normalizedText);

        //wd 提取n-gram
        List<String> ngrams = extractNgrams(tokens, MIN_NGRAM, MAX_NGRAM);

        //wd 计算TF-IDF
        List<Feature> features = calculateTfIdf(ngrams);

        //wd 按权重排序
        Collections.sort(features, (a, b) -> Double.compare(b.weight, a.weight));

        FileLog.d("wd BayesianFeatureExtractor 提取到 " + features.size() + " 个特征");
        return features;
    }

    //wd 标准化文本
    @NonNull
    private String normalizeText(String text) {
        //wd Unicode标准化
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);

        //wd 移除控制字符
        normalized = normalized.replaceAll("[\\p{Cf}]+", "");

        //wd 统一空白字符
        normalized = normalized.replaceAll("[\\p{Z}]+", " ");

        //wd 处理常见异型字和变体
        normalized = normalized.replace("薇", "微")
                .replace("丄", "上")
                .replace("沖", "冲")
                .replace("値", "值")
                .replace("玳", "代")
                .replace("菠", "博")
                .replace("菜", "彩")
                .replace("Ⓑ", "B")
                .replace("Ⓒ", "C")
                .replace("v.x", "微信")
                .replace("vx", "微信")
                .replace("V.X", "微信")
                .replace("VX", "微信")
                .replace("wechat", "微信")
                .replace("WeChat", "微信");

        return normalized.trim().toLowerCase();
    }

    //wd 分词
    @NonNull
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isChinese(c)) {
                //wd 中文单字作为一个token
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString());
                    currentWord.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                currentWord.append(c);
            } else {
                //wd 非字母数字字符作为分隔符
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString());
                    currentWord.setLength(0);
                }
            }
        }

        //wd 处理最后一个词
        if (currentWord.length() > 0) {
            tokens.add(currentWord.toString());
        }

        return tokens;
    }

    //wd 判断是否为中文
    private boolean isChinese(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    //wd 提取n-gram
    @NonNull
    private List<String> extractNgrams(List<String> tokens, int minN, int maxN) {
        List<String> ngrams = new ArrayList<>();

        for (int n = minN; n <= maxN && n <= tokens.size(); n++) {
            for (int i = 0; i <= tokens.size() - n; i++) {
                StringBuilder ngram = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) {
                        ngram.append("_");
                    }
                    ngram.append(tokens.get(i + j));
                }
                ngrams.add(ngram.toString());
            }
        }

        return ngrams;
    }

    //wd 计算TF-IDF
    @NonNull
    private List<Feature> calculateTfIdf(List<String> ngrams) {
        if (ngrams.isEmpty()) {
            return Collections.emptyList();
        }

        //wd 计算词频(TF)
        Map<String, Integer> termFreq = new HashMap<>();
        for (String ngram : ngrams) {
            termFreq.put(ngram, termFreq.getOrDefault(ngram, 0) + 1);
        }

        //wd 计算TF-IDF
        List<Feature> features = new ArrayList<>();
        int totalTerms = ngrams.size();

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int freq = entry.getValue();

            //wd TF = 词频 / 总词数
            double tf = (double) freq / totalTerms;

            //wd IDF（从缓存或默认值）
            double idf = idfCache.getOrDefault(term, 1.0);

            //wd TF-IDF权重
            double weight = tf * idf;

            features.add(new Feature(term, weight, freq));
        }

        return features;
    }

    //wd 提取关键词（用于特征库编辑）
    @NonNull
    public List<ExtractedKeyword> extractKeywords(String text) {
        List<Feature> features = extractFeatures(text);
        List<ExtractedKeyword> keywords = new ArrayList<>();

        //wd 只返回高权重特征（潜在广告词）
        for (Feature feature : features) {
            //wd 过滤单字（除非是高频广告词）
            if (feature.term.length() >= 2 || feature.frequency >= 2) {
                //wd 根据TF-IDF权重映射到0.5-0.95范围
                float weight = (float) Math.min(0.95, 0.5 + feature.weight * 2);
                keywords.add(new ExtractedKeyword(feature.term, weight, feature.frequency));
            }
        }

        return keywords;
    }

    //wd 更新文档频率（用于在线学习）
    public void updateDocumentFrequency(List<String> features, String className) {
        totalDocuments++;

        for (String feature : features) {
            documentFrequency.put(feature, documentFrequency.getOrDefault(feature, 0) + 1);

            //wd 更新IDF缓存
            int df = documentFrequency.get(feature);
            double idf = Math.log((double) (totalDocuments + 1) / (df + 1)) + 1.0;
            idfCache.put(feature, idf);
        }
    }

    //wd 获取IDF值
    public double getIdf(String term) {
        return idfCache.getOrDefault(term, 1.0);
    }

    //wd 特征类
    public static class Feature {
        public final String term;
        public final double weight;
        public final int frequency;

        public Feature(String term, double weight, int frequency) {
            this.term = term;
            this.weight = weight;
            this.frequency = frequency;
        }

        @NonNull
        @Override
        public String toString() {
            return "Feature{term='" + term + "', weight=" + weight + ", freq=" + frequency + "}";
        }
    }

    //wd 提取的关键词类
    public static class ExtractedKeyword {
        public final String keyword;
        public final float weight;
        public final int frequency;

        public ExtractedKeyword(String keyword, float weight, int frequency) {
            this.keyword = keyword;
            this.weight = weight;
            this.frequency = frequency;
        }

        @NonNull
        @Override
        public String toString() {
            return "ExtractedKeyword{keyword='" + keyword + "', weight=" + weight + ", freq=" + frequency + "}";
        }
    }
}