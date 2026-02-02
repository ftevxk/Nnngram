/*
 * Copyright (C) 2024 Nnngram
 * 信息关键词提取结果对话框
 * 显示提取的关键词列表，允许用户选择添加到概率表
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

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AiKeywordExtractor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;

import java.util.ArrayList;
import java.util.List;

/**
 * 信息关键词提取结果对话框
 * 显示提取的关键词列表，允许用户选择哪些关键词添加到概率表
 */
public class KeywordExtractResultDialog extends AlertDialog {

    private final List<ExtractedKeywordItem> keywordItems;
    private final OnKeywordsSelectedListener listener;
    private RecyclerView listView;
    private KeywordAdapter adapter;

    /**
     * 提取的关键词项
     */
    public static class ExtractedKeywordItem {
        public final String keyword;
        public final float weight;
        public final int frequency;
        public boolean selected;
        public final boolean isNew;

        public ExtractedKeywordItem(String keyword, float weight, int frequency, boolean isNew) {
            this.keyword = keyword;
            this.weight = weight;
            this.frequency = frequency;
            this.isNew = isNew;
            this.selected = isNew && weight >= 0.7f; // 默认选中高权重的新关键词
        }

        public static ExtractedKeywordItem fromExtractedKeyword(AiKeywordExtractor.ExtractedKeyword kw, boolean isNew) {
            return new ExtractedKeywordItem(kw.keyword, kw.weight, kw.frequency, isNew);
        }
    }

    /**
     * 关键词选择回调接口
     */
    public interface OnKeywordsSelectedListener {
        void onKeywordsSelected(List<ExtractedKeywordItem> selectedKeywords);
        void onCancelled();
    }

    public KeywordExtractResultDialog(Context context, List<ExtractedKeywordItem> items, OnKeywordsSelectedListener listener) {
        super(context, 0);
        this.keywordItems = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //wd 设置窗口背景透明
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Context context = getContext();

        //wd 创建主布局
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        //wd 设置圆角背景
        container.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 
            Theme.getColor(Theme.key_dialogBackground)));

        //wd 标题
        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.KeywordExtractDialogTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setPadding(
            AndroidUtilities.dp(20),
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(20),
            AndroidUtilities.dp(8)
        );
        container.addView(titleView);

        //wd 副标题/说明
        TextView subtitleView = new TextView(context);
        subtitleView.setText(LocaleController.getString(R.string.KeywordExtractDialogSubtitle));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        subtitleView.setPadding(
            AndroidUtilities.dp(20),
            0,
            AndroidUtilities.dp(20),
            AndroidUtilities.dp(12)
        );
        container.addView(subtitleView);

        //wd 关键词列表
        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(true);

        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        //wd 添加关键词项
        for (int i = 0; i < keywordItems.size(); i++) {
            ExtractedKeywordItem item = keywordItems.get(i);
            KeywordCheckCell cell = new KeywordCheckCell(context, item);
            cell.setOnClickListener(v -> {
                item.selected = !item.selected;
                cell.setChecked(item.selected);
            });
            listContainer.addView(cell);
        }

        scrollView.addView(listContainer);
        container.addView(scrollView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            AndroidUtilities.dp(300) // 最大高度
        ));

        //wd 按钮区域
        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END);
        buttonLayout.setPadding(
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(12),
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(16)
        );

        //wd 取消按钮
        TextView cancelButton = new TextView(context);
        cancelButton.setText(LocaleController.getString(R.string.Cancel));
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        cancelButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        cancelButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCancelled();
            }
            dismiss();
        });
        buttonLayout.addView(cancelButton);

        //wd 添加按钮
        TextView addButton = new TextView(context);
        addButton.setText(LocaleController.getString(R.string.aiKeywordExtractAdd));
        addButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        addButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        addButton.setOnClickListener(v -> {
            List<ExtractedKeywordItem> selected = new ArrayList<>();
            for (ExtractedKeywordItem item : keywordItems) {
                if (item.selected) {
                    selected.add(item);
                }
            }
            if (listener != null) {
                listener.onKeywordsSelected(selected);
            }
            dismiss();
        });
        buttonLayout.addView(addButton);

        container.addView(buttonLayout);

        setContentView(container);
    }

    /**
     * 关键词选择单元格 - 使用Telegram标准CheckBoxCell
     */
    private static class KeywordCheckCell extends FrameLayout {

        private final CheckBoxCell checkBoxCell;
        private final ExtractedKeywordItem item;

        public KeywordCheckCell(Context context, ExtractedKeywordItem item) {
            super(context);
            this.item = item;

            setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);

            //wd 使用Telegram标准CheckBoxCell
            checkBoxCell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT);
            checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));

            //wd 设置文本：关键词 + 权重信息
            String weightStr = String.format("%.2f", item.weight);
            String weightLabel = LocaleController.getString(R.string.KeywordExtractWeightLabel);
            checkBoxCell.setText(item.keyword, weightLabel + weightStr, false, false);
            checkBoxCell.setChecked(item.selected, false);

            addView(checkBoxCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        }

        public void setChecked(boolean checked) {
            item.selected = checked;
            checkBoxCell.setChecked(checked, true);
        }

        public boolean isChecked() {
            return checkBoxCell.isChecked();
        }
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final Context context;
        private List<ExtractedKeywordItem> items = new ArrayList<>();
        private OnKeywordsSelectedListener listener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder addKeyword(ExtractedKeywordItem item) {
            items.add(item);
            return this;
        }

        public Builder addKeywords(List<ExtractedKeywordItem> items) {
            this.items.addAll(items);
            return this;
        }

        public Builder setOnKeywordsSelectedListener(OnKeywordsSelectedListener listener) {
            this.listener = listener;
            return this;
        }

        public KeywordExtractResultDialog create() {
            return new KeywordExtractResultDialog(context, items, listener);
        }

        public void show() {
            create().show();
        }
    }

    //wd 简单的适配器类占位
    private static class KeywordAdapter extends RecyclerView.Adapter<KeywordViewHolder> {
        private final List<ExtractedKeywordItem> items;

        public KeywordAdapter(List<ExtractedKeywordItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public KeywordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return null;
        }

        @Override
        public void onBindViewHolder(@NonNull KeywordViewHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class KeywordViewHolder extends RecyclerView.ViewHolder {
        public KeywordViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
