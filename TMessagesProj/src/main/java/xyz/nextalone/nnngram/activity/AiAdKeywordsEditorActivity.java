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

import org.telegram.messenger.AiAdFeatureLibrary;
import org.telegram.messenger.AiAdKeywordFeature;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

//wd AI广告关键词特征库编辑器
//wd 用于编辑和管理AI广告过滤的关键词特征库
public class AiAdKeywordsEditorActivity extends BaseFragment {

    private EditTextBoldCursor editText;
    private File featureLibraryFile;

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
                    saveFeatureLibrary();
                } else if (id == 2) {
                    //wd 恢复默认
                    showRestoreDefaultDialog();
                }
            }
        });

        //wd 添加保存按钮
        actionBar.createMenu().addItem(1, R.drawable.ic_ab_done);
        actionBar.createMenu().addItem(2, R.drawable.ic_ab_other);

        fragmentView = new ScrollView(context);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(layout);

        //wd 添加说明头部
        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("AiAdKeywordsDesc", R.string.AiAdKeywordsDesc));
        layout.addView(headerCell);

        //wd 创建编辑框
        editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        editText.setHint("keyword,weight,frequency,category,source\n\n例如:\n博彩,0.95,150,ad,ai_extracted\n跑分,0.92,120,ad,ai_extracted");
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        //wd 添加底部说明
        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
        infoCell.setText("格式说明：\n关键词,权重(0.0-1.0),频次,分类(ad/normal),来源(ai_extracted/manual)");
        layout.addView(infoCell);

        //wd 初始化文件路径
        initFilePath();

        //wd 加载特征库
        loadFeatureLibrary();

        return fragmentView;
    }

    //wd 初始化特征库文件路径
    private void initFilePath() {
        File nnngramFilesDir = null;
        try {
            nnngramFilesDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES);
        } catch (Exception e) {
            FileLog.e("wd FileLoader.getDirectory() 失败，使用降级路径", e);
        }
        if (nnngramFilesDir == null) {
            File externalDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            if (externalDir != null) {
                nnngramFilesDir = new File(new File(externalDir, "Nnngram"), "Nnngram Files");
            } else {
                //wd 最终降级：使用应用私有目录
                nnngramFilesDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "Nnngram Files");
            }
        }
        File aiFilterDir = new File(nnngramFilesDir, "ai_ad_filter");
        if (!aiFilterDir.exists()) {
            aiFilterDir.mkdirs();
        }
        featureLibraryFile = new File(aiFilterDir, "ad_feature_library.txt");

        //wd 如果文件不存在，从assets复制
        if (!featureLibraryFile.exists()) {
            copyFromAssets();
        }
    }

    //wd 从assets复制默认文件
    private void copyFromAssets() {
        try {
            InputStream is = ApplicationLoader.applicationContext.getAssets().open("ai_ad_filter/ad_feature_library.txt");
            FileWriter writer = new FileWriter(featureLibraryFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line + "\n");
            }
            reader.close();
            writer.close();
            FileLog.d("wd 已复制默认特征库文件");
        } catch (IOException e) {
            FileLog.e("wd 复制特征库文件失败", e);
        }
    }

    //wd 加载特征库到编辑器
    private void loadFeatureLibrary() {
        if (!featureLibraryFile.exists()) {
            return;
        }

        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(featureLibraryFile));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            editText.setText(content.toString());
        } catch (IOException e) {
            FileLog.e("wd 读取特征库文件失败", e);
        }
    }

    //wd 保存特征库
    private void saveFeatureLibrary() {
        String content = editText.getText().toString();
        if (TextUtils.isEmpty(content)) {
            finishFragment();
            return;
        }

        try {
            FileWriter writer = new FileWriter(featureLibraryFile);
            writer.write(content);
            writer.close();

            //wd 重新加载特征库到AiAdFeatureLibrary
            AiAdFeatureLibrary library = AiAdFeatureLibrary.getInstance();
            if (library != null) {
                library.reloadFeatures();
                FileLog.d("wd AI广告特征库已重新加载");
            }

            FileLog.d("wd 特征库已保存并刷新缓存");
            finishFragment();
        } catch (IOException e) {
            FileLog.e("wd 保存特征库失败", e);
        }
    }

    //wd 显示恢复默认对话框
    private void showRestoreDefaultDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getString("RestoreDefault", R.string.RestoreDefault));
        builder.setMessage(LocaleController.getString("RestoreDefaultConfirm", R.string.RestoreDefaultConfirm));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            copyFromAssets();
            loadFeatureLibrary();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }
}
