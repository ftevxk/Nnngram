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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    //wd 分析阈值（降低到0.65，需要多个高权重词才能触发）
    private float threshold = 0.65f;

    //wd 最小关键词数要求（降低到1个，允许单关键词高权重触发）
    private static final int MIN_KEYWORD_COUNT = 1;

    //wd 组合加分阈值（当同时出现多个推广词时给予额外加分）
    private static final float COMBO_BONUS_THRESHOLD = 0.5f;

    //wd 推广类关键词权重范围（用于识别新用户推广类关键词）
    private static final float PROMOTION_WEIGHT_MIN = 0.60f;
    private static final float PROMOTION_WEIGHT_MAX = 0.80f;

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
        FileLog.d("wd AiAdContentAnalyzer 设置阈值为 " + this.threshold);
    }

    //wd 获取当前阈值
    public float getThreshold() {
        return threshold;
    }

    //wd 分析消息内容
    //wd 步骤1: 提取关键词及频次
    //wd 步骤2: 与特征库比对打分
    //wd 步骤3: 频次加权计算最终得分
    //wd 步骤4: 检测组合特征并给予额外加分
    @NonNull
    public AnalysisResult analyze(String text) {
        if (TextUtils.isEmpty(text)) {
            return new AnalysisResult(false, 0f, Collections.emptyList(), "空文本");
        }

        FileLog.d("wd AiAdContentAnalyzer 开始分析消息，阈值=" + threshold);

        //wd 步骤1: 提取关键词
        List<AiKeywordExtractor.ExtractedKeyword> extractedKeywords = keywordExtractor.extractKeywords(text);
        if (extractedKeywords.isEmpty()) {
            FileLog.d("wd AiAdContentAnalyzer 未提取到关键词，判定为正常消息");
            return new AnalysisResult(false, 0f, Collections.emptyList(), "未提取到广告关键词");
        }

        FileLog.d("wd AiAdContentAnalyzer 提取到 " + extractedKeywords.size() + " 个关键词");

        //wd 步骤2 & 3: 计算得分
        float score = calculateScore(extractedKeywords);

        //wd 步骤4: 检测新用户推广组合特征
        float comboBonus = calculateComboBonus(extractedKeywords);
        if (comboBonus > 0) {
            score = Math.min(0.99f, score + comboBonus);
            FileLog.d("wd AiAdContentAnalyzer 检测到组合特征，加分=" + comboBonus + "，最终得分=" + score);
        }

        //wd 判定是否为广告
        boolean isAd = score >= threshold;

        //wd 构建结果
        List<MatchedKeyword> matchedKeywords = new ArrayList<>();
        for (AiKeywordExtractor.ExtractedKeyword kw : extractedKeywords) {
            matchedKeywords.add(new MatchedKeyword(kw.keyword, kw.weight, kw.frequency));
        }

        String reason = isAd ? "广告关键词匹配得分=" + String.format("%.2f", score) : "正常消息";

        FileLog.d("wd AiAdContentAnalyzer 分析完成: 得分=" + String.format("%.4f", score) + 
                " 阈值=" + threshold + " 是广告=" + isAd);

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

    //wd 计算组合特征加分
    //wd 当检测到多个新用户推广类关键词同时出现时，给予额外加分
    //wd 推广类关键词通过权重范围识别（0.60-0.80）
    private float calculateComboBonus(List<AiKeywordExtractor.ExtractedKeyword> extractedKeywords) {
        int comboCount = 0;
        float maxWeight = 0f;

        for (AiKeywordExtractor.ExtractedKeyword kw : extractedKeywords) {
            //wd 根据权重范围判断是否为推广类关键词
            if (kw.weight >= PROMOTION_WEIGHT_MIN && kw.weight <= PROMOTION_WEIGHT_MAX) {
                comboCount++;
                maxWeight = Math.max(maxWeight, kw.weight);
                FileLog.d("wd AiAdContentAnalyzer 识别到推广词: " + kw.keyword + " 权重=" + kw.weight);
            }
        }

        //wd 如果同时出现3个及以上推广词，给予额外加分
        if (comboCount >= 3) {
            //wd 基础加分0.15，根据最大权重调整
            float bonus = 0.15f + (maxWeight * 0.1f);
            FileLog.d("wd AiAdContentAnalyzer 检测到3个及以上推广词，加分=" + bonus);
            return bonus;
        } else if (comboCount >= 2) {
            //wd 出现2个，给予较小加分
            float bonus = 0.08f + (maxWeight * 0.05f);
            FileLog.d("wd AiAdContentAnalyzer 检测到2个推广词，加分=" + bonus);
            return bonus;
        }

        return 0f;
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
    public boolean addKeywordsToFeatureLibrary(List<AiKeywordExtractor.ExtractedKeyword> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }

        List<AiAdKeywordFeature> features = new ArrayList<>();
        for (AiKeywordExtractor.ExtractedKeyword kw : keywords) {
            features.add(kw.toFeature());
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
