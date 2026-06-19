/*
 * Copyright (C) 2019-2024 qwq233 <qwq233@qwq2333.top>
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

package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//wd 加入群组后弹框选择文件夹的 BottomSheet
public class JoinGroupFolderSelectSheet extends BottomSheet {

    public interface Callback {
        void onFoldersSelected(List<Long> selectedIds);
    }

    private final Callback callback;
    private final Set<Integer> selectedFilterIds = new HashSet<>();
    private final List<MessagesController.DialogFilter> customFilters = new ArrayList<>();

    public JoinGroupFolderSelectSheet(Context context, BaseFragment fragment, long chatId, Theme.ResourcesProvider resourcesProvider, Callback callback) {
        super(context, false, resourcesProvider);
        this.callback = callback;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundWhite));

        int currentAccount = fragment.getCurrentAccount();
        ArrayList<MessagesController.DialogFilter> allFilters = MessagesController.getInstance(currentAccount).getDialogFilters();
        if (allFilters != null) {
            for (MessagesController.DialogFilter filter : allFilters) {
                if (filter != null && !filter.isDefault()) {
                    customFilters.add(filter);
                }
            }
        }
        FileLog.d("wd JoinGroupFolderSelectSheet 构造，chatId=" + chatId + "，自定义文件夹数=" + customFilters.size());

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.FolderSelectOnJoin));
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        container.addView(titleView);

        for (MessagesController.DialogFilter filter : customFilters) {
            CheckBoxCell cell = new CheckBoxCell(context, 0, resourcesProvider);
            cell.setText(filter.name, "", false, false);
            final int filterId = filter.id;
            cell.setOnClickListener(v -> {
                boolean checked = !selectedFilterIds.contains(filterId);
                if (checked) {
                    selectedFilterIds.add(filterId);
                } else {
                    selectedFilterIds.remove(filterId);
                }
                cell.setChecked(checked, true);
            });
            container.addView(cell);
        }

        TextView confirmButton = new TextView(context);
        confirmButton.setText(LocaleController.getString(R.string.OK));
        confirmButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        confirmButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        confirmButton.setGravity(Gravity.CENTER);
        confirmButton.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        confirmButton.setOnClickListener(v -> {
            List<Long> ids = new ArrayList<>();
            for (Integer id : selectedFilterIds) {
                ids.add(id.longValue());
            }
            FileLog.d("wd JoinGroupFolderSelectSheet 确认，选中文件夹数=" + ids.size());
            if (callback != null) {
                callback.onFoldersSelected(ids);
            }
            dismiss();
        });
        container.addView(confirmButton);

        setCustomView(container);
    }

    public static void show(BaseFragment fragment, long chatId, Theme.ResourcesProvider resourcesProvider, Callback callback) {
        if (fragment == null || callback == null) {
            FileLog.d("wd JoinGroupFolderSelectSheet.show fragment 或 callback 为空");
            return;
        }
        Context context = fragment.getContext();
        if (context == null) {
            FileLog.d("wd JoinGroupFolderSelectSheet.show context 为空");
            callback.onFoldersSelected(new ArrayList<>());
            return;
        }
        FileLog.d("wd JoinGroupFolderSelectSheet.show 显示文件夹选择 Sheet，chatId=" + chatId);
        JoinGroupFolderSelectSheet sheet = new JoinGroupFolderSelectSheet(context, fragment, chatId, resourcesProvider, callback);
        sheet.show();
    }
}
