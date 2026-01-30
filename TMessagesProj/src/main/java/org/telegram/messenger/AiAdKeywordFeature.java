/*
 * Copyright (C) 2024 Nnngram
 * AI广告关键词特征数据类
 * 用于存储单个关键词的特征信息
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

import androidx.annotation.NonNull;

//wd AI广告关键词特征数据类
//wd 用于存储单个关键词的特征信息，包括权重、频次、分类和来源
public class AiAdKeywordFeature {

    //wd 关键词文本
    public final String keyword;

    //wd 权重，范围0.0-1.0，表示该关键词的广告特征强度
    public final float weight;

    //wd 频次，表示该关键词在训练数据中出现的次数
    public final int frequency;

    //wd 分类：ad(广告)或normal(正常)
    public final String category;

    //wd 来源：ai_extracted(AI提取)或manual(手动添加)
    public final String source;

    //wd 构造函数
    public AiAdKeywordFeature(String keyword, float weight, int frequency, String category, String source) {
        this.keyword = keyword != null ? keyword.trim() : "";
        this.weight = Math.max(0f, Math.min(1f, weight));
        this.frequency = Math.max(1, frequency);
        this.category = category != null ? category.trim().toLowerCase() : "ad";
        this.source = source != null ? source.trim().toLowerCase() : "manual";
    }

    //wd 从CSV行解析特征
    //wd 格式：关键词,权重,频次,分类,来源
    public static AiAdKeywordFeature fromCsvLine(String line) {
        if (line == null || line.trim().isEmpty() || line.startsWith("#")) {
            return null;
        }

        String[] parts = line.split(",");
        if (parts.length < 2) {
            return null;
        }

        try {
            String keyword = parts[0].trim();
            float weight = Float.parseFloat(parts[1].trim());
            int frequency = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1;
            String category = parts.length > 3 ? parts[3].trim() : "ad";
            String source = parts.length > 4 ? parts[4].trim() : "manual";

            return new AiAdKeywordFeature(keyword, weight, frequency, category, source);
        } catch (NumberFormatException e) {
            FileLog.e("wd AiAdKeywordFeature 解析CSV失败: " + line, e);
            return null;
        }
    }

    //wd 转换为CSV行
    //wd 格式：关键词,权重,频次,分类,来源
    public String toCsvLine() {
        return keyword + "," + weight + "," + frequency + "," + category + "," + source;
    }

    //wd 获取计算后的有效权重（考虑频次）
    //wd 频次越高，权重贡献越大
    public float getEffectiveWeight() {
        //wd 使用频次加权公式：weight * (1 + log(frequency)) / 2
        //wd 这样可以在保持权重在合理范围内的同时，体现频次的影响
        float freqFactor = (float) (1 + Math.log10(frequency)) / 2f;
        return weight * Math.min(freqFactor, 2.0f);
    }

    //wd 判断是否为广告关键词
    public boolean isAdKeyword() {
        return "ad".equals(category);
    }

    //wd 判断是否来自AI提取
    public boolean isFromAi() {
        return "ai_extracted".equals(source);
    }

    //wd 判断是否手动添加
    public boolean isManual() {
        return "manual".equals(source);
    }

    @NonNull
    @Override
    public String toString() {
        return "AiAdKeywordFeature{" +
                "keyword='" + keyword + '\'' +
                ", weight=" + weight +
                ", frequency=" + frequency +
                ", category='" + category + '\'' +
                ", source='" + source + '\'' +
                ", effectiveWeight=" + getEffectiveWeight() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiAdKeywordFeature that = (AiAdKeywordFeature) o;
        return keyword.equalsIgnoreCase(that.keyword);
    }

    @Override
    public int hashCode() {
        return keyword.toLowerCase().hashCode();
    }
}
