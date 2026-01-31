/*
 * Copyright (C) 2024 Nnngram
 * 朴素贝叶斯分类器
 * 基于贝叶斯定理进行广告消息分类
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

//wd 朴素贝叶斯分类器
//wd 使用贝叶斯定理计算 P(类别|特征)
//wd 为防止浮点下溢，使用对数概率计算
public class BayesianClassifier {

    private static volatile BayesianClassifier instance;

    //wd 概率表
    private BayesianProbabilityTable probabilityTable;

    //wd 特征提取器
    private BayesianFeatureExtractor featureExtractor;

    //wd 上下文
    private Context context;

    //wd 分类阈值
    private double threshold = 0.65;

    //wd 最小特征数要求
    private static final int MIN_FEATURE_COUNT = 1;

    //wd 对数概率的阈值（用于防止下溢）
    private static final double LOG_PROBABILITY_FLOOR = -50.0;

    //wd 私有构造函数
    private BayesianClassifier() {
        probabilityTable = BayesianProbabilityTable.getInstance();
        featureExtractor = BayesianFeatureExtractor.getInstance();
    }

    //wd 获取单例实例
    public static BayesianClassifier getInstance() {
        if (instance == null) {
            synchronized (BayesianClassifier.class) {
                if (instance == null) {
                    instance = new BayesianClassifier();
                }
            }
        }
        return instance;
    }

    //wd 初始化分类器
    public void init(Context context) {
        this.context = context.getApplicationContext();

        //wd 初始化概率表
        probabilityTable.init(context);

        //wd 初始化特征提取器
        featureExtractor.init(context);

        FileLog.d("wd BayesianClassifier 初始化完成");
    }

    //wd 设置分类阈值
    public void setThreshold(double threshold) {
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
        FileLog.d("wd BayesianClassifier 设置阈值为 " + this.threshold);
    }

    //wd 获取当前阈值
    public double getThreshold() {
        return threshold;
    }

    //wd 分类文本
    //wd 返回分类结果，包含类别、概率和详细信息
    @NonNull
    public ClassificationResult classify(String text) {
        if (TextUtils.isEmpty(text)) {
            return new ClassificationResult(
                    BayesianProbabilityTable.CLASS_NORMAL,
                    0.0,
                    0.0,
                    Collections.emptyList(),
                    "空文本"
            );
        }

        //wd 提取特征
        List<BayesianFeatureExtractor.Feature> features = featureExtractor.extractFeatures(text);

        if (features.isEmpty()) {
            FileLog.d("wd BayesianClassifier 未提取到特征，判定为正常消息");
            return new ClassificationResult(
                    BayesianProbabilityTable.CLASS_NORMAL,
                    0.0,
                    1.0,
                    Collections.emptyList(),
                    "未提取到特征"
            );
        }

        //wd 如果特征数不足，直接返回正常
        if (features.size() < MIN_FEATURE_COUNT) {
            FileLog.d("wd BayesianClassifier 特征数不足(" + features.size() + "<" + MIN_FEATURE_COUNT + ")，判定为正常");
            return new ClassificationResult(
                    BayesianProbabilityTable.CLASS_NORMAL,
                    0.0,
                    1.0,
                    Collections.emptyList(),
                    "特征数不足"
            );
        }

        //wd 计算对数概率
        LogProbabilityResult logProbResult = calculateLogProbability(features);

        //wd 将对数概率转换为概率
        ProbabilityResult probResult = convertLogToProbability(logProbResult);

        //wd 确定分类结果
        String predictedClass;
        double adProbability;
        double confidence;

        if (probResult.adProbability >= threshold) {
            predictedClass = BayesianProbabilityTable.CLASS_AD;
            adProbability = probResult.adProbability;
            confidence = probResult.adProbability;
        } else {
            predictedClass = BayesianProbabilityTable.CLASS_NORMAL;
            adProbability = probResult.adProbability;
            confidence = 1.0 - probResult.adProbability;
        }

        //wd 构建匹配的特征列表（用于调试）
        List<MatchedFeature> matchedFeatures = new ArrayList<>();
        for (BayesianFeatureExtractor.Feature feature : features) {
            double adCondProb = probabilityTable.getConditionalProbability(feature.term, BayesianProbabilityTable.CLASS_AD);
            double normalCondProb = probabilityTable.getConditionalProbability(feature.term, BayesianProbabilityTable.CLASS_NORMAL);
            matchedFeatures.add(new MatchedFeature(feature.term, feature.weight, feature.frequency, adCondProb, normalCondProb));
        }

        String reason = predictedClass.equals(BayesianProbabilityTable.CLASS_AD)
                ? "贝叶斯分类判定为广告，概率=" + String.format("%.4f", adProbability)
                : "贝叶斯分类判定为正常，概率=" + String.format("%.4f", 1 - adProbability);

        FileLog.d("wd BayesianClassifier 分类完成: 类别=" + predictedClass +
                " 广告概率=" + String.format("%.4f", adProbability) +
                " 置信度=" + String.format("%.4f", confidence) +
                " 特征数=" + features.size());

        return new ClassificationResult(predictedClass, adProbability, confidence, matchedFeatures, reason);
    }

    //wd 计算对数概率
    //wd 使用对数防止浮点下溢
    private LogProbabilityResult calculateLogProbability(List<BayesianFeatureExtractor.Feature> features) {
        //wd 获取先验概率的对数
        double logPriorAd = Math.log(probabilityTable.getPriorProbability(BayesianProbabilityTable.CLASS_AD));
        double logPriorNormal = Math.log(probabilityTable.getPriorProbability(BayesianProbabilityTable.CLASS_NORMAL));

        //wd 计算条件概率的对数和
        double logLikelihoodAd = 0.0;
        double logLikelihoodNormal = 0.0;

        for (BayesianFeatureExtractor.Feature feature : features) {
            //wd 获取条件概率
            double condProbAd = probabilityTable.getConditionalProbability(feature.term, BayesianProbabilityTable.CLASS_AD);
            double condProbNormal = probabilityTable.getConditionalProbability(feature.term, BayesianProbabilityTable.CLASS_NORMAL);

            //wd 计算对数概率（使用权重加权）
            double weight = feature.weight;

            //wd 防止log(0)，设置最小值
            double logCondProbAd = Math.log(Math.max(condProbAd, 1e-10));
            double logCondProbNormal = Math.log(Math.max(condProbNormal, 1e-10));

            //wd 加权对数概率
            logLikelihoodAd += weight * logCondProbAd;
            logLikelihoodNormal += weight * logCondProbNormal;

            //wd 防止下溢，设置下限
            if (logLikelihoodAd < LOG_PROBABILITY_FLOOR) {
                logLikelihoodAd = LOG_PROBABILITY_FLOOR;
            }
            if (logLikelihoodNormal < LOG_PROBABILITY_FLOOR) {
                logLikelihoodNormal = LOG_PROBABILITY_FLOOR;
            }
        }

        //wd 总对数概率 = 先验 + 似然
        double logProbAd = logPriorAd + logLikelihoodAd;
        double logProbNormal = logPriorNormal + logLikelihoodNormal;

        FileLog.d("wd BayesianClassifier 对数概率计算: logP(广告)=" + String.format("%.4f", logProbAd) +
                " logP(正常)=" + String.format("%.4f", logProbNormal));

        return new LogProbabilityResult(logProbAd, logProbNormal);
    }

    //wd 将对数概率转换为概率
    //wd 使用softmax转换
    private ProbabilityResult convertLogToProbability(LogProbabilityResult logResult) {
        //wd 为防止溢出，先减去最大值
        double maxLogProb = Math.max(logResult.logProbAd, logResult.logProbNormal);

        double expAd = Math.exp(logResult.logProbAd - maxLogProb);
        double expNormal = Math.exp(logResult.logProbNormal - maxLogProb);

        double sumExp = expAd + expNormal;

        double probAd = expAd / sumExp;
        double probNormal = expNormal / sumExp;

        return new ProbabilityResult(probAd, probNormal);
    }

    //wd 检查分类器是否已就绪
    public boolean isReady() {
        return probabilityTable.isLoaded();
    }

    //wd 获取概率表（用于在线学习）
    public BayesianProbabilityTable getProbabilityTable() {
        return probabilityTable;
    }

    //wd 获取特征提取器
    public BayesianFeatureExtractor getFeatureExtractor() {
        return featureExtractor;
    }

    //wd 对数概率结果类
    private static class LogProbabilityResult {
        final double logProbAd;
        final double logProbNormal;

        LogProbabilityResult(double logProbAd, double logProbNormal) {
            this.logProbAd = logProbAd;
            this.logProbNormal = logProbNormal;
        }
    }

    //wd 概率结果类
    private static class ProbabilityResult {
        final double adProbability;
        final double normalProbability;

        ProbabilityResult(double adProbability, double normalProbability) {
            this.adProbability = adProbability;
            this.normalProbability = normalProbability;
        }
    }

    //wd 分类结果类
    public static class ClassificationResult {
        public final String predictedClass;      //wd 预测类别
        public final double adProbability;       //wd 是广告的概率
        public final double confidence;          //wd 置信度
        public final List<MatchedFeature> matchedFeatures;  //wd 匹配的特征
        public final String reason;              //wd 分类原因

        public ClassificationResult(String predictedClass, double adProbability,
                                    double confidence, List<MatchedFeature> matchedFeatures, String reason) {
            this.predictedClass = predictedClass;
            this.adProbability = adProbability;
            this.confidence = confidence;
            this.matchedFeatures = matchedFeatures != null ? matchedFeatures : Collections.emptyList();
            this.reason = reason;
        }

        //wd 是否为广告
        public boolean isAd() {
            return BayesianProbabilityTable.CLASS_AD.equals(predictedClass);
        }

        @NonNull
        @Override
        public String toString() {
            return "ClassificationResult{class='" + predictedClass + "', adProb=" + adProbability +
                    ", confidence=" + confidence + ", features=" + matchedFeatures.size() + "}";
        }
    }

    //wd 匹配的特征类
    public static class MatchedFeature {
        public final String term;           //wd 特征词
        public final double weight;         //wd TF-IDF权重
        public final int frequency;         //wd 频次
        public final double adProb;         //wd P(特征|广告)
        public final double normalProb;     //wd P(特征|正常)

        public MatchedFeature(String term, double weight, int frequency, double adProb, double normalProb) {
            this.term = term;
            this.weight = weight;
            this.frequency = frequency;
            this.adProb = adProb;
            this.normalProb = normalProb;
        }

        @NonNull
        @Override
        public String toString() {
            return "MatchedFeature{term='" + term + "', weight=" + weight + ", freq=" + frequency + "}";
        }
    }
}