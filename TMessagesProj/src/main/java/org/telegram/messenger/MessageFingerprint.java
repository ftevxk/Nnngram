/*
 * Copyright (C) 2024 Nnngram
 * 消息指纹生成器
 * 基于SimHash算法生成消息指纹，用于相似广告匹配
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

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//wd 消息指纹生成器
//wd 基于SimHash算法生成消息指纹，用于相似广告匹配
public class MessageFingerprint {

    private static volatile MessageFingerprint instance;

    //wd SimHash位数
    private static final int HASH_BITS = 64;

    //wd 特征提取正则
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]|[a-zA-Z0-9]{2,}");

    //wd 停用词
    private static final List<String> STOP_WORDS = Arrays.asList(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
        "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
        "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "啊",
        "哦", "呢", "吧", "吗", "嘛", "哈", "嘿", "哎", "嗯", "哼"
    );

    //wd 私有构造函数
    private MessageFingerprint() {
    }

    //wd 获取单例实例
    public static MessageFingerprint getInstance() {
        if (instance == null) {
            synchronized (MessageFingerprint.class) {
                if (instance == null) {
                    instance = new MessageFingerprint();
                }
            }
        }
        return instance;
    }

    //wd 指纹结果类
    public static class FingerprintResult {
        public final long fingerprint;      //wd 64位指纹
        public final String fingerprintHex; //wd 16进制表示
        public final List<String> features; //wd 提取的特征词
        public final Map<String, Double> featureWeights; //wd 特征权重

        public FingerprintResult(long fingerprint, String fingerprintHex,
                                List<String> features, Map<String, Double> featureWeights) {
            this.fingerprint = fingerprint;
            this.fingerprintHex = fingerprintHex;
            this.features = features;
            this.featureWeights = featureWeights;
        }
    }

    //wd 生成消息指纹
    @Nullable
    public FingerprintResult generateFingerprint(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        //wd 1. 预处理文本
        String normalizedText = normalizeText(text);

        //wd 2. 提取特征词
        List<String> features = extractFeatures(normalizedText);
        if (features.isEmpty()) {
            return null;
        }

        //wd 3. 计算TF-IDF权重
        Map<String, Double> weights = calculateWeights(features);

        //wd 4. 生成SimHash
        long fingerprint = computeSimHash(features, weights);

        //wd 5. 生成16进制表示
        String fingerprintHex = String.format("%016x", fingerprint);

        FileLog.d("wd MessageFingerprint 生成指纹: " + fingerprintHex + " 特征数=" + features.size());

        return new FingerprintResult(fingerprint, fingerprintHex, features, weights);
    }

    //wd 标准化文本
    @NonNull
    private String normalizeText(String text) {
        //wd 转小写
        String normalized = text.toLowerCase();

        //wd 统一空白字符
        normalized = normalized.replaceAll("\\s+", " ");

        //wd 移除特殊字符但保留中文和英文
        normalized = normalized.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", " ");

        //wd 处理常见变体
        normalized = normalized.replace("薇", "微")
                .replace("威信", "微信")
                .replace("v.x", "微信")
                .replace("vx", "微信")
                .replace("wx", "微信")
                .replace("扣扣", "qq")
                .replace("企鹅", "qq");

        return normalized.trim();
    }

    //wd 提取特征词
    @NonNull
    private List<String> extractFeatures(String text) {
        List<String> features = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);

        while (matcher.find()) {
            String token = matcher.group();
            //wd 过滤停用词和单字（除非是中文）
            if (token.length() >= 2 || isChinese(token.charAt(0))) {
                if (!STOP_WORDS.contains(token)) {
                    features.add(token);
                }
            }
        }

        return features;
    }

    //wd 判断是否为中文
    private boolean isChinese(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    //wd 计算TF-IDF权重
    @NonNull
    private Map<String, Double> calculateWeights(List<String> features) {
        Map<String, Integer> termFreq = new HashMap<>();
        for (String feature : features) {
            termFreq.put(feature, termFreq.getOrDefault(feature, 0) + 1);
        }

        Map<String, Double> weights = new HashMap<>();
        int totalTerms = features.size();

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int freq = entry.getValue();

            //wd TF = 词频 / 总词数
            double tf = (double) freq / totalTerms;

            //wd 简化IDF计算（假设每个词出现在2个文档中）
            double idf = Math.log((2.0 + 1.0) / (1.0 + 1.0)) + 1.0;

            //wd TF-IDF
            weights.put(term, tf * idf);
        }

        return weights;
    }

    //wd 计算SimHash
    private long computeSimHash(List<String> features, Map<String, Double> weights) {
        int[] vector = new int[HASH_BITS];

        for (String feature : features) {
            //wd 计算特征词的哈希值
            long hash = hashFeature(feature);
            double weight = weights.getOrDefault(feature, 1.0);

            //wd 更新向量
            for (int i = 0; i < HASH_BITS; i++) {
                long bit = (hash >> i) & 1;
                if (bit == 1) {
                    vector[i] += weight;
                } else {
                    vector[i] -= weight;
                }
            }
        }

        //wd 生成指纹
        long fingerprint = 0;
        for (int i = 0; i < HASH_BITS; i++) {
            if (vector[i] > 0) {
                fingerprint |= (1L << i);
            }
        }

        return fingerprint;
    }

    //wd 计算特征词的哈希值
    private long hashFeature(String feature) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(feature.getBytes(StandardCharsets.UTF_8));

            //wd 取前8字节作为64位哈希
            long result = 0;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            //wd 降级方案：使用字符串哈希
            return feature.hashCode();
        }
    }

    //wd 计算两个指纹的汉明距离
    public int hammingDistance(long fp1, long fp2) {
        long xor = fp1 ^ fp2;
        int distance = 0;
        while (xor != 0) {
            distance++;
            xor &= (xor - 1);
        }
        return distance;
    }

    //wd 计算两个指纹的相似度（0-1）
    public double calculateSimilarity(long fp1, long fp2) {
        int distance = hammingDistance(fp1, fp2);
        return 1.0 - ((double) distance / HASH_BITS);
    }

    //wd 判断两个指纹是否相似（汉明距离小于阈值）
    public boolean isSimilar(long fp1, long fp2, int threshold) {
        return hammingDistance(fp1, fp2) <= threshold;
    }

    //wd 默认相似度阈值
    public static final int DEFAULT_SIMILARITY_THRESHOLD = 3;

    //wd 判断两个指纹是否相似（使用默认阈值）
    public boolean isSimilar(long fp1, long fp2) {
        return isSimilar(fp1, fp2, DEFAULT_SIMILARITY_THRESHOLD);
    }

    //wd 批量查找相似指纹
    @NonNull
    public List<String> findSimilarFingerprints(long targetFp, Map<String, Long> fingerprintMap, int threshold) {
        List<String> similar = new ArrayList<>();

        for (Map.Entry<String, Long> entry : fingerprintMap.entrySet()) {
            if (isSimilar(targetFp, entry.getValue(), threshold)) {
                similar.add(entry.getKey());
            }
        }

        return similar;
    }

    //wd 生成消息的唯一标识（用于存储）
    @NonNull
    public String generateMessageId(MessageObject messageObject) {
        return messageObject.getDialogId() + "_" + messageObject.getId();
    }
}