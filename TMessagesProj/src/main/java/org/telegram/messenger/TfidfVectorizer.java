/*
 * Copyright (C) 2024 Nnngram
 * TF-IDF 文本向量化器 - 用于 TensorFlow Lite 模型输入
 * 实现与 Python sklearn TfidfVectorizer 相同的功能
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TF-IDF 文本向量化器
 * 将文本转换为与训练时相同的 TF-IDF 特征向量
 */
public class TfidfVectorizer {
    
    private final Map<String, Integer> vocabulary;
    private final float[] idfValues;
    private final int maxFeatures;
    private final int minNgram;
    private final int maxNgram;
    
    // 分词正则表达式
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]|\\w+");
    
    public TfidfVectorizer(@NonNull Context context, @NonNull String vocabPath, 
                          @NonNull String idfPath) throws IOException {
        this.vocabulary = loadVocabulary(context, vocabPath);
        this.idfValues = loadIdfValues(context, idfPath);
        this.maxFeatures = vocabulary.size();
        this.minNgram = 1;
        this.maxNgram = 3;
        
        FileLog.d("wd TfidfVectorizer: 已加载词汇表大小=" + maxFeatures);
    }
    
    /**
     * 加载词汇表
     */
    private Map<String, Integer> loadVocabulary(Context context, String path) throws IOException {
        Map<String, Integer> vocab = new HashMap<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path), "UTF-8")
            );
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    vocab.put(line, index++);
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return vocab;
    }
    
    /**
     * 加载 IDF 值
     */
    private float[] loadIdfValues(Context context, String path) throws IOException {
        List<Float> idfList = new ArrayList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(path), "UTF-8")
            );
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        idfList.add(Float.parseFloat(line));
                    } catch (NumberFormatException e) {
                        FileLog.e("wd TfidfVectorizer: 解析IDF值失败: " + line);
                    }
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        
        float[] result = new float[idfList.size()];
        for (int i = 0; i < idfList.size(); i++) {
            result[i] = idfList.get(i);
        }
        return result;
    }
    
    /**
     * 将文本转换为 TF-IDF 特征向量
     * @param text 输入文本
     * @return float[1][maxFeatures] 特征向量
     */
    public float[][] transform(@NonNull String text) {
        if (TextUtils.isEmpty(text)) {
            return new float[1][maxFeatures];
        }

        // 分词并提取 n-gram
        List<String> tokens = tokenize(text);
        List<String> ngrams = extractNgrams(tokens, minNgram, maxNgram);

        // 计算词频 (TF)
        Map<String, Integer> termFreq = new HashMap<>();
        for (String ngram : ngrams) {
            termFreq.put(ngram, termFreq.getOrDefault(ngram, 0) + 1);
        }

        // 计算 TF-IDF
        float[][] result = new float[1][maxFeatures];
        float norm = 0;

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            Integer idx = vocabulary.get(term);
            if (idx != null && idx < maxFeatures) {
                // TF = 词频 / 总词数
                float tf = (float) entry.getValue() / ngrams.size();
                // IDF 从预计算的数组中获取
                float idf = idx < idfValues.length ? idfValues[idx] : 1.0f;
                // TF-IDF
                float tfidf = tf * idf;
                result[0][idx] = tfidf;
                norm += tfidf * tfidf;
            }
        }

        // L2 归一化
        if (norm > 0) {
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < maxFeatures; i++) {
                result[0][i] /= norm;
            }
        }

        return result;
    }

    //wd 计算文本的特征覆盖率 - 用于白名单机制判断
    //wd 返回文本中有多少比例的 n-gram 在广告词汇表中
    //wd 覆盖率低于阈值时直接判定为正常消息
    /**
     * 计算文本的特征覆盖率
     * @param text 输入文本
     * @return 覆盖率 (0.0 - 1.0)，表示文本中有多少比例的 n-gram 在词汇表中
     */
    public float getFeatureCoverage(@NonNull String text) {
        if (TextUtils.isEmpty(text)) {
            return 0f;
        }

        // 分词并提取 n-gram
        List<String> tokens = tokenize(text);
        List<String> ngrams = extractNgrams(tokens, minNgram, maxNgram);

        if (ngrams.isEmpty()) {
            return 0f;
        }

        // 统计在词汇表中的 n-gram 数量
        int matchedCount = 0;
        for (String ngram : ngrams) {
            if (vocabulary.containsKey(ngram)) {
                matchedCount++;
            }
        }

        return (float) matchedCount / ngrams.size();
    }

    //wd 获取文本中匹配的特征数量 - 辅助判断是否为广告内容
    /**
     * 获取文本中匹配的特征数量
     * @param text 输入文本
     * @return 匹配的特征数量
     */
    public int getMatchedFeatureCount(@NonNull String text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }

        // 分词并提取 n-gram
        List<String> tokens = tokenize(text);
        List<String> ngrams = extractNgrams(tokens, minNgram, maxNgram);

        // 统计在词汇表中的 n-gram 数量
        int matchedCount = 0;
        for (String ngram : ngrams) {
            if (vocabulary.containsKey(ngram)) {
                matchedCount++;
            }
        }

        return matchedCount;
    }
    
    /**
     * 分词 - 支持中文单字和英文单词
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (isChinese(c)) {
                // 中文单字作为一个 token
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString().toLowerCase(Locale.ROOT));
                    currentWord.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                currentWord.append(c);
            } else {
                // 非字母数字字符作为分隔符
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString().toLowerCase(Locale.ROOT));
                    currentWord.setLength(0);
                }
            }
        }
        
        // 处理最后一个词
        if (currentWord.length() > 0) {
            tokens.add(currentWord.toString().toLowerCase(Locale.ROOT));
        }
        
        return tokens;
    }
    
    /**
     * 提取 n-gram
     */
    private List<String> extractNgrams(List<String> tokens, int minN, int maxN) {
        List<String> ngrams = new ArrayList<>();
        
        for (int n = minN; n <= maxN && n <= tokens.size(); n++) {
            for (int i = 0; i <= tokens.size() - n; i++) {
                StringBuilder ngram = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) ngram.append(" ");
                    ngram.append(tokens.get(i + j));
                }
                ngrams.add(ngram.toString());
            }
        }
        
        return ngrams;
    }
    
    /**
     * 判断是否为中文
     */
    private boolean isChinese(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }
    
    /**
     * 获取特征维度
     */
    public int getFeatureSize() {
        return maxFeatures;
    }
    
    /**
     * 获取词汇表大小
     */
    public int getVocabSize() {
        return vocabulary.size();
    }
}
