/*
 * Copyright (C) 2024 Nnngram
 * AI 广告消息过滤器
 * 使用 TensorFlow Lite Interpreter 进行文本分类
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
import androidx.annotation.Nullable;

import org.tensorflow.lite.Interpreter;
import xyz.nextalone.nnngram.config.ConfigManager;
import xyz.nextalone.nnngram.utils.Defines;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class MessageAiAdFilter {
    private static volatile MessageAiAdFilter instance;
    private final Context context;
    private Interpreter interpreter;
    private MappedByteBuffer modelBuffer;
    private SimpleAdDetector simpleDetector;
    private AtomicBoolean useSimpleDetector = new AtomicBoolean(false);
    private List<String> labels;
    private final Object interpreterLock = new Object();
    private final Map<String, FilterResult> cache = new HashMap<>();
    private final Object cacheLock = new Object();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final AtomicBoolean useBertClassifier = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private float threshold = 0.85f;
    private boolean strictMode = false;

    private static final String MODEL_PATH = "ai_ad_filter/model.tflite";
    private static final String LABELS_PATH = "ai_ad_filter/labels.txt";
    private static final String CACHE_KEY_SEPARATOR = "_";

    private String messageFilterTextCached;
    private Pattern messageFilterPatternCached;
    private ArrayList<String> messageFilterKeywordsCached;
    private final Object filterConfigLock = new Object();

    private static class FilterResult {
        final boolean isAd;
        final float confidence;
        final long timestamp;

        FilterResult(boolean isAd, float confidence) {
            this.isAd = isAd;
            this.confidence = confidence;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private MessageAiAdFilter(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
    }

    private void loadConfig() {
        threshold = ConfigManager.getFloatOrDefault(Defines.aiAdFilterThreshold, 0.85f);
        threshold = Math.max(0f, Math.min(1f, threshold));
        strictMode = ConfigManager.getBooleanOrDefault(Defines.aiAdFilterStrictMode, false);
        updateMessageFilterCache();
    }

    public static MessageAiAdFilter getInstance(Context context) {
        if (instance == null) {
            synchronized (MessageAiAdFilter.class) {
                if (instance == null) {
                    instance = new MessageAiAdFilter(context);
                }
            }
        }
        return instance;
    }

    public static MessageAiAdFilter getInstance() {
        if (instance == null) {
            return null;
        }
        return instance;
    }

    public void initialize() {
        if (isInitialized.get() || isInitializing.getAndSet(true)) {
            return;
        }
        executor.execute(() -> {
            try {
                loadModel();
            } finally {
                isInitializing.set(false);
            }
        });
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        try (android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(MODEL_PATH);
             java.io.FileInputStream inputStream = afd.createInputStream();
             java.nio.channels.FileChannel channel = inputStream.getChannel()) {
            return channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.getStartOffset(), afd.getLength());
        } catch (java.io.FileNotFoundException e) {
            FileLog.d("wd MessageAiAdFilter: model file not found in assets: " + MODEL_PATH);
            return null;
        }
    }

    private void loadModel() {
        synchronized (interpreterLock) {
            try {
                FileLog.d("wd MessageAiAdFilter: loading model from " + MODEL_PATH);
                modelBuffer = loadModelFile();
                if (modelBuffer == null) {
                    FileLog.e("wd MessageAiAdFilter: model file not found");
                    return;
                }
                labels = loadLabels();
                if (labels == null || labels.isEmpty()) {
                    FileLog.e("wd MessageAiAdFilter: failed to load labels");
                    return;
                }
                FileLog.d("wd MessageAiAdFilter: loaded " + labels.size() + " labels: " + labels);

                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(4);
                interpreter = new Interpreter(modelBuffer, options);

                isInitialized.set(true);
                FileLog.d("wd MessageAiAdFilter: initialized successfully with TFLite Interpreter, model loaded!");
            } catch (IOException e) {
                FileLog.e("wd MessageAiAdFilter: failed to load model", e);
            } catch (Exception e) {
                FileLog.e("wd MessageAiAdFilter: initialization error", e);
            }
        }
    }

    private List<String> loadLabels() {
        List<String> labelList = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABELS_PATH))
            );
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    labelList.add(line);
                }
            }
            reader.close();
        } catch (IOException e) {
            FileLog.e("wd MessageAiAdFilter: failed to load labels", e);
            return null;
        }
        return labelList;
    }

    public void setThreshold(float threshold) {
        this.threshold = Math.max(0f, Math.min(1f, threshold));
        ConfigManager.putFloat(Defines.aiAdFilterThreshold, this.threshold);
        clearCache();
    }

    public float getThreshold() {
        return threshold;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
        ConfigManager.putBoolean(Defines.aiAdFilterStrictMode, strictMode);
        clearCache();
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public boolean isEnabled() {
        return ConfigManager.getBooleanOrDefault(Defines.aiAdFilterEnabled, false);
    }

    public boolean shouldFilter(@NonNull MessageObject messageObject) {
        if (!isEnabled()) {
            return false;
        }
        String text = getMessageText(messageObject);
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return shouldFilter(text, messageObject.getDialogId(), messageObject.getId());
    }

    private String getMessageText(@NonNull MessageObject messageObject) {
        if (!TextUtils.isEmpty(messageObject.messageText)) {
            return messageObject.messageText.toString();
        }
        if (!TextUtils.isEmpty(messageObject.caption)) {
            return messageObject.caption.toString();
        }
        return "";
    }

    public boolean shouldFilter(@NonNull String text, long dialogId, long messageId) {
        if (!isEnabled()) {
            FileLog.d("wd AI ad filter: disabled, skipping message " + messageId);
            return false;
        }
        if (TextUtils.isEmpty(text)) {
            FileLog.d("wd AI ad filter: empty text, skipping message " + messageId);
            return false;
        }

        String cacheKey = buildCacheKey(dialogId, messageId, text);
        FilterResult cached = getFromCache(cacheKey);
        if (cached != null) {
            FileLog.d("wd AI ad filter: cache hit for message " + messageId + ", isAd=" + cached.isAd);
            return cached.isAd;
        }

        if (!isInitialized.get()) {
            FileLog.d("wd AI ad filter: not initialized, initializing now, message " + messageId);
            initialize();
            return false;
        }

        if (isMessageFilterBlocked(text)) {
            FileLog.d("wd AI ad filter: FILTERED message " + messageId + " by message filter (regex/keywords)");
            putToCache(cacheKey, new FilterResult(true, 1.0f));
            return true;
        }

        try {
            float adScore = classifyText(text);
            FileLog.d("wd AI ad filter: message " + messageId + ", score=" + String.format("%.4f", adScore) + 
                ", threshold=" + String.format("%.4f", threshold) + ", strictMode=" + strictMode);
            boolean isAd = adScore >= threshold;

            if (strictMode && adScore >= threshold * 0.7f) {
                isAd = true;
            }

            if (isAd) {
                putToCache(cacheKey, new FilterResult(isAd, adScore));
                FileLog.d("wd AI ad filter: FILTERED message " + messageId + " with confidence " + String.format("%.4f", adScore));
            }

            return isAd;
        } catch (Exception e) {
            FileLog.e("wd MessageAiAdFilter inference error", e);
            return false;
        }
    }

    private void updateMessageFilterCache() {
        String filterText = ConfigManager.getStringOrDefault(Defines.messageFilter, "");
        synchronized (filterConfigLock) {
            if (TextUtils.equals(filterText, messageFilterTextCached)) {
                return;
            }

            Pattern pattern = null;
            ArrayList<String> literalKeywords = null;

            if (!TextUtils.isEmpty(filterText)) {
                try {
                    pattern = Pattern.compile(filterText, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    FileLog.e("wd 无效的消息过滤正则表达式: " + filterText, e);
                }

                String[] filterTokens = filterText.split("\\|");
                for (int i = 0; i < filterTokens.length; i++) {
                    String token = filterTokens[i];
                    if (token == null) {
                        continue;
                    }
                    token = token.trim();
                    if (token.isEmpty()) {
                        continue;
                    }

                    boolean hasRegexMeta = false;
                    for (int c = 0; c < token.length(); c++) {
                        char ch = token.charAt(c);
                        if (ch == '\\' || ch == '.' || ch == '*' || ch == '+' || ch == '?' || ch == '^' || ch == '$' || ch == '[' || ch == ']' || ch == '(' || ch == ')' || ch == '{' || ch == '}') {
                            hasRegexMeta = true;
                            break;
                        }
                    }
                    if (hasRegexMeta) {
                        continue;
                    }

                    String normalizedToken = Normalizer.normalize(token, Normalizer.Form.NFKC);
                    String cleanToken = normalizedToken.replaceAll("[\\p{P}\\p{S}\\p{Z}\\s\\p{Cf}]+", "");
                    if (cleanToken.isEmpty()) {
                        continue;
                    }
                    if (literalKeywords == null) {
                        literalKeywords = new ArrayList<>();
                    }
                    literalKeywords.add(cleanToken);
                }
            }

            messageFilterTextCached = filterText;
            messageFilterPatternCached = pattern;
            messageFilterKeywordsCached = literalKeywords;
        }
    }

    private boolean isMessageFilterBlocked(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        synchronized (filterConfigLock) {
            Pattern pattern = messageFilterPatternCached;
            ArrayList<String> keywords = messageFilterKeywordsCached;
            if (pattern == null && (keywords == null || keywords.isEmpty())) {
                return false;
            }

            String normalizedText = Normalizer.normalize(text, Normalizer.Form.NFKC);
            String cleanText = normalizedText.replaceAll("[\\p{P}\\p{S}\\p{Z}\\s\\p{Cf}]+", "");

            if (pattern != null && pattern.matcher(cleanText).find()) {
                return true;
            }

            if (keywords != null && !keywords.isEmpty()) {
                int totalKeywords = keywords.size();
                int matchedKeywords = 0;

                for (String keyword : keywords) {
                    if (!TextUtils.isEmpty(keyword) && cleanText.contains(keyword)) {
                        matchedKeywords++;
                    }
                }

                float matchRatio = (float) matchedKeywords / totalKeywords;
                int totalLength = cleanText.length();
                int adContentLength = 0;

                for (String keyword : keywords) {
                    if (!TextUtils.isEmpty(keyword) && cleanText.contains(keyword)) {
                        int startIndex = 0;
                        while (true) {
                            int index = cleanText.indexOf(keyword, startIndex);
                            if (index == -1) break;
                            adContentLength += keyword.length();
                            startIndex = index + keyword.length();
                        }
                    }
                }

                float lengthRatio = totalLength > 0 ? (float) adContentLength / totalLength : 0f;

                boolean isMostlyAd = matchRatio >= 0.5f || lengthRatio >= 0.3f;
                if (isMostlyAd) {
                    FileLog.d("wd MessageAiAdFilter: ad content ratio=" + lengthRatio + ", keyword ratio=" + matchRatio);
                }
                return isMostlyAd;
            }

            return false;
        }
    }

    private float classifyText(String text) {
        Interpreter classifier;
        synchronized (interpreterLock) {
            classifier = this.interpreter;
        }

        if (classifier == null) {
            return useSimpleDetectorFallback(text);
        }

        try {
            return classifyWithInterpreter(text, classifier);
        } catch (Exception e) {
            FileLog.e("wd MessageAiAdFilter classification error, falling back to simple detector", e);
            return useSimpleDetectorFallback(text);
        }
    }

    private float useSimpleDetectorFallback(String text) {
        if (simpleDetector == null) {
            simpleDetector = SimpleAdDetector.getInstance();
        }
        float score = simpleDetector.getAdScore(text);
        if (score > 0) {
            useSimpleDetector.set(true);
            FileLog.d("wd MessageAiAdFilter: using simple detector, score=" + score);
        }
        return score;
    }

    private float classifyWithInterpreter(String text, Interpreter classifier) {
        try {
            int[] inputShape = classifier.getInputTensor(0).shape();
            int[] outputShape = classifier.getOutputTensor(0).shape();
            FileLog.d("wd MessageAiAdFilter: model input shape=" + java.util.Arrays.toString(inputShape) + 
                ", output shape=" + java.util.Arrays.toString(outputShape));
            
            int numLabels = labels.size();
            
            float[][] input = textToFloats(text);
            int inputLength = input.length;
            
            // 根据模型输入形状准备数据
            Object inputBuffer;
            if (inputShape.length == 2) {
                // 模型期望 [batch, features]
                // 确保批次维度正确
                if (inputShape[0] <= 0) {
                    inputShape[0] = 1;
                }
                if (inputShape[1] <= 0) {
                    inputShape[1] = inputLength;
                }
                float[][] shapedInput = new float[inputShape[0]][inputShape[1]];
                for (int i = 0; i < Math.min(inputLength, inputShape[1]); i++) {
                    shapedInput[0][i] = input[i][0];
                }
                inputBuffer = shapedInput;
            } else if (inputShape.length == 3) {
                // 模型期望 [batch, seq_len, features]
                if (inputShape[0] <= 0) inputShape[0] = 1;
                if (inputShape[1] <= 0) inputShape[1] = inputLength;
                if (inputShape[2] <= 0) inputShape[2] = 1;
                float[][][] input3d = new float[inputShape[0]][inputShape[1]][inputShape[2]];
                for (int i = 0; i < Math.min(inputLength, inputShape[1]); i++) {
                    input3d[0][i][0] = input[i][0];
                }
                inputBuffer = input3d;
            } else {
                FileLog.e("wd MessageAiAdFilter: unsupported input shape length: " + inputShape.length);
                return 0f;
            }
            
            // 根据模型输出形状分配内存
            Object outputBuffer;
            if (outputShape.length == 2) {
                if (outputShape[0] <= 0) outputShape[0] = 1;
                if (outputShape[1] <= 0) outputShape[1] = numLabels;
                outputBuffer = new float[outputShape[0]][outputShape[1]];
            } else {
                outputBuffer = new float[1][numLabels];
            }
            
            classifier.run(inputBuffer, outputBuffer);
            
            // 提取结果
            float adScore = 0f;
            if (outputBuffer instanceof float[][]) {
                float[][] output = (float[][]) outputBuffer;
                for (int i = 0; i < Math.min(numLabels, output[0].length); i++) {
                    String label = labels.get(i).toLowerCase();
                    if ("ad".equals(label) || "spam".equals(label)) {
                        adScore = Math.max(adScore, output[0][i]);
                    }
                    if ("normal".equals(label) || "ham".equals(label)) {
                        adScore = Math.max(adScore, 1.0f - output[0][i]);
                    }
                }
            }
            
            return adScore;
        } catch (Exception e) {
            FileLog.e("wd MessageAiAdFilter: interpreter.run failed", e);
            return 0f;
        }
    }

    private float[][] textToFloats(String text) {
        int maxLen = Math.min(text.length(), 128);
        float[][] result = new float[maxLen][1];
        for (int i = 0; i < maxLen; i++) {
            result[i][0] = text.charAt(i) / 128.0f;
        }
        return result;
    }

    private String buildCacheKey(long dialogId, long messageId, String text) {
        int hash = text.hashCode();
        return dialogId + CACHE_KEY_SEPARATOR + messageId + CACHE_KEY_SEPARATOR + hash;
    }

    @Nullable
    private FilterResult getFromCache(String cacheKey) {
        synchronized (cacheLock) {
            FilterResult result = cache.get(cacheKey);
            if (result != null && System.currentTimeMillis() - result.timestamp < 3600000) {
                return result;
            }
            return null;
        }
    }

    private void putToCache(String cacheKey, FilterResult result) {
        synchronized (cacheLock) {
            if (cache.size() >= 10000) {
                cache.clear();
            }
            cache.put(cacheKey, result);
        }
    }

    public void clearCache() {
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    public void release() {
        executor.shutdownNow();
        synchronized (interpreterLock) {
            if (interpreter != null) {
                interpreter.close();
                interpreter = null;
            }
            modelBuffer = null;
        }
        isInitialized.set(false);
        clearCache();
    }

    public boolean isModelLoaded() {
        return isInitialized.get() && interpreter != null;
    }
}
