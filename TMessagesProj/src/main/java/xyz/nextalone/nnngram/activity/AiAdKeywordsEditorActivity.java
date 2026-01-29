/*
 * Copyright (C) 2019-2023 qwq233 <qwq233@qwq2333.top>
 * https://github.com/qwq233/Nullgram
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this software.
 *  If not, see
 * <https://www.gnu.org/licenses/>
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
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageTopicAnalyzer;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class AiAdKeywordsEditorActivity extends BaseFragment {

    private EditTextBoldCursor editText;
    private File keywordsFile;

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
                    //wd 保存
                    saveKeywords();
                } else if (id == 2) {
                    //wd 恢复默认
                    showRestoreDefaultDialog();
                }
            }
        });

        //wd 添加保存按钮
        actionBar.createMenu().addItem(1, R.drawable.sticker_added, AndroidUtilities.dp(56));

        //wd 添加恢复默认按钮到菜单
        actionBar.createMenu().addItem(2, LocaleController.getString("RestoreDefault", R.string.RestoreDefault));

        //wd 初始化关键词文件路径
        initKeywordsFile();

        //wd 创建主布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        //wd 添加说明头部
        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("AiAdKeywordsDesc", R.string.AiAdKeywordsDesc));
        layout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        //wd 添加隐私说明
        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        infoCell.setText("每行一个关键词，格式：关键词,权重,分类\n分类：ad(广告), consult(咨询), chat(闲聊), greeting(问候)\n权重范围：0.0-1.0");
        layout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        //wd 创建ScrollView和EditText
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        FrameLayout frameLayout = new FrameLayout(context);

        editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setBackgroundDrawable(null);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        editText.setHint("输入关键词，每行一个...");

        frameLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        layout.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1.0f));

        //wd 加载现有内容
        loadKeywords();

        fragmentView = layout;
        return layout;
    }

    //wd 初始化关键词文件路径
    private void initKeywordsFile() {
        File externalDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        File nnngramFilesDir = new File(externalDir, "Nnngram Files");
        File aiFilterDir = new File(nnngramFilesDir, "ai_ad_filter");
        if (!aiFilterDir.exists()) {
            aiFilterDir.mkdirs();
        }
        keywordsFile = new File(aiFilterDir, "ad_keywords.txt");

        //wd 如果文件不存在，从assets复制
        if (!keywordsFile.exists()) {
            copyFromAssets();
        }
    }

    //wd 从assets复制默认文件
    private void copyFromAssets() {
        try {
            InputStream is = ApplicationLoader.applicationContext.getAssets().open("ai_ad_filter/ad_keywords.txt");
            FileWriter writer = new FileWriter(keywordsFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
            reader.close();
            writer.close();
            FileLog.d("wd 已复制默认关键词文件");
        } catch (IOException e) {
            FileLog.e("wd 复制关键词文件失败", e);
        }
    }

    //wd 加载关键词到编辑器
    private void loadKeywords() {
        if (!keywordsFile.exists()) {
            return;
        }

        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(keywordsFile));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            editText.setText(content.toString());
        } catch (IOException e) {
            FileLog.e("wd 读取关键词文件失败", e);
        }
    }

    //wd 保存关键词
    private void saveKeywords() {
        String content = editText.getText().toString();
        if (TextUtils.isEmpty(content)) {
            finishFragment();
            return;
        }

        try {
            FileWriter writer = new FileWriter(keywordsFile);
            writer.write(content);
            writer.close();

            //wd 重新加载关键词到分析器
            MessageTopicAnalyzer analyzer = MessageTopicAnalyzer.getInstance();
            if (analyzer != null) {
                analyzer.reloadKeywords();
            }

            FileLog.d("wd 关键词已保存");
            finishFragment();
        } catch (IOException e) {
            FileLog.e("wd 保存关键词失败", e);
        }
    }

    //wd 显示恢复默认对话框
    private void showRestoreDefaultDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getString("RestoreDefault", R.string.RestoreDefault));
        builder.setMessage(LocaleController.getString("RestoreDefaultConfirm", R.string.RestoreDefaultConfirm));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            copyFromAssets();
            loadKeywords();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }
}
