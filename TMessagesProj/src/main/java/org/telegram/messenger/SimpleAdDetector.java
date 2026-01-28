/*
 * 简单关键词广告检测器（增强版）
 * 作为 TFLite 模型的备用方案
 * 基于上下文感知的广告检测，降低误杀率
 */

package org.telegram.messenger;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleAdDetector {
    private static volatile SimpleAdDetector instance;
    private final Set<String> adKeywords = new HashSet<>();
    private final Set<Pattern> adPatterns = new HashSet<>();
    private final Map<String, KeywordContext> keywordContexts = new HashMap<>();
    private float threshold = 0.75f;

    // 关键词上下文定义 - 用于区分正常用法和广告用法
    private static class KeywordContext {
        final Set<String> normalIndicators;   // 正常用法指示词
        final Set<String> adIndicators;       // 广告用法指示词
        final float baseWeight;               // 基础权重

        KeywordContext(Set<String> normalIndicators, Set<String> adIndicators, float baseWeight) {
            this.normalIndicators = normalIndicators;
            this.adIndicators = adIndicators;
            this.baseWeight = baseWeight;
        }
    }

    // 核心广告关键词（强信号）
    private static final String[] CORE_AD_KEYWORDS = {
        "上分", "下分", "出分", "跑分", "对接", "费率", "结算",
        "博彩", "菠菜", "娱乐城", "赌场", "百家乐", "老虎机",
        "体育投注", "真人", "电子", "棋牌", "捕鱼",
        "USDT", "OTC", "数字货币", "虚拟货币"
    };

    // 推广类关键词（中等信号）
    private static final String[] PROMOTION_KEYWORDS = {
        "充值", "首充", "返利", "返现", "优惠", "活动",
        "代理", "加盟", "合作", "共赢", "分红", "佣金",
        "赚钱", "兼职", "日结", "周结", "月入", "高薪"
    };

    // 弱信号关键词（需要组合判断）
    private static final String[] WEAK_SIGNAL_KEYWORDS = {
        "微信", "支付宝", "银行卡", "转账", "汇款", "提现", "流水",
        "注册", "首单", "新用户", "邀请", "免费", "赠送", "福利",
        "VIP", "贵宾", "TG", "Telegram", "@", "机器人", "bot"
    };

    // 正常语境指示词（降低误判）
    private static final String[] NORMAL_CONTEXT_WORDS = {
        "我在", "我是", "我们", "讨论", "请问", "咨询", "了解",
        "怎么", "如何", "什么", "有人知道", "求助", "疑问",
        "谢谢", "感谢", "请问", "麻烦", "请教"
    };

    // 广告语境指示词（提高识别）
    private static final String[] AD_CONTEXT_WORDS = {
        "加我", "联系我", "私聊", "详聊", "咨询", "了解",
        "马上", "立即", "速来", "快来", "抓紧", "机不可失",
        "错过", "最后", "仅剩", "名额有限", "先到先得"
    };

    // 交易金额模式（强广告信号）
        private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:上分|下分|出分|充值|提现)?\\s*[：:]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(元|块|刀|万|w|USDT|U)", Pattern.CASE_INSENSITIVE);

    // 联系方式+业务关键词组合模式
    private static final Pattern[] COMBINATION_PATTERNS = {
        // 联系方式 + 博彩/交易
        Pattern.compile("(?:微信|VX|wx|QQ|TG|@).*?(?:上分|下分|博彩|充值|代理)", Pattern.CASE_INSENSITIVE),
        // 优惠 + 联系方式
        Pattern.compile("(?:优惠|返利|活动|免费).*?(?:微信|QQ|TG|@|加)", Pattern.CASE_INSENSITIVE),
        // 赚钱/兼职 + 联系方式
        Pattern.compile("(?:赚钱|兼职|日结|月入).*?(?:微信|QQ|TG|@|加)", Pattern.CASE_INSENSITIVE)
    };

    private static final String[] PATTERN_STRINGS = {
        "(?:微信|VX|wx|WeChat)[：:]\\s*[a-zA-Z0-9_\\-]+",
        "(?:QQ|qq)[：:]\\s*[0-9]+",
        "(?:TG|Telegram|电报)[@：:]\\s*[a-zA-Z0-9_]+",
        "@[a-zA-Z0-9_]{5,32}",
        "https?://[\\w\\-./?%&=]+",
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    };

    private SimpleAdDetector() {
        // 初始化关键词
        for (String keyword : CORE_AD_KEYWORDS) {
            adKeywords.add(keyword);
            keywordContexts.put(keyword, new KeywordContext(
                new HashSet<>(),
                new HashSet<>(Arrays.asList("代理", "充值", "优惠", "活动")),
                1.0f
            ));
        }

        for (String keyword : PROMOTION_KEYWORDS) {
            adKeywords.add(keyword);
            keywordContexts.put(keyword, new KeywordContext(
                new HashSet<>(Arrays.asList("讨论", "了解", "咨询")),
                new HashSet<>(Arrays.asList("赚钱", "联系", "加我")),
                0.7f
            ));
        }

        for (String keyword : WEAK_SIGNAL_KEYWORDS) {
            adKeywords.add(keyword);
            keywordContexts.put(keyword, new KeywordContext(
                new HashSet<>(Arrays.asList("我在", "使用", "支付")),
                new HashSet<>(Arrays.asList("加我", "联系", "代理")),
                0.3f
            ));
        }

        // 初始化正则模式
        for (String pattern : PATTERN_STRINGS) {
            adPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
    }

    public static SimpleAdDetector getInstance() {
        if (instance == null) {
            synchronized (SimpleAdDetector.class) {
                if (instance == null) {
                    instance = new SimpleAdDetector();
                }
            }
        }
        return instance;
    }

    public void setThreshold(float threshold) {
        this.threshold = Math.max(0f, Math.min(1f, threshold));
    }

    public float getThreshold() {
        return threshold;
    }

    public boolean isAd(@NonNull String text) {
        return getAdScore(text) >= threshold;
    }

    public float getAdScore(@NonNull String text) {
        if (text.isEmpty()) {
            return 0f;
        }

        String lowerText = text.toLowerCase();
        float totalScore = 0f;
        int contextBonus = 0;
        int contextPenalty = 0;

        // 1. 检查正常语境（降低误判）
        for (String normalWord : NORMAL_CONTEXT_WORDS) {
            if (lowerText.contains(normalWord)) {
                contextPenalty++;
            }
        }

        // 2. 检查广告语境（提高识别）
        for (String adWord : AD_CONTEXT_WORDS) {
            if (lowerText.contains(adWord)) {
                contextBonus++;
            }
        }

        // 3. 核心关键词检测（强信号）
        int coreKeywordMatches = 0;
        for (String keyword : CORE_AD_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                coreKeywordMatches++;
                KeywordContext ctx = keywordContexts.get(keyword);
                if (ctx != null) {
                    totalScore += ctx.baseWeight;
                }
            }
        }

        // 4. 推广关键词检测（中等信号）
        int promoKeywordMatches = 0;
        for (String keyword : PROMOTION_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                promoKeywordMatches++;
                KeywordContext ctx = keywordContexts.get(keyword);
                if (ctx != null) {
                    // 检查上下文
                    boolean hasNormalContext = false;
                    for (String indicator : ctx.normalIndicators) {
                        if (lowerText.contains(indicator)) {
                            hasNormalContext = true;
                            break;
                        }
                    }
                    boolean hasAdContext = false;
                    for (String indicator : ctx.adIndicators) {
                        if (lowerText.contains(indicator)) {
                            hasAdContext = true;
                            break;
                        }
                    }

                    if (hasAdContext) {
                        totalScore += ctx.baseWeight * 1.5f;
                    } else if (hasNormalContext) {
                        totalScore += ctx.baseWeight * 0.3f;
                    } else {
                        totalScore += ctx.baseWeight;
                    }
                }
            }
        }

        // 5. 弱信号关键词检测（需要组合）
        int weakKeywordMatches = 0;
        for (String keyword : WEAK_SIGNAL_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                weakKeywordMatches++;
            }
        }
        // 弱信号需要多个同时出现才计分
        if (weakKeywordMatches >= 3) {
            totalScore += 0.2f * weakKeywordMatches;
        }

        // 6. 正则模式匹配
        int patternMatches = 0;
        for (Pattern pattern : adPatterns) {
            if (pattern.matcher(text).find()) {
                patternMatches++;
            }
        }
        totalScore += Math.min(0.3f, patternMatches * 0.1f);

        // 7. 金额模式检测（强广告信号）
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(text);
        int amountMatches = 0;
        while (amountMatcher.find()) {
            amountMatches++;
        }
        if (amountMatches > 0) {
            totalScore += 0.4f * amountMatches;
        }

        // 8. 组合模式检测（最强信号）
        int combinationMatches = 0;
        for (Pattern pattern : COMBINATION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                combinationMatches++;
            }
        }
        totalScore += 0.5f * combinationMatches;

        // 9. 应用上下文调整
        // 正常语境降低分数
        if (contextPenalty >= 2) {
            totalScore *= 0.6f;
        } else if (contextPenalty >= 1) {
            totalScore *= 0.8f;
        }

        // 广告语境增加分数
        if (contextBonus >= 2) {
            totalScore += 0.3f;
        } else if (contextBonus >= 1) {
            totalScore += 0.15f;
        }

        // 10. 综合判断逻辑
        // 如果同时有核心关键词+联系方式+金额，直接判定为高概率广告
        if (coreKeywordMatches >= 1 && patternMatches >= 1 && amountMatches >= 1) {
            totalScore = Math.max(totalScore, 0.9f);
        }

        // 如果只有弱信号关键词，需要更多证据
        if (coreKeywordMatches == 0 && promoKeywordMatches == 0 && weakKeywordMatches >= 2) {
            // 只有弱信号，降低分数
            totalScore *= 0.5f;
        }

        // 限制最大分数
        return Math.min(1.0f, totalScore);
    }

    /**
     * 获取详细的检测分析结果（用于调试）
     */
    public DetectionAnalysis analyze(@NonNull String text) {
        if (text.isEmpty()) {
            return new DetectionAnalysis(0f, 0, 0, 0, 0, 0, 0, "empty text");
        }

        String lowerText = text.toLowerCase();

        int normalContextCount = 0;
        int adContextCount = 0;
        int coreKeywordMatches = 0;
        int promoKeywordMatches = 0;
        int weakKeywordMatches = 0;
        int patternMatches = 0;

        for (String word : NORMAL_CONTEXT_WORDS) {
            if (lowerText.contains(word)) normalContextCount++;
        }

        for (String word : AD_CONTEXT_WORDS) {
            if (lowerText.contains(word)) adContextCount++;
        }

        for (String keyword : CORE_AD_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) coreKeywordMatches++;
        }

        for (String keyword : PROMOTION_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) promoKeywordMatches++;
        }

        for (String keyword : WEAK_SIGNAL_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) weakKeywordMatches++;
        }

        for (Pattern pattern : adPatterns) {
            if (pattern.matcher(text).find()) patternMatches++;
        }

        float score = getAdScore(text);
        StringBuilder reason = new StringBuilder();
        reason.append("核心关键词:").append(coreKeywordMatches)
              .append(", 推广词:").append(promoKeywordMatches)
              .append(", 弱信号:").append(weakKeywordMatches)
              .append(", 模式:").append(patternMatches)
              .append(", 正常语境:").append(normalContextCount)
              .append(", 广告语境:").append(adContextCount);

        return new DetectionAnalysis(score, coreKeywordMatches, promoKeywordMatches,
            weakKeywordMatches, patternMatches, normalContextCount, adContextCount, reason.toString());
    }

    public static class DetectionAnalysis {
        public final float score;
        public final int coreKeywords;
        public final int promoKeywords;
        public final int weakSignals;
        public final int patterns;
        public final int normalContext;
        public final int adContext;
        public final String reason;

        public DetectionAnalysis(float score, int coreKeywords, int promoKeywords,
                                int weakSignals, int patterns, int normalContext,
                                int adContext, String reason) {
            this.score = score;
            this.coreKeywords = coreKeywords;
            this.promoKeywords = promoKeywords;
            this.weakSignals = weakSignals;
            this.patterns = patterns;
            this.normalContext = normalContext;
            this.adContext = adContext;
            this.reason = reason;
        }
    }

    public void addKeyword(String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            adKeywords.add(keyword.toLowerCase());
        }
    }

    public void addPattern(String pattern) {
        if (pattern != null && !pattern.isEmpty()) {
            try {
                adPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                FileLog.e("wd SimpleAdDetector: invalid pattern: " + pattern, e);
            }
        }
    }

    public void clearKeywords() {
        adKeywords.clear();
    }

    public void clearPatterns() {
        adPatterns.clear();
    }
}
