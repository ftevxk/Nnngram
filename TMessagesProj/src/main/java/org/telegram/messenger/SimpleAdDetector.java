/*
 * 简单关键词广告检测器
 * 作为 TFLite 模型的备用方案
 * 基于常见广告关键词进行检测
 */

package org.telegram.messenger;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SimpleAdDetector {
    private static volatile SimpleAdDetector instance;
    private final Set<String> adKeywords = new HashSet<>();
    private final Set<Pattern> adPatterns = new HashSet<>();
    private float threshold = 0.85f;

    private static final String[] KEYWORDS = {
        "上分", "下分", "出分", "跑分", "对接", "费率", "结算",
        "微信", "支付宝", "USDT", "OTC", "充值", "返利",
        "兼职", "赚钱", "日结", "周结", "工资",
        "博彩", "菠菜", "娱乐城", "赌场", "百家乐",
        "注册", "首充", "优惠", "送", "免费",
        "代理", "加盟", "分红", "佣金",
        "VIP", "贵宾", "老虎机", "体育投注",
        "真人", "电子", "棋牌", "捕鱼",
        "大额", "小额", "流水", "提现",
        "安全", "稳定", "信誉", "实力",
        "TG", "Telegram", "@", "机器人", "bot"
    };

    private static final String[] PATTERN_STRINGS = {
        "微信[：:]?[a-zA-Z0-9_]+",
        "QQ[：:]?[0-9]+",
        "TG[@：:][a-zA-Z0-9_]+",
        "[0-9]{6,12}",
        "https?://[\\w\\-./]+",
        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    };

    private SimpleAdDetector() {
        for (String keyword : KEYWORDS) {
            adKeywords.add(keyword);
        }
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
        if (text.isEmpty()) {
            return false;
        }

        int keywordMatches = 0;
        String lowerText = text.toLowerCase();

        for (String keyword : adKeywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                keywordMatches++;
            }
        }

        for (Pattern pattern : adPatterns) {
            if (pattern.matcher(text).find()) {
                keywordMatches++;
            }
        }

        float score = Math.min(1.0f, keywordMatches / 5.0f);
        return score >= threshold;
    }

    public float getAdScore(@NonNull String text) {
        if (text.isEmpty()) {
            return 0f;
        }

        int keywordMatches = 0;
        String lowerText = text.toLowerCase();

        for (String keyword : adKeywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                keywordMatches++;
            }
        }

        for (Pattern pattern : adPatterns) {
            if (pattern.matcher(text).find()) {
                keywordMatches++;
            }
        }

        return Math.min(1.0f, keywordMatches / 5.0f);
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
