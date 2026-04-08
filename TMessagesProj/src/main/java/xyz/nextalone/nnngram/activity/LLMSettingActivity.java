/*
 * Copyright (C) 2019-2025 qwq233 <qwq233@qwq2333.top>
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Locale;

import xyz.nextalone.nnngram.config.ConfigManager;
import xyz.nextalone.nnngram.translate.providers.LLMTranslator;
import xyz.nextalone.nnngram.ui.PopupBuilder;
import xyz.nextalone.nnngram.utils.Defines;

@SuppressLint("NotifyDataSetChanged")
public class LLMSettingActivity extends BaseActivity {

    private int providerHeaderRow;
    private int llmProviderRow;
    private int llmApiFormatRow;
    private int llmApiFormatDescRow;
    private int llmApiUrlRow;
    private int providerHeader2Row;

    private int credentialHeaderRow;
    private int llmApiKeyRow;
    private int llmModelNameRow;
    private int llmModelFetchRow;
    private int credentialHeader2Row;

    private int advancedHeaderRow;
    private int llmSystemPromptRow;
    private int llmTemperatureRow;
    private int advancedHeader2Row;

    private int testConnectionRow;
    private int test2Row;

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("LLMTranslatorSettings", R.string.LLMTranslatorSettings);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == llmProviderRow) {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> ids = new ArrayList<>();
            names.add(LocaleController.getString("LLMProviderCustom", R.string.LLMProviderCustom)); ids.add(0);
            names.add(LocaleController.getString("LLMProviderOpenAI", R.string.LLMProviderOpenAI)); ids.add(1);
            names.add(LocaleController.getString("LLMProviderGemini", R.string.LLMProviderGemini)); ids.add(2);
            names.add(LocaleController.getString("LLMProviderGroq", R.string.LLMProviderGroq)); ids.add(3);
            names.add(LocaleController.getString("LLMProviderDeepSeek", R.string.LLMProviderDeepSeek)); ids.add(4);
            names.add(LocaleController.getString("LLMProviderXAI", R.string.LLMProviderXAI)); ids.add(5);
            names.add(LocaleController.getString("LLMProviderZhipuAI", R.string.LLMProviderZhipuAI)); ids.add(6);
            names.add(LocaleController.getString("LLMProviderMistral", R.string.LLMProviderMistral)); ids.add(7);
            names.add(LocaleController.getString("LLMProviderOpenRouter", R.string.LLMProviderOpenRouter)); ids.add(8);
            names.add(LocaleController.getString("LLMProviderQwen", R.string.LLMProviderQwen)); ids.add(9);
            names.add(LocaleController.getString("LLMProviderMoonshot", R.string.LLMProviderMoonshot)); ids.add(10);
            names.add(LocaleController.getString("LLMProviderSiliconFlow", R.string.LLMProviderSiliconFlow)); ids.add(11);

            int cur = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
            PopupBuilder.show(names, LocaleController.getString("LLMProvider", R.string.LLMProvider),
                ids.indexOf(cur), getParentActivity(), view, i -> {
                    int newProvider = ids.get(i);
                    ConfigManager.putInt(Defines.llmProvider, newProvider);

                    // Set default model if not configured
                    String modelKey = getLLMModelConfig(newProvider);
                    String currentModel = ConfigManager.getStringOrDefault(modelKey, "");
                    if (currentModel == null || currentModel.isEmpty()) {
                        String def = LLMTranslator.INSTANCE.getDefaultModel(newProvider);
                        if (def != null) {
                            ConfigManager.putString(modelKey, def);
                        }
                    }

                    updateRows();
                    listAdapter.notifyDataSetChanged();
                });
        } else if (position == llmApiFormatRow) {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> ids = new ArrayList<>();
            names.add(LocaleController.getString("LLMApiFormatOpenAIChat", R.string.LLMApiFormatOpenAIChat)); ids.add(0);
            names.add(LocaleController.getString("LLMApiFormatOpenAIResponse", R.string.LLMApiFormatOpenAIResponse)); ids.add(1);
            names.add(LocaleController.getString("LLMApiFormatAnthropic", R.string.LLMApiFormatAnthropic)); ids.add(2);
            names.add(LocaleController.getString("LLMApiFormatCustom", R.string.LLMApiFormatCustom)); ids.add(3);
            int cur = ConfigManager.getIntOrDefault(Defines.llmApiFormat, 0);
            PopupBuilder.show(names, LocaleController.getString("LLMApiFormat", R.string.LLMApiFormat),
                ids.indexOf(cur), getParentActivity(), view, i -> {
                    ConfigManager.putInt(Defines.llmApiFormat, ids.get(i));
                    listAdapter.notifyItemChanged(llmApiFormatRow, PARTIAL);
                    listAdapter.notifyItemChanged(llmApiFormatDescRow, PARTIAL);
                });
        } else if (position == llmApiUrlRow) {
            showInputDialog(Defines.llmApiUrl, R.string.LLMApiUrl, R.string.LLMApiUrlNotice, position);
        } else if (position == llmApiKeyRow) {
            int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
            showInputDialog(getLLMApiKeyConfig(provider), R.string.LLMApiKey, R.string.LLMApiKeyNotice, position);
        } else if (position == llmModelNameRow) {
            int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
            showInputDialog(getLLMModelConfig(provider), R.string.LLMModelName, R.string.LLMModelNameNotice, position);
        } else if (position == llmModelFetchRow) {
            fetchModels();
        } else if (position == llmSystemPromptRow) {
            showInputDialog(Defines.llmSystemPrompt, R.string.LLMSystemPrompt, R.string.LLMSystemPromptNotice, position);
        } else if (position == llmTemperatureRow) {
            showTemperatureDialog();
        } else if (position == testConnectionRow) {
            testConnection();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        return false;
    }

    @Override
    protected String getKey() {
        return "llm";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);

        providerHeaderRow = addRow();
        llmProviderRow = addRow("llmProvider");
        if (provider == 0) {
            llmApiFormatRow = addRow("llmApiFormat");
            llmApiFormatDescRow = addRow();
            llmApiUrlRow = addRow("llmApiUrl");
        } else {
            llmApiFormatRow = -1;
            llmApiFormatDescRow = -1;
            llmApiUrlRow = -1;
        }
        providerHeader2Row = addRow();

        credentialHeaderRow = addRow();
        llmApiKeyRow = addRow("llmApiKey");
        llmModelNameRow = addRow("llmModelName");
        llmModelFetchRow = addRow("llmModelFetch");
        credentialHeader2Row = addRow();

        advancedHeaderRow = addRow();
        llmSystemPromptRow = addRow("llmSystemPrompt");
        llmTemperatureRow = addRow("llmTemperature");
        advancedHeader2Row = addRow();

        testConnectionRow = addRow("llmTestConnection");
        test2Row = addRow();

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    // --- Helpers ---

    private String getLLMProviderName(int provider) {
        return switch (provider) {
            case 1 -> LocaleController.getString("LLMProviderOpenAI", R.string.LLMProviderOpenAI);
            case 2 -> LocaleController.getString("LLMProviderGemini", R.string.LLMProviderGemini);
            case 3 -> LocaleController.getString("LLMProviderGroq", R.string.LLMProviderGroq);
            case 4 -> LocaleController.getString("LLMProviderDeepSeek", R.string.LLMProviderDeepSeek);
            case 5 -> LocaleController.getString("LLMProviderXAI", R.string.LLMProviderXAI);
            case 6 -> LocaleController.getString("LLMProviderZhipuAI", R.string.LLMProviderZhipuAI);
            case 7 -> LocaleController.getString("LLMProviderMistral", R.string.LLMProviderMistral);
            case 8 -> LocaleController.getString("LLMProviderOpenRouter", R.string.LLMProviderOpenRouter);
            case 9 -> LocaleController.getString("LLMProviderQwen", R.string.LLMProviderQwen);
            case 10 -> LocaleController.getString("LLMProviderMoonshot", R.string.LLMProviderMoonshot);
            case 11 -> LocaleController.getString("LLMProviderSiliconFlow", R.string.LLMProviderSiliconFlow);
            default -> LocaleController.getString("LLMProviderCustom", R.string.LLMProviderCustom);
        };
    }

    private String getLLMApiKeyConfig(int provider) {
        return switch (provider) {
            case 1 -> Defines.llmOpenAIKey;
            case 2 -> Defines.llmGeminiKey;
            case 3 -> Defines.llmGroqKey;
            case 4 -> Defines.llmDeepSeekKey;
            case 5 -> Defines.llmXAIKey;
            case 6 -> Defines.llmZhipuAIKey;
            case 7 -> Defines.llmMistralKey;
            case 8 -> Defines.llmOpenRouterKey;
            case 9 -> Defines.llmQwenKey;
            case 10 -> Defines.llmMoonshotKey;
            case 11 -> Defines.llmSiliconFlowKey;
            default -> Defines.llmApiKey;
        };
    }

    private String getLLMModelConfig(int provider) {
        return switch (provider) {
            case 1 -> Defines.llmOpenAIModel;
            case 2 -> Defines.llmGeminiModel;
            case 3 -> Defines.llmGroqModel;
            case 4 -> Defines.llmDeepSeekModel;
            case 5 -> Defines.llmXAIModel;
            case 6 -> Defines.llmZhipuAIModel;
            case 7 -> Defines.llmMistralModel;
            case 8 -> Defines.llmOpenRouterModel;
            case 9 -> Defines.llmQwenModel;
            case 10 -> Defines.llmMoonshotModel;
            case 11 -> Defines.llmSiliconFlowModel;
            default -> Defines.llmModelName;
        };
    }

    private void showInputDialog(String configKey, int titleRes, int noticeRes, int rowPosition) {
        Context context = getParentActivity();
        if (context == null) return;

        org.telegram.ui.Components.EditTextBoldCursor editText = new org.telegram.ui.Components.EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHint(LocaleController.getString(titleRes));
        editText.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        editText.setText(ConfigManager.getStringOrDefault(configKey, ""));

        android.widget.FrameLayout frameLayout = new android.widget.FrameLayout(context);
        frameLayout.addView(editText, org.telegram.ui.Components.LayoutHelper.createFrame(
            org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
            org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
            android.view.Gravity.TOP, 16, 0, 16, 0));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(titleRes));
        builder.setMessage(LocaleController.getString(noticeRes));
        builder.setView(frameLayout);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialogInterface, i) -> {
            ConfigManager.putString(configKey, editText.getText().toString().trim());
            listAdapter.notifyItemChanged(rowPosition, PARTIAL);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showTemperatureDialog() {
        Context context = getParentActivity();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("LLMTemperature", R.string.LLMTemperature));

        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        float currentTemp = ConfigManager.getFloatOrDefault(Defines.llmTemperature, 0.7f);

        android.widget.TextView label = new android.widget.TextView(context);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setText(String.format(Locale.US, "%.1f", currentTemp));
        layout.addView(label, org.telegram.ui.Components.LayoutHelper.createLinear(
            org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
            org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
            android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        org.telegram.ui.Components.SeekBarView seekBar = new org.telegram.ui.Components.SeekBarView(context);
        seekBar.setReportChanges(true);
        float[] tempValue = {currentTemp};
        seekBar.setDelegate(new org.telegram.ui.Components.SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                tempValue[0] = progress * 2.0f;
                label.setText(String.format(Locale.US, "%.1f", tempValue[0]));
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }
        });
        seekBar.setProgress(currentTemp / 2.0f);
        layout.addView(seekBar, org.telegram.ui.Components.LayoutHelper.createLinear(
            org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, 38));

        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
            ConfigManager.putFloat(Defines.llmTemperature, tempValue[0]);
            listAdapter.notifyItemChanged(llmTemperatureRow, PARTIAL);
        });
        builder.show();
    }

    private void fetchModels() {
        int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
        String apiKey = ConfigManager.getStringOrDefault(getLLMApiKeyConfig(provider), "");
        if (apiKey == null || apiKey.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(
                LocaleController.getString("LLMNoModelsFound", R.string.LLMNoModelsFound)).show();
            return;
        }

        String customUrl = provider == 0 ? ConfigManager.getStringOrDefault(Defines.llmApiUrl, "") : null;

        // Show loading
        listAdapter.notifyItemChanged(llmModelFetchRow, PARTIAL);

        new Thread(() -> {
            try {
                java.util.List<String> models = LLMTranslator.INSTANCE
                    .fetchModelsBlocking(provider, apiKey, customUrl);

                AndroidUtilities.runOnUIThread(() -> {
                    if (models == null || models.isEmpty()) {
                        BulletinFactory.of(LLMSettingActivity.this).createErrorBulletin(
                            LocaleController.getString("LLMNoModelsFound", R.string.LLMNoModelsFound)).show();
                        return;
                    }

                    Context context = getParentActivity();
                    if (context == null) return;

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(LocaleController.getString("LLMSelectModel", R.string.LLMSelectModel));
                    String[] modelArray = models.toArray(new String[0]);
                    builder.setItems(modelArray, (dialog, which) -> {
                        String modelKey = getLLMModelConfig(provider);
                        ConfigManager.putString(modelKey, modelArray[which]);
                        listAdapter.notifyItemChanged(llmModelNameRow, PARTIAL);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.show();
                });
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() ->
                    BulletinFactory.of(LLMSettingActivity.this).createErrorBulletin(
                        LocaleController.getString("LLMNoModelsFound", R.string.LLMNoModelsFound)).show());
            }
        }).start();
    }

    private void testConnection() {
        int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
        String apiKey = ConfigManager.getStringOrDefault(getLLMApiKeyConfig(provider), "");
        if (apiKey == null || apiKey.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(
                String.format(LocaleController.getString("LLMTestFailed", R.string.LLMTestFailed), "API Key is empty")).show();
            return;
        }

        String modelKey = getLLMModelConfig(provider);
        String model = ConfigManager.getStringOrDefault(modelKey, "");
        if (model == null || model.isEmpty()) {
            String def = LLMTranslator.INSTANCE.getDefaultModel(provider);
            model = def != null ? def : "";
        }
        if (model.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(
                String.format(LocaleController.getString("LLMTestFailed", R.string.LLMTestFailed), "Model is empty")).show();
            return;
        }

        String customUrl = provider == 0 ? ConfigManager.getStringOrDefault(Defines.llmApiUrl, "") : null;
        int apiFormat = ConfigManager.getIntOrDefault(Defines.llmApiFormat, 0);
        String finalModel = model;

        new Thread(() -> {
            String error = LLMTranslator.INSTANCE
                .testConnectionBlocking(provider, apiKey, finalModel, customUrl, apiFormat);

            AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    BulletinFactory.of(LLMSettingActivity.this).createSimpleBulletin(
                        R.raw.contact_check,
                        LocaleController.getString("LLMTestSuccess", R.string.LLMTestSuccess)).show();
                } else {
                    String msg = error.length() > 100 ? error.substring(0, 100) + "..." : error;
                    BulletinFactory.of(LLMSettingActivity.this).createErrorBulletin(
                        String.format(LocaleController.getString("LLMTestFailed", R.string.LLMTestFailed), msg)).show();
                }
            });
        }).start();
    }

    // --- Adapter ---

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_SHADOW: {
                    if (position == test2Row) {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == providerHeaderRow) {
                        cell.setText(LocaleController.getString("LLMProvider", R.string.LLMProvider));
                    } else if (position == credentialHeaderRow) {
                        cell.setText(LocaleController.getString("LLMApiKey", R.string.LLMApiKey));
                    } else if (position == advancedHeaderRow) {
                        cell.setText(LocaleController.getString("General", R.string.General));
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == llmProviderRow) {
                        int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
                        cell.setTextAndValue(LocaleController.getString("LLMProvider", R.string.LLMProvider),
                            getLLMProviderName(provider), payload, true);
                    } else if (position == llmApiFormatRow) {
                        int format = ConfigManager.getIntOrDefault(Defines.llmApiFormat, 0);
                        String value = switch (format) {
                            case 1 -> LocaleController.getString("LLMApiFormatOpenAIResponse", R.string.LLMApiFormatOpenAIResponse);
                            case 2 -> LocaleController.getString("LLMApiFormatAnthropic", R.string.LLMApiFormatAnthropic);
                            case 3 -> LocaleController.getString("LLMApiFormatCustom", R.string.LLMApiFormatCustom);
                            default -> LocaleController.getString("LLMApiFormatOpenAIChat", R.string.LLMApiFormatOpenAIChat);
                        };
                        cell.setTextAndValue(LocaleController.getString("LLMApiFormat", R.string.LLMApiFormat),
                            value, payload, true);
                    } else if (position == llmApiUrlRow) {
                        String value = ConfigManager.getStringOrDefault(Defines.llmApiUrl, "");
                        if (value != null && value.length() > 30) {
                            value = value.substring(0, 30) + "...";
                        }
                        cell.setTextAndValue(LocaleController.getString("LLMApiUrl", R.string.LLMApiUrl),
                            value != null ? value : "", payload, false);
                    } else if (position == llmApiKeyRow) {
                        int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
                        String value = ConfigManager.getStringOrDefault(getLLMApiKeyConfig(provider), "");
                        if (value != null && value.length() > 10) {
                            value = value.substring(0, 4) + "****" + value.substring(value.length() - 4);
                        }
                        cell.setTextAndValue(LocaleController.getString("LLMApiKey", R.string.LLMApiKey),
                            value != null ? value : "", payload, true);
                    } else if (position == llmModelNameRow) {
                        int provider = ConfigManager.getIntOrDefault(Defines.llmProvider, 0);
                        String value = ConfigManager.getStringOrDefault(getLLMModelConfig(provider), "");
                        cell.setTextAndValue(LocaleController.getString("LLMModelName", R.string.LLMModelName),
                            value != null ? value : "", payload, true);
                    } else if (position == llmModelFetchRow) {
                        cell.setText(LocaleController.getString("LLMFetchModels", R.string.LLMFetchModels), true);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                    } else if (position == llmSystemPromptRow) {
                        String value = ConfigManager.getStringOrDefault(Defines.llmSystemPrompt, "");
                        if (value != null && value.length() > 30) {
                            value = value.substring(0, 30) + "...";
                        }
                        cell.setTextAndValue(LocaleController.getString("LLMSystemPrompt", R.string.LLMSystemPrompt),
                            value != null ? value : "", payload, true);
                    } else if (position == llmTemperatureRow) {
                        float temp = ConfigManager.getFloatOrDefault(Defines.llmTemperature, 0.7f);
                        cell.setTextAndValue(LocaleController.getString("LLMTemperature", R.string.LLMTemperature),
                            String.format(Locale.US, "%.1f", temp), payload, false);
                    } else if (position == testConnectionRow) {
                        cell.setText(LocaleController.getString("LLMTestConnection", R.string.LLMTestConnection), false);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == llmApiFormatDescRow) {
                        int format = ConfigManager.getIntOrDefault(Defines.llmApiFormat, 0);
                        String desc = switch (format) {
                            case 1 -> LocaleController.getString("LLMApiFormatOpenAIResponseDesc", R.string.LLMApiFormatOpenAIResponseDesc);
                            case 2 -> LocaleController.getString("LLMApiFormatAnthropicDesc", R.string.LLMApiFormatAnthropicDesc);
                            case 3 -> LocaleController.getString("LLMApiFormatCustomDesc", R.string.LLMApiFormatCustomDesc);
                            default -> LocaleController.getString("LLMApiFormatOpenAIChatDesc", R.string.LLMApiFormatOpenAIChatDesc);
                        };
                        cell.setText(desc);
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_SETTINGS;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == providerHeader2Row || position == credentialHeader2Row
                || position == advancedHeader2Row || position == test2Row) {
                return TYPE_SHADOW;
            } else if (position == providerHeaderRow || position == credentialHeaderRow
                || position == advancedHeaderRow) {
                return TYPE_HEADER;
            } else if (position == llmApiFormatDescRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SETTINGS;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return super.onCreateViewHolder(parent, viewType);
        }
    }
}
