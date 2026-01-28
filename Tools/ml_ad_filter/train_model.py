#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI 广告过滤模型训练脚本
生成 TensorFlow Lite 模型和 TF-IDF vectorizer 参数
"""

import pandas as pd
import numpy as np
import tensorflow as tf
import json
import re
from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer

def normalize_text(text):
    """标准化文本，处理异型字"""
    if pd.isna(text):
        return ""
    text = str(text)
    # 替换常见异型字
    replacements = {
        '薇': '微', '丄': '上', '沖': '冲', '値': '值',
        '玳': '代', '菠': '博', '菜': '彩',
        'Ⓑ': 'B', 'Ⓒ': 'C',
        'v.x': 'vx', 'V.X': 'VX', 'V X': 'VX',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    # 移除零宽字符
    text = re.sub(r'[\u200b-\u200f\ufeff]', '', text)
    return text

def save_vectorizer(vectorizer, vocab_path, idf_path, config_path):
    """保存 vectorizer 参数"""
    # 保存词汇表
    vocab = vectorizer.vocabulary_
    with open(vocab_path, 'w', encoding='utf-8') as f:
        for word, idx in sorted(vocab.items(), key=lambda x: x[1]):
            f.write(f"{word}\n")
    
    # 保存 IDF 值
    idf = vectorizer.idf_
    with open(idf_path, 'w') as f:
        for val in idf:
            f.write(f"{val}\n")
    
    # 保存配置
    config = {
        'max_features': vectorizer.max_features,
        'ngram_range': list(vectorizer.ngram_range),
        'vocab_size': len(vocab)
    }
    with open(config_path, 'w') as f:
        json.dump(config, f, indent=2)
    
    print(f"  词汇表大小: {len(vocab)}")
    print(f"  配置: {config}")

def train_topic_model(df, output_dir):
    """训练主题分析模型"""
    print("\n" + "="*50)
    print("阶段 1：训练主题分析模型")
    print("="*50)
    
    # 准备主题分类数据
    topic_labels = df['topic'].unique()
    topic_to_idx = {t: i for i, t in enumerate(topic_labels)}
    idx_to_topic = {i: t for t, i in topic_to_idx.items()}
    
    print(f"主题类别: {list(topic_labels)}")
    
    # 分割数据
    topic_y = [topic_to_idx[t] for t in df['topic'].tolist()]
    X_train_topic, X_test_topic, y_train_topic, y_test_topic = train_test_split(
        df['text_normalized'].tolist(), topic_y, test_size=0.2, random_state=42, stratify=topic_y
    )
    
    # TF-IDF 特征提取
    topic_vectorizer = TfidfVectorizer(max_features=8000, ngram_range=(1, 3))
    X_train_topic_tfidf = topic_vectorizer.fit_transform(X_train_topic).toarray()
    X_test_topic_tfidf = topic_vectorizer.transform(X_test_topic).toarray()
    
    # 构建主题分类模型
    topic_model = tf.keras.Sequential([
        tf.keras.layers.Dense(128, activation='relu', input_shape=(X_train_topic_tfidf.shape[1],)),
        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.Dense(len(topic_labels), activation='softmax')
    ])
    
    topic_model.compile(optimizer='adam',
                       loss='sparse_categorical_crossentropy',
                       metrics=['accuracy'])
    
    # 训练
    history = topic_model.fit(X_train_topic_tfidf, np.array(y_train_topic),
                             epochs=15, batch_size=32,
                             validation_data=(X_test_topic_tfidf, np.array(y_test_topic)),
                             verbose=1)
    
    # 评估
    loss, acc = topic_model.evaluate(X_test_topic_tfidf, np.array(y_test_topic))
    print(f"主题模型测试集准确率: {acc:.4f}")
    
    # 保存主题模型
    converter = tf.lite.TFLiteConverter.from_keras_model(topic_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_topic_model = converter.convert()
    
    with open(f'{output_dir}/topic_model.tflite', 'wb') as f:
        f.write(tflite_topic_model)
    
    with open(f'{output_dir}/topic_labels.txt', 'w') as f:
        for i in range(len(topic_labels)):
            f.write(f"{idx_to_topic[i]}\n")
    
    # 保存 vectorizer 参数
    print("保存主题模型 vectorizer 参数...")
    save_vectorizer(topic_vectorizer, 
                   f'{output_dir}/topic_vocabulary.txt',
                   f'{output_dir}/topic_idf.txt',
                   f'{output_dir}/topic_config.json')
    
    print("主题分析模型训练完成！")
    return topic_model, topic_vectorizer

def train_ad_model(df, output_dir):
    """训练广告分类模型"""
    print("\n" + "="*50)
    print("阶段 2：训练广告分类模型")
    print("="*50)
    
    # 准备广告分类数据
    ad_labels = [0 if l == 'ad' else 1 for l in df['label'].tolist()]
    X_train_ad, X_test_ad, y_train_ad, y_test_ad = train_test_split(
        df['text_normalized'].tolist(), ad_labels, test_size=0.2, random_state=42, stratify=ad_labels
    )
    
    # TF-IDF 特征提取
    ad_vectorizer = TfidfVectorizer(max_features=8000, ngram_range=(1, 3))
    X_train_ad_tfidf = ad_vectorizer.fit_transform(X_train_ad).toarray()
    X_test_ad_tfidf = ad_vectorizer.transform(X_test_ad).toarray()
    
    # 构建广告分类模型
    ad_model = tf.keras.Sequential([
        tf.keras.layers.Dense(128, activation='relu', input_shape=(X_train_ad_tfidf.shape[1],)),
        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(32, activation='relu'),
        tf.keras.layers.Dense(2, activation='softmax')
    ])
    
    ad_model.compile(optimizer='adam',
                    loss='sparse_categorical_crossentropy',
                    metrics=['accuracy'])
    
    # 训练
    history = ad_model.fit(X_train_ad_tfidf, np.array(y_train_ad),
                          epochs=15, batch_size=32,
                          validation_data=(X_test_ad_tfidf, np.array(y_test_ad)),
                          verbose=1)
    
    # 评估
    loss, acc = ad_model.evaluate(X_test_ad_tfidf, np.array(y_test_ad))
    print(f"广告模型测试集准确率: {acc:.4f}")
    
    # 保存广告模型
    converter = tf.lite.TFLiteConverter.from_keras_model(ad_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_ad_model = converter.convert()
    
    with open(f'{output_dir}/model.tflite', 'wb') as f:
        f.write(tflite_ad_model)
    
    with open(f'{output_dir}/labels.txt', 'w') as f:
        f.write('ad\nnormal\n')
    
    # 保存 vectorizer 参数
    print("保存广告模型 vectorizer 参数...")
    save_vectorizer(ad_vectorizer,
                   f'{output_dir}/ad_vocabulary.txt',
                   f'{output_dir}/ad_idf.txt',
                   f'{output_dir}/ad_config.json')
    
    print("广告分类模型训练完成！")
    return ad_model, ad_vectorizer

def main():
    import os
    import sys
    
    # 设置输出目录
    output_dir = "output"
    if len(sys.argv) > 1:
        output_dir = sys.argv[1]
    
    os.makedirs(output_dir, exist_ok=True)
    
    # 加载训练数据
    data_path = "train.csv"
    if len(sys.argv) > 2:
        data_path = sys.argv[2]
    
    print(f"加载训练数据: {data_path}")
    df = pd.read_csv(data_path)
    print(f"总样本数: {len(df)}")
    print(f"广告样本: {len(df[df['label']=='ad'])}")
    print(f"正常样本: {len(df[df['label']=='normal'])}")
    
    # 文本预处理
    df['text_normalized'] = df['text'].apply(normalize_text)
    
    # 训练模型
    topic_model, topic_vectorizer = train_topic_model(df, output_dir)
    ad_model, ad_vectorizer = train_ad_model(df, output_dir)
    
    # 输出文件列表
    print("\n" + "="*50)
    print("生成的文件列表")
    print("="*50)
    files = [
        "model.tflite          # 广告分类模型",
        "labels.txt            # 广告标签 (ad, normal)",
        "ad_vocabulary.txt     # 广告模型词汇表",
        "ad_idf.txt           # 广告模型 IDF 值",
        "ad_config.json       # 广告模型配置",
        "topic_model.tflite    # 主题分析模型",
        "topic_labels.txt      # 主题标签",
        "topic_vocabulary.txt  # 主题模型词汇表",
        "topic_idf.txt        # 主题模型 IDF 值",
        "topic_config.json    # 主题模型配置"
    ]
    for f in files:
        print(f"  {output_dir}/{f}")
    
    print("\n部署到 Android 项目:")
    print("  将上述 .tflite, .txt 文件复制到:")
    print("  TMessagesProj/src/main/assets/ai_ad_filter/")
    
    print("\n训练完成！")

if __name__ == "__main__":
    main()
