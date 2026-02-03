/*
 * Copyright (C) 2024 Nnngram
 * 广告关键词编辑器
 * 只管理广告关键词列表，不包含词频和类别
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

import org.telegram.messenger.AdFilter;
import org.telegram.messenger.AdKeywordsStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import xyz.nextalone.nnngram.config.ConfigManager;
import xyz.nextalone.nnngram.utils.Defines;

//wd 广告关键词编辑器
public class AdKeywordsEditorActivity extends BaseFragment {

    private RecyclerListView listView;
    private KeywordAdapter adapter;
    private AdKeywordsStore keywordsStore;
    private TextSettingsCell multiKeywordCell;
    private TextSettingsCell repeatKeywordCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("AdKeywords", R.string.AdKeywords));
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
                    //wd 清空所有关键词
                    showClearAllDialog();
                }
            }
        });

        //wd 添加按钮
        actionBar.createMenu().addItem(1, R.drawable.msg_add);
        actionBar.createMenu().addItem(2, R.drawable.msg_delete);

        keywordsStore = AdKeywordsStore.getInstance();

        //wd 创建主布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        //wd 添加配置区域头部
        HeaderCell configHeaderCell = new HeaderCell(context);
        configHeaderCell.setText(LocaleController.getString("AdKeywordsConfig", R.string.AdKeywordsConfig));
        layout.addView(configHeaderCell);

        //wd 多关键词阈值配置
        multiKeywordCell = new TextSettingsCell(context);
        multiKeywordCell.setBackground(Theme.getSelectorDrawable(false));
        updateMultiKeywordCell();
        multiKeywordCell.setOnClickListener(v -> showMultiKeywordThresholdDialog());
        layout.addView(multiKeywordCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        //wd 重复关键词阈值配置
        repeatKeywordCell = new TextSettingsCell(context);
        repeatKeywordCell.setBackground(Theme.getSelectorDrawable(false));
        updateRepeatKeywordCell();
        repeatKeywordCell.setOnClickListener(v -> showRepeatKeywordThresholdDialog());
        layout.addView(repeatKeywordCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        //wd 配置说明
        TextInfoPrivacyCell configInfoCell = new TextInfoPrivacyCell(context);
        configInfoCell.setText(LocaleController.getString("AdKeywordsConfigDesc", R.string.AdKeywordsConfigDesc));
        layout.addView(configInfoCell);

        //wd 添加关键词列表头部
        HeaderCell headerCell = new HeaderCell(context);
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
        infoCell.setText(LocaleController.getString("AdKeywordsDesc", R.string.AdKeywordsDesc));
        layout.addView(infoCell);

        fragmentView = layout;

        //wd 加载数据
        loadKeywords();

        return fragmentView;
    }

    //wd 更新多关键词阈值显示
    private void updateMultiKeywordCell() {
        int threshold = ConfigManager.getIntOrDefault(Defines.adFilterMultiKeywordThreshold, 2);
        multiKeywordCell.setTextAndValue(
                LocaleController.getString("AdKeywordsMultiThreshold", R.string.AdKeywordsMultiThreshold),
                String.valueOf(threshold),
                false);
    }

    //wd 更新重复关键词阈值显示
    private void updateRepeatKeywordCell() {
        int threshold = ConfigManager.getIntOrDefault(Defines.adFilterRepeatKeywordThreshold, 3);
        repeatKeywordCell.setTextAndValue(
                LocaleController.getString("AdKeywordsRepeatThreshold", R.string.AdKeywordsRepeatThreshold),
                String.valueOf(threshold),
                false);
    }

    //wd 显示多关键词阈值选择对话框
    private void showMultiKeywordThresholdDialog() {
        Context context = getContext();
        if (context == null) return;

        int currentValue = ConfigManager.getIntOrDefault(Defines.adFilterMultiKeywordThreshold, 2);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AdKeywordsMultiThreshold", R.string.AdKeywordsMultiThreshold));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), 0);

        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(1);
        picker.setMaxValue(10);
        picker.setValue(currentValue);
        layout.addView(picker);

        builder.setView(layout);

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            int newValue = picker.getValue();
            ConfigManager.putInt(Defines.adFilterMultiKeywordThreshold, newValue);
            updateMultiKeywordCell();

            //wd 刷新过滤器
            AdFilter filter = AdFilter.getInstance();
            if (filter != null) {
                filter.refreshFilterConfig();
            }

            FileLog.d("wd AdKeywordsEditor 设置多关键词阈值: " + newValue);
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 显示重复关键词阈值选择对话框
    private void showRepeatKeywordThresholdDialog() {
        Context context = getContext();
        if (context == null) return;

        int currentValue = ConfigManager.getIntOrDefault(Defines.adFilterRepeatKeywordThreshold, 3);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AdKeywordsRepeatThreshold", R.string.AdKeywordsRepeatThreshold));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), 0);

        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(1);
        picker.setMaxValue(10);
        picker.setValue(currentValue);
        layout.addView(picker);

        builder.setView(layout);

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            int newValue = picker.getValue();
            ConfigManager.putInt(Defines.adFilterRepeatKeywordThreshold, newValue);
            updateRepeatKeywordCell();

            //wd 刷新过滤器
            AdFilter filter = AdFilter.getInstance();
            if (filter != null) {
                filter.refreshFilterConfig();
            }

            FileLog.d("wd AdKeywordsEditor 设置重复关键词阈值: " + newValue);
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 加载关键词列表
    private void loadKeywords() {
        Set<String> keywords = keywordsStore.getAdKeywords();
        List<String> items = new ArrayList<>(keywords);

        //wd 按字母排序
        Collections.sort(items);

        adapter.setItems(items);
        adapter.notifyDataSetChanged();

        //wd 更新标题
        View header = fragmentView.findViewWithTag("header");
        if (header instanceof HeaderCell) {
            ((HeaderCell) header).setText(LocaleController.getString("AdKeywords", R.string.AdKeywords) +
                    " (" + items.size() + ")");
        }
    }

    //wd 显示添加关键词对话框
    private void showAddKeywordDialog() {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AdKeywordsAdd", R.string.AdKeywordsAdd));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), 0);

        //wd 关键词输入框
        EditTextBoldCursor keywordInput = new EditTextBoldCursor(context);
        keywordInput.setHint(LocaleController.getString("AdKeywordsHint", R.string.AdKeywordsHint));
        keywordInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        layout.addView(keywordInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String keyword = keywordInput.getText().toString().trim();

            if (TextUtils.isEmpty(keyword)) {
                return;
            }

            //wd 添加到关键词存储
            keywordsStore.addAdKeyword(keyword);
            keywordsStore.saveKeywords();

            //wd 刷新过滤器缓存
            AdFilter filter = AdFilter.getInstance();
            if (filter != null) {
                filter.refreshFilterConfig();
            }

            FileLog.d("wd AdKeywordsEditor 添加关键词: " + keyword);

            //wd 刷新列表
            loadKeywords();
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 显示清空所有关键词对话框
    private void showClearAllDialog() {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AdKeywordsClearAllTitle", R.string.AdKeywordsClearAllTitle));
        builder.setMessage(LocaleController.getString("AdKeywordsClearAllConfirm", R.string.AdKeywordsClearAllConfirm));

        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            keywordsStore.clearAdKeywords();
            keywordsStore.saveKeywords();

            //wd 刷新过滤器缓存
            AdFilter filter = AdFilter.getInstance();
            if (filter != null) {
                filter.refreshFilterConfig();
            }

            FileLog.d("wd AdKeywordsEditor 清空所有关键词");

            //wd 刷新列表
            loadKeywords();
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 显示删除确认对话框
    private void showDeleteConfirmDialog(String keyword) {
        Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AdKeywordsDeleteTitle", R.string.AdKeywordsDeleteTitle));
        builder.setMessage(LocaleController.formatString("AdKeywordsDeleteConfirm", R.string.AdKeywordsDeleteConfirm, keyword));

        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
            keywordsStore.removeAdKeyword(keyword);
            keywordsStore.saveKeywords();

            //wd 刷新过滤器缓存
            AdFilter filter = AdFilter.getInstance();
            if (filter != null) {
                filter.refreshFilterConfig();
            }

            FileLog.d("wd AdKeywordsEditor 删除关键词: " + keyword);

            //wd 刷新列表
            loadKeywords();
        });

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    //wd 列表适配器
    private class KeywordAdapter extends RecyclerListView.SelectionAdapter {

        private List<String> items = new ArrayList<>();

        void setItems(List<String> items) {
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
                String keyword = items.get(position);
                ((KeywordCell) holder.itemView).setData(keyword);
            }
        }
    }

    //wd 列表项视图
    private class KeywordCell extends FrameLayout {

        private final TextView keywordText;

        KeywordCell(Context context) {
            super(context);

            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            keywordText = new TextView(context);
            keywordText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            keywordText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(keywordText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.START | Gravity.CENTER_VERTICAL));

            setBackground(Theme.getSelectorDrawable(false));
        }

        void setData(String keyword) {
            keywordText.setText(keyword);
        }
    }

    //wd 列表项ViewHolder
    private class KeywordViewHolder extends RecyclerListView.ViewHolder {

        KeywordViewHolder(View itemView) {
            super(itemView);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position >= 0 && position < adapter.getItemCount()) {
                    String keyword = adapter.items.get(position);
                    showDeleteConfirmDialog(keyword);
                }
            });
        }
    }
}
