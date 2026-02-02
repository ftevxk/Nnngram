/*
 * Copyright (C) 2024 Nnngram
 * 消息主题分析器 - 简化版
 * 仅保留必要的辅助分析功能，主要逻辑已迁移到AiAdContentAnalyzer
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//wd 消息主题分析器 - 简化版
//wd 主要逻辑已迁移到AiAdContentAnalyzer，此类保留辅助功能
public class MessageTopicAnalyzer {
    private static volatile MessageTopicAnalyzer instance;

    //wd AI内容分析器
    private AiAdContentAnalyzer contentAnalyzer;

    //wd 上下文
    private Context context;

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
        public final float adProbability;       // 广告概率 0-1
        public final Set<String> entities;      // 提取的实体
        public final Map<String, Float> features; // 特征分数
        public final String summary;            // 内容摘要

        public TopicAnalysis(TopicType topicType, IntentType intentType,
                            float adProbability, Set<String> entities,
                            Map<String, Float> features, String summary) {
            this.topicType = topicType;
            this.intentType = intentType;
            this.adProbability = adProbability;
            this.entities = entities;
            this.features = features;
            this.summary = summary;
        }

        @Override
        public String toString() {
            return String.format("TopicAnalysis{topic=%s, intent=%s, adProb=%.2f}",
                topicType, intentType, adProbability);
        }
    }

    private MessageTopicAnalyzer() {
        contentAnalyzer = AiAdContentAnalyzer.getInstance();
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
        contentAnalyzer.init(context);
    }

    @NonNull
    public TopicAnalysis analyze(@NonNull String text) {
        if (TextUtils.isEmpty(text)) {
            return createEmptyAnalysis();
        }

        //wd 使用新的内容分析器进行分析
        AiAdContentAnalyzer.AnalysisResult result = contentAnalyzer.analyze(text);

        //wd 基于分析结果推断主题类型
        TopicType topicType = inferTopicType(text, result);
        IntentType intentType = inferIntentType(text);

        //wd 提取实体
        Set<String> entities = extractEntities(text);

        //wd 构建特征图
        Map<String, Float> features = new HashMap<>();
        features.put("ad_score", result.score);
        features.put("keyword_count", (float) result.matchedKeywords.size());

        //wd 生成摘要
        String summary = generateSummary(text, topicType, result);

        return new TopicAnalysis(
            topicType, intentType, result.score,
            entities, features, summary);
    }

    //wd 基于分析结果推断主题类型
    private TopicType inferTopicType(String text, AiAdContentAnalyzer.AnalysisResult result) {
        String lowerText = text.toLowerCase();

        //wd 如果判定为广告，进一步细分类型
        if (result.isAd) {
            if (lowerText.contains("代理") || lowerText.contains("招募") || lowerText.contains("加盟")) {
                return TopicType.RECRUITMENT;
            }
            if (lowerText.contains("上分") || lowerText.contains("下分") || lowerText.contains("充值") ||
                lowerText.contains("跑分") || lowerText.contains("收款")) {
                return TopicType.TRANSACTION;
            }
            return TopicType.PROMOTION;
        }

        //wd 正常消息类型判断
        if (lowerText.contains("请问") || lowerText.contains("咨询") || lowerText.contains("怎么") ||
            lowerText.contains("如何") || lowerText.contains("多少")) {
            return TopicType.CONSULTATION;
        }

        if (lowerText.contains("你好") || lowerText.contains("在吗") || lowerText.contains("早上好") ||
            lowerText.contains("晚上好")) {
            return TopicType.GREETING;
        }

        if (lowerText.contains("通知") || lowerText.contains("公告") || lowerText.contains("提醒")) {
            return TopicType.NOTIFICATION;
        }

        return TopicType.CHAT;
    }

    //wd 推断意图类型
    private IntentType inferIntentType(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("马上") || lowerText.contains("立即") || lowerText.contains("速来") ||
            lowerText.contains("快来") || lowerText.contains("抓紧") || lowerText.contains("机不可失")) {
            return IntentType.URGE;
        }

        if (lowerText.contains("提供") || lowerText.contains("可以") || lowerText.contains("能") ||
            lowerText.contains("支持")) {
            return IntentType.OFFER;
        }

        if (lowerText.contains("加入") || lowerText.contains("联系") || lowerText.contains("加") ||
            lowerText.contains("私聊")) {
            return IntentType.INVITE;
        }

        if (lowerText.contains("请问") || lowerText.contains("问一下") || lowerText.contains("能否") ||
            lowerText.contains("可以吗")) {
            return IntentType.REQUEST;
        }

        return IntentType.INFORM;
    }

    //wd 提取实体
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

    //wd 生成摘要
    private String generateSummary(String text, TopicType topicType, AiAdContentAnalyzer.AnalysisResult result) {
        StringBuilder summary = new StringBuilder();
        summary.append("主题:").append(topicType);
        summary.append(", 广告得分:").append(String.format("%.2f", result.score));
        summary.append(", 匹配关键词:").append(result.matchedKeywords.size());

        if (!result.matchedKeywords.isEmpty()) {
            summary.append(", 关键词:");
            int count = 0;
            for (AiAdContentAnalyzer.MatchedKeyword kw : result.matchedKeywords) {
                if (count++ > 0) summary.append("|");
                summary.append(kw.keyword);
                if (count >= 3) break; //wd 最多显示3个关键词
            }
        }

        return summary.toString();
    }

    private TopicAnalysis createEmptyAnalysis() {
        return new TopicAnalysis(
            TopicType.UNKNOWN, IntentType.UNKNOWN,
            0f, new HashSet<>(), new HashMap<>(), ""
        );
    }

    //wd 重新加载关键词（用于兼容旧接口）
    public void reloadKeywords() {
        //wd 新架构中关键词由BayesianProbabilityTable管理
        BayesianProbabilityTable probTable = BayesianProbabilityTable.getInstance();
        if (probTable != null) {
            probTable.reloadProbabilities();
            FileLog.d("wd MessageTopicAnalyzer 已刷新概率表");
        }
    }

    //wd 释放资源
    public void release() {
        //wd 资源由各个单例自己管理
    }
}
