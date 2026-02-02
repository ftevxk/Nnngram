/*
 * Copyright (C) 2024 Nnngram
 * 贝叶斯广告关键词编辑器
 * 用于编辑和管理贝叶斯分类器的广告关键词
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

package xyz.nextalone.nnngram.activity;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BayesianProbabilityTable;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

//wd 贝叶斯广告关键词编辑器
//wd 用于编辑和管理贝叶斯分类器的广告/正常关键词
public class AiAdKeywordsEditorActivity extends BaseFragment {

    private RecyclerListView listView;
    private KeywordAdapter adapter;
    private BayesianProbabilityTable probTable;

    //wd 当前选中的类别
    private String currentClass = BayesianProbabilityTable.CLASS_AD;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("AiAdKeywords", R.string.AiAdKeywords));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    //wd 添加关键词
                    showAddKeywordDialog();
                } else if (id == 2) {
                    //wd 切换类别
                    showSwitchClassDialog();
                }
            }
        });

        //wd 添加按钮
        actionBar.createMenu().addItem(1, R.drawable.msg_add);
        actionBar.createMenu().addItem(2, R.drawable.ic_ab_other);

        probTable = BayesianProbabilityTable.getInstance();

        //wd 创建主布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        //wd 添加说明头部
        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(getCurrentClassDesc());
        headerCell.setTag("header");
        layout.addView(headerCell);

        //wd 创建列表
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new KeywordAdapter();
        listView.setAdapter(adapter);
        layout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        //wd 添加底部说明
        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        infoCell.setText("点击关键词可编辑词频，长按可删除\n词频越高，该关键词对分类的影响越大");
        layout.addView(infoCell);

        fragmentView = layout;

        //wd 加载数据
        loadKeywords();

        return fragmentView;
    }

    //wd 获取当前类别描述
    private String getCurrentClassDesc() {
        int count = (adapter != null) ? adapter.getItemCount() : 0;
        if (BayesianProbabilityTable.CLASS_AD.equals(currentClass)) {
            return "广告关键词 (" + count + ")";
        } else {
            return "正常关键词 (" + count + ")";
        }
    }

    //wd 加载关键词列表
    private void loadKeywords() {
        Map<String, Integer> features = probTable.getFeaturesByClass(currentClass);
        List<KeywordItem> items = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : features.entrySet()) {
            items.add(new KeywordItem(entry.getKey(), entry.getValue()));
        }

        //wd 按词频降序排序
        Collections.sort(items, (a, b) -> Integer.compare(b.count, a.count));

        adapter.setItems(items);
        adapter.notifyDataSetChanged();

        //wd 更新标题
        View header = fragmentView.findViewWithTag("header");
        if (header instanceof HeaderCell) {
            ((HeaderCell) header).setText(getCurrentClassDesc());
        }
    }

    //wd 显示添加关键词对话框
    private void showAddKeywordDialog() {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("添加关键词");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), 0);

        //wd 关键词输入框
        EditTextBoldCursor keywordInput = new EditTextBoldCursor(context);
        keywordInput.setHint("关键词");
        keywordInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        layout.addView(keywordInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        //wd 词频输入框
        EditTextBoldCursor countInput = new EditTextBoldCursor(context);
        countInput.setHint("词频 (建议1-100)");
        countInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(countInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));

        builder.setView(layout);

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String keyword = keywordInput.getText().toString().trim();
            String countStr = countInput.getText().toString().trim();

            if (TextUtils.isEmpty(keyword)) {
                return;
            }

            int count = 10; //wd 默认词频
            if (!TextUtils.isEmpty(countStr)) {
                try {
                    count = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    //wd 使用默认值
                }
            }

            //wd 添加到概率表
            probTable.setFeatureCount(keyword, currentClass, count);
            probTable.saveProbabilities();

            FileLog.d("wd AiAdKeywordsEditor 添加关键词: " + keyword + "=" + count);

            //wd 刷新列表
            loadKeywords();
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 显示切换类别对话框
    private void showSwitchClassDialog() {
        Context context = getContext();
        if (context == null) return;

        String[] items = {"广告关键词", "正常关键词"};

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择类别");
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                currentClass = BayesianProbabilityTable.CLASS_AD;
            } else {
                currentClass = BayesianProbabilityTable.CLASS_NORMAL;
            }
            loadKeywords();
        });
        showDialog(builder.create());
    }

    //wd 显示编辑词频对话框
    private void showEditCountDialog(KeywordItem item) {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("编辑词频: " + item.keyword);

        EditTextBoldCursor countInput = new EditTextBoldCursor(context);
        countInput.setText(String.valueOf(item.count));
        countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        countInput.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));

        builder.setView(countInput);

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String countStr = countInput.getText().toString().trim();
            if (TextUtils.isEmpty(countStr)) return;

            try {
                int count = Integer.parseInt(countStr);
                if (count <= 0) {
                    //wd 删除关键词
                    probTable.removeFeature(item.keyword, currentClass);
                } else {
                    //wd 更新词频
                    probTable.setFeatureCount(item.keyword, currentClass, count);
                }
                probTable.saveProbabilities();
                loadKeywords();
            } catch (NumberFormatException e) {
                //wd 忽略
            }
        });

        builder.setNeutralButton("删除", (dialog, which) -> {
            probTable.removeFeature(item.keyword, currentClass);
            probTable.saveProbabilities();
            loadKeywords();
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 关键词数据类
    private static class KeywordItem {
        final String keyword;
        final int count;

        KeywordItem(String keyword, int count) {
            this.keyword = keyword;
            this.count = count;
        }
    }

    //wd 列表适配器
    private class KeywordAdapter extends RecyclerListView.SelectionAdapter {

        private List<KeywordItem> items = new ArrayList<>();

        void setItems(List<KeywordItem> items) {
            this.items = items;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new KeywordViewHolder(new KeywordCell(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof KeywordCell) {
                KeywordItem item = items.get(position);
                ((KeywordCell) holder.itemView).setData(item);
            }
        }
    }

    //wd 列表项视图
    private class KeywordCell extends FrameLayout {

        private final TextView keywordText;
        private final TextView countText;

        KeywordCell(Context context) {
            super(context);

            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            keywordText = new TextView(context);
            keywordText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            keywordText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(keywordText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.START | Gravity.CENTER_VERTICAL));

            countText = new TextView(context);
            countText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            countText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            addView(countText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.END | Gravity.CENTER_VERTICAL));

            setBackground(Theme.getSelectorDrawable(false));
        }

        void setData(KeywordItem item) {
            keywordText.setText(item.keyword);
            countText.setText("词频: " + item.count);
        }
    }

    //wd 列表项ViewHolder
    private class KeywordViewHolder extends RecyclerListView.ViewHolder {

        KeywordViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position >= 0 && position < adapter.getItemCount()) {
                    KeywordItem item = adapter.items.get(position);
                    showEditCountDialog(item);
                }
            });
        }
    }
}
