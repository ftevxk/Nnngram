/*
 * Copyright (C) 2024 Nnngram
 * AI广告内容分析器
 * 整合关键词提取和特征库比对，判断消息是否为广告
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//wd AI广告内容分析器
//wd 整合关键词提取和特征库比对，提供完整的广告内容分析功能
public class AiAdContentAnalyzer {

    private static volatile AiAdContentAnalyzer instance;

    //wd 关键词提取器
    private AiKeywordExtractor keywordExtractor;

    //wd 特征库
    private AiAdFeatureLibrary featureLibrary;

    //wd 上下文
    private Context context;

    //wd 分析阈值（提高到0.8，需要多个高权重词才能触发）
    private float threshold = 0.8f;

    //wd 最小关键词数要求（至少2个关键词才判定为广告）
    private static final int MIN_KEYWORD_COUNT = 2;

    //wd 私有构造函数
    private AiAdContentAnalyzer() {
        keywordExtractor = AiKeywordExtractor.getInstance();
        featureLibrary = AiAdFeatureLibrary.getInstance();
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
        keywordExtractor.init(context);
        featureLibrary.init(context);
    }

    //wd 设置判定阈值
    public void setThreshold(float threshold) {
        this.threshold = Math.max(0f, Math.min(1f, threshold));
    }

    //wd 获取当前阈值
    public float getThreshold() {
        return threshold;
    }

    //wd 分析消息内容
    //wd 步骤1: 提取关键词及频次
    //wd 步骤2: 与特征库比对打分
    //wd 步骤3: 频次加权计算最终得分
    @NonNull
    public AnalysisResult analyze(String text) {
        if (TextUtils.isEmpty(text)) {
            return new AnalysisResult(false, 0f, Collections.emptyList(), "空文本");
        }

        FileLog.d("wd AiAdContentAnalyzer 开始分析消息");

        //wd 步骤1: 提取关键词
        List<AiKeywordExtractor.ExtractedKeyword> extractedKeywords = keywordExtractor.extractKeywords(text);
        if (extractedKeywords.isEmpty()) {
            FileLog.d("wd AiAdContentAnalyzer 未提取到关键词，判定为正常消息");
            return new AnalysisResult(false, 0f, Collections.emptyList(), "未提取到广告关键词");
        }

        FileLog.d("wd AiAdContentAnalyzer 提取到 " + extractedKeywords.size() + " 个关键词");

        //wd 步骤2 & 3: 计算得分
        float score = calculateScore(extractedKeywords);

        //wd 判定是否为广告
        boolean isAd = score >= threshold;

        //wd 构建结果
        List<MatchedKeyword> matchedKeywords = new ArrayList<>();
        for (AiKeywordExtractor.ExtractedKeyword kw : extractedKeywords) {
            matchedKeywords.add(new MatchedKeyword(kw.keyword, kw.weight, kw.frequency));
        }

        String reason = isAd ? "广告关键词匹配得分=" + String.format("%.2f", score) : "正常消息";

        FileLog.d("wd AiAdContentAnalyzer 分析完成: 得分=" + score + " 阈值=" + threshold + " 是广告=" + isAd);

        return new AnalysisResult(isAd, score, matchedKeywords, reason);
    }

    //wd 计算得分
    //wd 使用概率并集公式：P = 1 - ∏(1 - wi)
    //wd 这样可以让总分趋近于1但不会超过1，需要多个高权重词才能达到高分数
    private float calculateScore(List<AiKeywordExtractor.ExtractedKeyword> extractedKeywords) {
        int keywordCount = extractedKeywords.size();

        //wd 如果关键词数量不足，直接返回低分
        if (keywordCount < MIN_KEYWORD_COUNT) {
            FileLog.d("wd AiAdContentAnalyzer 关键词数量不足(" + keywordCount + "<" + MIN_KEYWORD_COUNT + ")，判定为正常");
            return 0f;
        }

        //wd 使用概率并集公式计算得分
        //wd P = 1 - (1-w1)*(1-w2)*...*(1-wn)
        double notMatchProb = 1.0; // 不匹配的概率乘积

        for (AiKeywordExtractor.ExtractedKeyword kw : extractedKeywords) {
            //wd 检查特征库中是否有该关键词
            AiAdKeywordFeature feature = featureLibrary.getFeature(kw.keyword);

            float weight;
            if (feature != null) {
                //wd 使用特征库中的权重（考虑频次）
                weight = feature.getEffectiveWeight();
                FileLog.d("wd AiAdContentAnalyzer 特征库匹配: " + kw.keyword + " 权重=" + weight);
            } else {
                //wd 使用提取的权重
                weight = kw.weight;
                FileLog.d("wd AiAdContentAnalyzer 规则提取: " + kw.keyword + " 权重=" + weight);
            }

            //wd 频次加权：权重 * (1 + log(频次))
            //wd 这样频次影响不会太大
            float freqFactor = 1f + (float) Math.log1p(kw.frequency);
            float adjustedWeight = Math.min(0.95f, weight * freqFactor); // 最高0.95，避免100%

            //wd 概率并集：乘以(1 - wi)
            notMatchProb *= (1.0 - adjustedWeight);
        }

        //wd 最终得分 = 1 - 不匹配概率
        float finalScore = (float) (1.0 - notMatchProb);

        FileLog.d("wd AiAdContentAnalyzer 得分计算: 关键词数=" + keywordCount +
                " 不匹配概率=" + String.format("%.4f", notMatchProb) +
                " 最终得分=" + String.format("%.4f", finalScore));

        return finalScore;
    }

    //wd 提取关键词并添加到特征库（用于长按菜单功能）
    //wd 返回提取的关键词列表，供用户选择
    @NonNull
    public List<AiKeywordExtractor.ExtractedKeyword> extractKeywordsForFeatureLibrary(String text) {
        if (TextUtils.isEmpty(text)) {
            return Collections.emptyList();
        }

        //wd 提取关键词
        List<AiKeywordExtractor.ExtractedKeyword> keywords = keywordExtractor.extractKeywords(text);

        //wd 过滤掉已经在特征库中的关键词（或者更新频次）
        List<AiKeywordExtractor.ExtractedKeyword> filteredKeywords = new ArrayList<>();
        for (AiKeywordExtractor.ExtractedKeyword kw : keywords) {
            AiAdKeywordFeature existing = featureLibrary.getFeature(kw.keyword);
            if (existing == null) {
                //wd 新关键词，添加到列表
                filteredKeywords.add(kw);
            } else {
                //wd 已存在，如果权重更高也显示
                if (kw.weight > existing.weight) {
                    filteredKeywords.add(kw);
                }
            }
        }

        return filteredKeywords;
    }

    //wd 将提取的关键词添加到特征库
    public boolean addKeywordsToFeatureLibrary(List<AiKeywordExtractor.ExtractedKeyword> keywords, String source) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        List<AiAdKeywordFeature> features = new ArrayList<>();
        for (AiKeywordExtractor.ExtractedKeyword kw : keywords) {
            features.add(kw.toFeature(source));
        }

        featureLibrary.addFeatures(features);
        FileLog.d("wd AiAdContentAnalyzer 已添加 " + features.size() + " 个关键词到特征库");

        return true;
    }

    //wd 检查模型是否已加载
    public boolean isReady() {
        return featureLibrary.isLoaded();
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
