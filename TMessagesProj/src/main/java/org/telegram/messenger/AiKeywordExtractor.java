/*
 * Copyright (C) 2024 Nnngram
 * AI关键词提取器 - 贝叶斯版本
 * 使用贝叶斯特征提取器从文本中提取广告关键词
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

//wd AI关键词提取器 - 贝叶斯版本
//wd 使用贝叶斯特征提取器从文本中提取广告关键词
//wd 此类保留以兼容旧代码，实际工作委托给BayesianFeatureExtractor
public class AiKeywordExtractor {

    private static volatile AiKeywordExtractor instance;

    //wd 贝叶斯特征提取器
    private BayesianFeatureExtractor bayesianFeatureExtractor;

    //wd 上下文
    private Context context;

    //wd 私有构造函数
    private AiKeywordExtractor() {
        bayesianFeatureExtractor = BayesianFeatureExtractor.getInstance();
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
        bayesianFeatureExtractor.init(context);
        FileLog.d("wd AiKeywordExtractor 初始化完成（贝叶斯版本）");
    }

    //wd 提取关键词
    //wd 实际工作委托给BayesianFeatureExtractor
    @NonNull
    public List<ExtractedKeyword> extractKeywords(String text) {
        if (TextUtils.isEmpty(text)) {
            return Collections.emptyList();
        }

        //wd 使用贝叶斯特征提取器提取关键词
        List<BayesianFeatureExtractor.ExtractedKeyword> bayesianKeywords =
                bayesianFeatureExtractor.extractKeywords(text);

        //wd 转换为旧格式
        List<ExtractedKeyword> keywords = new ArrayList<>();
        for (BayesianFeatureExtractor.ExtractedKeyword kw : bayesianKeywords) {
            keywords.add(new ExtractedKeyword(kw.keyword, kw.weight, kw.frequency));
        }

        FileLog.d("wd AiKeywordExtractor 提取到 " + keywords.size() + " 个关键词");
        return keywords;
    }

    //wd 检查模型是否已加载
    public boolean isModelLoaded() {
        return true; //wd 贝叶斯版本总是就绪
    }

    //wd 释放资源
    public void release() {
        //wd 贝叶斯版本无需释放资源
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
