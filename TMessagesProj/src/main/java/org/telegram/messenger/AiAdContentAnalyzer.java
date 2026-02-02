/*
 * Copyright (C) 2024 Nnngram
 * AI广告内容分析器 - 贝叶斯版本
 * 基于朴素贝叶斯分类器判断消息是否为广告
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//wd AI广告内容分析器 - 贝叶斯版本
//wd 使用朴素贝叶斯分类器进行广告内容分析
public class AiAdContentAnalyzer {

    private static volatile AiAdContentAnalyzer instance;

    //wd 贝叶斯分类器
    private BayesianClassifier bayesianClassifier;

    //wd 特征提取器（用于关键词提取功能）
    private BayesianFeatureExtractor featureExtractor;

    //wd 概率表（用于关键词管理）
    private BayesianProbabilityTable probTable;

    //wd 上下文
    private Context context;

    //wd 分析阈值
    private float threshold = 0.65f;

    //wd 私有构造函数
    private AiAdContentAnalyzer() {
        bayesianClassifier = BayesianClassifier.getInstance();
        featureExtractor = BayesianFeatureExtractor.getInstance();
        probTable = BayesianProbabilityTable.getInstance();
    }

    //wd 获取单例实例
    public static AiAdContentAnalyzer getInstance() {
        if (instance == null) {
            synchronized (AiAdContentAnalyzer.class) {
                if (instance == null) {
                    instance = new AiAdContentAnalyzer();
                }
            }
        }
        return instance;
    }

    //wd 初始化分析器
    public void init(Context context) {
        this.context = context.getApplicationContext();

        //wd 初始化贝叶斯分类器
        bayesianClassifier.init(context);

        FileLog.d("wd AiAdContentAnalyzer 初始化完成（贝叶斯版本）");
    }

    //wd 设置判定阈值
    public void setThreshold(float threshold) {
        this.threshold = Math.max(0f, Math.min(1f, threshold));
        bayesianClassifier.setThreshold(this.threshold);
        FileLog.d("wd AiAdContentAnalyzer 设置阈值为 " + this.threshold);
    }

    //wd 获取当前阈值
    public float getThreshold() {
        return threshold;
    }

    //wd 分析消息内容
    //wd 使用贝叶斯分类器进行分类
    @NonNull
    public AnalysisResult analyze(String text) {
        if (TextUtils.isEmpty(text)) {
            return new AnalysisResult(false, 0f, Collections.emptyList(), "空文本");
        }

        FileLog.d("wd AiAdContentAnalyzer 开始分析消息，阈值=" + threshold);

        //wd 使用贝叶斯分类器进行分类
        BayesianClassifier.ClassificationResult classificationResult = bayesianClassifier.classify(text);

        //wd 转换为分析结果
        boolean isAd = classificationResult.isAd();
        float score = (float) classificationResult.adProbability;

        //wd 构建匹配的关键词列表
        List<MatchedKeyword> matchedKeywords = new ArrayList<>();
        for (BayesianClassifier.MatchedFeature feature : classificationResult.matchedFeatures) {
            matchedKeywords.add(new MatchedKeyword(
                    feature.term,
                    (float) feature.weight,
                    feature.frequency
            ));
        }

        FileLog.d("wd AiAdContentAnalyzer 分析完成: 得分=" + String.format("%.4f", score) +
                " 阈值=" + threshold + " 是广告=" + isAd +
                " 匹配特征数=" + matchedKeywords.size());

        return new AnalysisResult(isAd, score, matchedKeywords, classificationResult.reason);
    }

    //wd 提取关键词（用于长按菜单功能）
    //wd 返回提取的关键词列表，供用户选择添加到概率表
    @NonNull
    public List<AiKeywordExtractor.ExtractedKeyword> extractKeywordsForFeatureLibrary(String text) {
        if (TextUtils.isEmpty(text)) {
            return Collections.emptyList();
        }

        //wd 提取关键词
        List<BayesianFeatureExtractor.ExtractedKeyword> bayesianKeywords = featureExtractor.extractKeywords(text);

        //wd 转换为AiKeywordExtractor.ExtractedKeyword格式以保持兼容性
        List<AiKeywordExtractor.ExtractedKeyword> keywords = new ArrayList<>();
        for (BayesianFeatureExtractor.ExtractedKeyword kw : bayesianKeywords) {
            keywords.add(new AiKeywordExtractor.ExtractedKeyword(kw.keyword, kw.weight, kw.frequency));
        }

        //wd 过滤掉已经在概率表中的关键词（或者更新频次）
        List<AiKeywordExtractor.ExtractedKeyword> filteredKeywords = new ArrayList<>();
        for (AiKeywordExtractor.ExtractedKeyword kw : keywords) {
            int existingCount = probTable.getFeatureCount(kw.keyword.toLowerCase(), BayesianProbabilityTable.CLASS_AD);
            if (existingCount == 0) {
                //wd 新关键词，添加到列表
                filteredKeywords.add(kw);
            } else {
                //wd 已存在，如果权重更高也显示
                if (kw.weight > 0.5f) {
                    filteredKeywords.add(kw);
                }
            }
        }

        return filteredKeywords;
    }

    //wd 将提取的关键词添加到概率表
    public boolean addKeywordsToFeatureLibrary(List<AiKeywordExtractor.ExtractedKeyword> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        for (AiKeywordExtractor.ExtractedKeyword kw : keywords) {
            //wd 更新广告类别的词频
            int count = Math.max(1, kw.frequency);
            int pseudoCount = (int) (count * kw.weight * 10);
            probTable.updateFeatureCount(kw.keyword.toLowerCase(), BayesianProbabilityTable.CLASS_AD, pseudoCount);
        }

        //wd 保存概率表
        probTable.saveProbabilities();

        FileLog.d("wd AiAdContentAnalyzer 已添加 " + keywords.size() + " 个关键词到概率表");
        return true;
    }

    //wd 检查模型是否已加载
    public boolean isReady() {
        return bayesianClassifier.isReady();
    }

    //wd 获取贝叶斯分类器（用于高级功能）
    public BayesianClassifier getBayesianClassifier() {
        return bayesianClassifier;
    }

    //wd 分析结果类
    public static class AnalysisResult {
        public final boolean isAd;
        public final float score;
        public final List<MatchedKeyword> matchedKeywords;
        public final String reason;

        public AnalysisResult(boolean isAd, float score, List<MatchedKeyword> matchedKeywords, String reason) {
            this.isAd = isAd;
            this.score = score;
            this.matchedKeywords = matchedKeywords != null ? matchedKeywords : Collections.emptyList();
            this.reason = reason;
        }
    }

    //wd 匹配的关键词类
    public static class MatchedKeyword {
        public final String keyword;
        public final float weight;
        public final int frequency;

        public MatchedKeyword(String keyword, float weight, int frequency) {
            this.keyword = keyword;
            this.weight = weight;
            this.frequency = frequency;
        }
    }
}
