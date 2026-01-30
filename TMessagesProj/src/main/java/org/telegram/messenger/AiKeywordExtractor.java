/*
 * Copyright (C) 2024 Nnngram
 * AI关键词提取器
 * 使用AI模型从文本中提取广告关键词及频次
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

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//wd AI关键词提取器
//wd 使用本地TFLite模型或规则引擎从文本中提取广告关键词
public class AiKeywordExtractor {

    private static volatile AiKeywordExtractor instance;

    //wd TFLite模型
    private Interpreter interpreter;
    private MappedByteBuffer modelBuffer;
    private final AtomicBoolean modelLoaded = new AtomicBoolean(false);

    //wd 词汇表和IDF
    private Map<String, Integer> vocabulary;
    private float[] idfValues;

    //wd 上下文
    private Context context;

    //wd 模型路径
    private static final String MODEL_PATH = "ai_ad_filter/keyword_extractor_model.tflite";
    private static final String VOCAB_PATH = "ai_ad_filter/keyword_extractor_vocab.txt";
    private static final String IDF_PATH = "ai_ad_filter/keyword_extractor_idf.txt";

    //wd 广告关键词规则库（用于规则引擎模式）
    private static final String[] AD_KEYWORD_PATTERNS = {
            "博彩", "菠菜", "赌博", "赌球", "casino", "bet365", "百家乐", "老虎机",
            "跑分", "上分", "下分", "usdt", "虚拟货币", "合约带单", "币圈", "空投",
            "刷单", "返利", "充值", "收款码", "代收", "代付", "码商", "码子",
            "支付宝", "微信", "qq", "一单一结", "结账", "零风险", "0风险",
            "首充", "代理招募", "月入过万", "稳赚不赔", "高额返佣", "日赚", "秒到账",
            "信誉担保", "内部消息", "限时优惠", "机不可失", "利润", "福利红包", "做单",
            "联系", "加好友", "私聊", "盘口", "代理", "加盟", "兼职", "赚钱",
            "推广", "营销", "广告", "招募", "合作", "诚邀", "诚邀合作",
            "网赌", "赌局", "下注", "赔率", "返水", "彩金", "存款", "提款",
            "资金", "账户", "会员", "注册", "登录", "网址", "链接", "点击",
            "真人", "荷官", "视讯", "直播", "在线", "24小时", "客服",
            "棋牌", "牛牛", "斗地主", "炸金花", "三公", "牌九", "龙虎斗",
            "彩票", "时时彩", "pk10", "北京赛车", "高频彩", "体彩", "福彩",
            "电子游艺", "pt", "mg", "bbin", "ag真人", "ag视讯",
            "薇信", "v.x", "vx", "丄分", "玳理", "冲值", "菠.菜"
    };

    //wd 权重映射表
    private static final Map<String, Float> KEYWORD_WEIGHTS = new ConcurrentHashMap<>();

    static {
        //wd 高权重关键词（强广告特征）
        KEYWORD_WEIGHTS.put("博彩", 0.95f);
        KEYWORD_WEIGHTS.put("菠菜", 0.95f);
        KEYWORD_WEIGHTS.put("赌博", 0.95f);
        KEYWORD_WEIGHTS.put("跑分", 0.95f);
        KEYWORD_WEIGHTS.put("上分", 0.95f);
        KEYWORD_WEIGHTS.put("下分", 0.95f);
        KEYWORD_WEIGHTS.put("网赌", 0.95f);
        KEYWORD_WEIGHTS.put("赌球", 0.95f);
        KEYWORD_WEIGHTS.put("casino", 0.95f);
        KEYWORD_WEIGHTS.put("百家乐", 0.95f);

        //wd 中高权重关键词
        KEYWORD_WEIGHTS.put("刷单", 0.85f);
        KEYWORD_WEIGHTS.put("返利", 0.85f);
        KEYWORD_WEIGHTS.put("充值", 0.85f);
        KEYWORD_WEIGHTS.put("收款码", 0.85f);
        KEYWORD_WEIGHTS.put("代收", 0.85f);
        KEYWORD_WEIGHTS.put("代付", 0.85f);
        KEYWORD_WEIGHTS.put("码商", 0.85f);
        KEYWORD_WEIGHTS.put("usdt", 0.85f);
        KEYWORD_WEIGHTS.put("虚拟货币", 0.85f);
        KEYWORD_WEIGHTS.put("币圈", 0.85f);

        //wd 中等权重关键词（降低以避免误判）
        KEYWORD_WEIGHTS.put("代理", 0.50f);
        KEYWORD_WEIGHTS.put("招募", 0.55f);
        KEYWORD_WEIGHTS.put("加盟", 0.55f);
        KEYWORD_WEIGHTS.put("兼职", 0.55f);
        KEYWORD_WEIGHTS.put("月入过万", 0.70f);
        KEYWORD_WEIGHTS.put("稳赚不赔", 0.70f);
        KEYWORD_WEIGHTS.put("高额返佣", 0.70f);
        KEYWORD_WEIGHTS.put("日赚", 0.65f);
        KEYWORD_WEIGHTS.put("秒到账", 0.60f);

        //wd 低权重关键词（常用词，权重更低）
        KEYWORD_WEIGHTS.put("联系", 0.30f);
        KEYWORD_WEIGHTS.put("加好友", 0.30f);
        KEYWORD_WEIGHTS.put("私聊", 0.30f);
        KEYWORD_WEIGHTS.put("微信", 0.30f);
        KEYWORD_WEIGHTS.put("qq", 0.30f);
        KEYWORD_WEIGHTS.put("点击", 0.35f);
    }

    //wd 私有构造函数
    private AiKeywordExtractor() {
    }

    //wd 获取单例实例
    public static AiKeywordExtractor getInstance() {
        if (instance == null) {
            synchronized (AiKeywordExtractor.class) {
                if (instance == null) {
                    instance = new AiKeywordExtractor();
                }
            }
        }
        return instance;
    }

    //wd 初始化提取器
    public void init(Context context) {
        this.context = context.getApplicationContext();
        loadModel();
    }

    //wd 加载TFLite模型
    private void loadModel() {
        try {
            FileLog.d("wd AiKeywordExtractor 开始加载模型");

            //wd 尝试加载模型文件
            modelBuffer = loadModelFile(MODEL_PATH);

            if (modelBuffer != null) {
                //wd 加载词汇表
                vocabulary = loadVocabulary(VOCAB_PATH);
                idfValues = loadIdfValues(IDF_PATH);

                if (vocabulary != null && idfValues != null) {
                    Interpreter.Options options = new Interpreter.Options();
                    options.setNumThreads(2);
                    interpreter = new Interpreter(modelBuffer, options);
                    modelLoaded.set(true);
                    FileLog.d("wd AiKeywordExtractor 模型加载成功");
                } else {
                    FileLog.w("wd AiKeywordExtractor 词汇表或IDF加载失败，将使用规则引擎");
                }
            } else {
                FileLog.w("wd AiKeywordExtractor 模型文件不存在，将使用规则引擎");
            }
        } catch (Exception e) {
            FileLog.e("wd AiKeywordExtractor 加载模型失败", e);
        }
    }

    //wd 加载模型文件
    private MappedByteBuffer loadModelFile(String path) throws IOException {
        try (android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(path);
             java.io.FileInputStream inputStream = afd.createInputStream();
             FileChannel channel = inputStream.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.getStartOffset(), afd.getLength());
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
    }

    //wd 加载词汇表
    private Map<String, Integer> loadVocabulary(String path) {
        Map<String, Integer> vocab = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(path))
            );
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    vocab.put(line, index++);
                }
            }
            reader.close();
            FileLog.d("wd AiKeywordExtractor 词汇表加载完成，共 " + vocab.size() + " 个词");
        } catch (IOException e) {
            FileLog.e("wd AiKeywordExtractor 加载词汇表失败", e);
            return null;
        }
        return vocab;
    }

    //wd 加载IDF值
    private float[] loadIdfValues(String path) {
        List<Float> idfList = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(path))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    idfList.add(Float.parseFloat(line));
                }
            }
            reader.close();

            float[] idfArray = new float[idfList.size()];
            for (int i = 0; i < idfList.size(); i++) {
                idfArray[i] = idfList.get(i);
            }
            FileLog.d("wd AiKeywordExtractor IDF值加载完成，共 " + idfArray.length + " 个");
            return idfArray;
        } catch (IOException | NumberFormatException e) {
            FileLog.e("wd AiKeywordExtractor 加载IDF值失败", e);
            return null;
        }
    }

    //wd 提取关键词
    //wd 优先使用AI模型，如果模型未加载则使用规则引擎
    @NonNull
    public List<ExtractedKeyword> extractKeywords(String text) {
        if (TextUtils.isEmpty(text)) {
            return Collections.emptyList();
        }

        //wd 标准化文本
        String normalizedText = normalizeText(text);

        //wd 如果模型已加载，使用AI模型提取
        if (modelLoaded.get() && interpreter != null) {
            try {
                return extractWithModel(normalizedText);
            } catch (Exception e) {
                FileLog.e("wd AiKeywordExtractor AI模型提取失败，降级到规则引擎", e);
            }
        }

        //wd 使用规则引擎提取
        return extractWithRules(normalizedText);
    }

    //wd 使用AI模型提取关键词
    //wd 使用TFLite模型进行多标签分类，提取广告关键词
    @NonNull
    private List<ExtractedKeyword> extractWithModel(String text) {
        if (interpreter == null || vocabulary == null || idfValues == null) {
            FileLog.w("wd AiKeywordExtractor 模型未就绪，降级到规则引擎");
            return extractWithRules(text);
        }

        try {
            //wd 1. 将文本转换为TF-IDF特征向量
            float[][] inputFeatures = textToTfidf(text);

            //wd 2. 准备输出数组 [1][keyword_count]
            int keywordCount = getKeywordCount();
            float[][] outputProbabilities = new float[1][keywordCount];

            //wd 3. 运行模型推理
            interpreter.run(inputFeatures, outputProbabilities);

            //wd 4. 解析输出，提取高概率关键词
            List<ExtractedKeyword> keywords = parseModelOutput(outputProbabilities[0], text);

            FileLog.d("wd AiKeywordExtractor 模型提取完成，找到 " + keywords.size() + " 个关键词");
            return keywords;

        } catch (Exception e) {
            FileLog.e("wd AiKeywordExtractor 模型推理失败", e);
            return extractWithRules(text);
        }
    }

    //wd 将文本转换为TF-IDF特征向量
    //wd 使用词汇表和IDF值计算TF-IDF
    private float[][] textToTfidf(String text) {
        //wd 分词并提取n-gram (1-3 gram)
        List<String> tokens = tokenize(text);
        List<String> ngrams = extractNgrams(tokens, 1, 3);

        //wd 计算词频 (TF)
        Map<String, Integer> termFreq = new HashMap<>();
        for (String ngram : ngrams) {
            termFreq.put(ngram, termFreq.getOrDefault(ngram, 0) + 1);
        }

        //wd 计算TF-IDF向量
        float[] tfidfVector = new float[vocabulary.size()];
        float norm = 0;

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            Integer idx = vocabulary.get(term);
            if (idx != null && idx < vocabulary.size()) {
                //wd TF = 词频 / 总词数
                float tf = (float) entry.getValue() / ngrams.size();
                //wd IDF 从预计算的数组中获取
                float idf = idx < idfValues.length ? idfValues[idx] : 1.0f;
                //wd TF-IDF
                float tfidf = tf * idf;
                tfidfVector[idx] = tfidf;
                norm += tfidf * tfidf;
            }
        }

        //wd L2 归一化
        if (norm > 0) {
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < tfidfVector.length; i++) {
                tfidfVector[i] /= norm;
            }
        }

        return new float[][]{tfidfVector};
    }

    //wd 分词 - 支持中文单字和英文单词
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isChinese(c)) {
                //wd 中文单字作为一个token
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString().toLowerCase());
                    currentWord.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                currentWord.append(c);
            } else {
                //wd 非字母数字字符作为分隔符
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString().toLowerCase());
                    currentWord.setLength(0);
                }
            }
        }

        //wd 处理最后一个词
        if (currentWord.length() > 0) {
            tokens.add(currentWord.toString().toLowerCase());
        }

        return tokens;
    }

    //wd 提取n-gram
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

    //wd 判断是否为中文
    private boolean isChinese(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    //wd 获取关键词数量
    private int getKeywordCount() {
        //wd 从配置中读取，默认为185
        return 185;
    }

    //wd 解析模型输出，提取关键词
    //wd 根据概率值提取高置信度的关键词
    private List<ExtractedKeyword> parseModelOutput(float[] probabilities, String originalText) {
        List<ExtractedKeyword> keywords = new ArrayList<>();
        String lowerText = originalText.toLowerCase();

        //wd 获取所有关键词标签
        List<String> keywordLabels = loadKeywordLabels();

        if (keywordLabels == null || keywordLabels.size() != probabilities.length) {
            FileLog.w("wd AiKeywordExtractor 关键词标签数量不匹配");
            return keywords;
        }

        //wd 提取概率 > 阈值的关键词
        float threshold = 0.5f; //wd 可配置阈值

        for (int i = 0; i < probabilities.length; i++) {
            float prob = probabilities[i];
            if (prob >= threshold) {
                String keyword = keywordLabels.get(i);

                //wd 统计关键词在原文中的出现频次
                int frequency = countOccurrences(lowerText, keyword.toLowerCase());

                //wd 如果模型预测有高概率但文本中没有直接出现，可能是变体或语义相关
                //wd 这种情况下频次设为1表示检测到
                if (frequency == 0) {
                    frequency = 1;
                }

                //wd 根据概率计算权重
                float weight = probabilityToWeight(prob);

                keywords.add(new ExtractedKeyword(keyword, weight, frequency));
                FileLog.d("wd AiKeywordExtractor 提取关键词: " + keyword + " (概率=" + prob + ", 权重=" + weight + ")");
            }
        }

        //wd 按权重排序
        Collections.sort(keywords, (a, b) -> Float.compare(b.weight, a.weight));

        return keywords;
    }

    //wd 将概率转换为权重
    //wd 概率越高，权重越高
    private float probabilityToWeight(float probability) {
        if (probability >= 0.9f) {
            return 0.95f;
        } else if (probability >= 0.8f) {
            return 0.85f;
        } else if (probability >= 0.7f) {
            return 0.75f;
        } else if (probability >= 0.6f) {
            return 0.65f;
        } else {
            return 0.55f;
        }
    }

    //wd 加载关键词标签列表
    private List<String> keywordLabels;

    private List<String> loadKeywordLabels() {
        if (keywordLabels != null) {
            return keywordLabels;
        }

        keywordLabels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("ai_ad_filter/keyword_extractor_labels.txt"))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    keywordLabels.add(line);
                }
            }
            reader.close();
            FileLog.d("wd AiKeywordExtractor 加载关键词标签: " + keywordLabels.size() + " 个");
        } catch (IOException e) {
            FileLog.e("wd AiKeywordExtractor 加载关键词标签失败", e);
            return null;
        }
        return keywordLabels;
    }

    //wd 使用规则引擎提取关键词
    @NonNull
    private List<ExtractedKeyword> extractWithRules(String text) {
        List<ExtractedKeyword> keywords = new ArrayList<>();
        String lowerText = text.toLowerCase();

        //wd 遍历所有广告关键词模式
        for (String pattern : AD_KEYWORD_PATTERNS) {
            int count = countOccurrences(lowerText, pattern.toLowerCase());
            if (count > 0) {
                float weight = getKeywordWeight(pattern);
                keywords.add(new ExtractedKeyword(pattern, weight, count));
            }
        }

        //wd 按权重排序
        Collections.sort(keywords, (a, b) -> Float.compare(b.weight, a.weight));

        FileLog.d("wd AiKeywordExtractor 规则引擎提取到 " + keywords.size() + " 个关键词");
        return keywords;
    }

    //wd 获取关键词权重
    private float getKeywordWeight(String keyword) {
        Float weight = KEYWORD_WEIGHTS.get(keyword);
        if (weight != null) {
            return weight;
        }

        //wd 对于变体词，尝试匹配原始词
        String normalized = normalizeVariant(keyword);
        weight = KEYWORD_WEIGHTS.get(normalized);
        if (weight != null) {
            return weight;
        }

        //wd 默认权重
        return 0.60f;
    }

    //wd 标准化变体词
    private String normalizeVariant(String keyword) {
        //wd 处理常见的变体形式
        return keyword.replace("薇", "微")
                .replace("v.x", "微信")
                .replace("vx", "微信")
                .replace("丄", "上")
                .replace("玳", "代")
                .replace("冲值", "充值")
                .replace("冲", "充")
                .replace("菠", "博")
                .replace("菜", "彩")
                .replace(".", "");
    }

    //wd 统计关键词出现次数
    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }

        return count;
    }

    //wd 标准化文本
    @NonNull
    private String normalizeText(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        normalized = normalized.replaceAll("[\\p{Cf}]+", "");
        normalized = normalized.replaceAll("[\\p{Z}]+", " ");

        //wd 处理常见异型字
        normalized = normalized.replace("薇", "微")
                .replace("丄", "上")
                .replace("沖", "冲")
                .replace("値", "值")
                .replace("玳", "代")
                .replace("菠", "博")
                .replace("菜", "彩")
                .replace("Ⓑ", "B")
                .replace("Ⓒ", "C");

        return normalized.trim();
    }

    //wd 检查模型是否已加载
    public boolean isModelLoaded() {
        return modelLoaded.get();
    }

    //wd 释放资源
    public void release() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        modelBuffer = null;
        modelLoaded.set(false);
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

        //wd 转换为特征对象
        public AiAdKeywordFeature toFeature(String source) {
            return new AiAdKeywordFeature(keyword, weight, frequency, "ad", source);
        }

        @NonNull
        @Override
        public String toString() {
            return "ExtractedKeyword{" +
                    "keyword='" + keyword + '\'' +
                    ", weight=" + weight +
                    ", frequency=" + frequency +
                    '}';
        }
    }
}
