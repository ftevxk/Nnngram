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

package xyz.nextalone.nnngram.utils

import android.content.Context
import android.content.DialogInterface
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.LaunchActivity
import org.telegram.ui.TwoStepVerificationActivity
import kotlin.apply

object PrivacyUtils {

    @JvmStatic
    fun postCheckAll(ctx: Context, account: Int) {
        if (!MessagesController.getMainSettings(account).getBoolean("privacy_warning_skip_phone_number", false)) {
            postCheckPhoneNumberVisible(ctx, account)
        }
        if (!MessagesController.getMainSettings(account).getBoolean("privacy_warning_skip_add_by_phone", false)) {
            postCheckAddMeByPhone(ctx, account)
        }
        if (!MessagesController.getMainSettings(account).getBoolean("privacy_warning_skip_p2p", false)) {
            postCheckAllowP2p(ctx, account)
        }
        if (!MessagesController.getMainSettings(account).getBoolean("privacy_warning_skip_2fa", false)) {
            postCheckAllow2fa(ctx, account)
        }

    }

    private fun postCheckPhoneNumberVisible(ctx: Context, account: Int) {

        ConnectionsManager.getInstance(account).sendRequest(TL_account.getPrivacy().apply {
            key = TLRPC.TL_inputPrivacyKeyPhoneNumber()
        }, { response, _ ->
            if (response is TL_account.privacyRules) {
                if (response.rules.isEmpty()) {
                    AndroidUtilities.runOnUIThread {
                        showPrivacyAlert(ctx, account, 0)
                    }
                } else {
                    response.rules.forEach {
                        if (it is TLRPC.TL_privacyValueAllowAll) {
                            AndroidUtilities.runOnUIThread {
                                showPrivacyAlert(ctx, account, 0)
                            }
                            return@forEach
                        }
                    }
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors)
    }

    private fun postCheckAddMeByPhone(ctx: Context, account: Int) {
        ConnectionsManager.getInstance(account).sendRequest(TL_account.getPrivacy().apply {
            key = TLRPC.TL_inputPrivacyKeyAddedByPhone()
        }, { response, _ ->
            if (response is TL_account.privacyRules) {
                if (response.rules.isEmpty()) {
                    AndroidUtilities.runOnUIThread {
                        showPrivacyAlert(ctx, account, 1)
                    }
                } else {
                    response.rules.forEach {
                        if (it is TLRPC.TL_privacyValueAllowAll) {
                            AndroidUtilities.runOnUIThread {
                                showPrivacyAlert(ctx, account, 1)
                            }
                            return@forEach
                        }
                    }
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors)
    }

    private fun postCheckAllowP2p(ctx: Context, account: Int) {
        ConnectionsManager.getInstance(account).sendRequest(TL_account.getPrivacy().apply {
            key = TLRPC.TL_inputPrivacyKeyPhoneP2P()
        }, { response, _ ->
            if (response is TL_account.privacyRules) {
                if (response.rules.isEmpty()) {
                    AndroidUtilities.runOnUIThread {
                        showPrivacyAlert(ctx, account, 2)
                    }
                } else {
                    response.rules.forEach {
                        if (it is TLRPC.TL_privacyValueAllowAll) {
                            AndroidUtilities.runOnUIThread {
                                showPrivacyAlert(ctx, account, 2)
                            }
                            return@forEach
                        }
                    }
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors)
    }

    private fun postCheckAllow2fa(ctx: Context, account: Int) {
        ConnectionsManager.getInstance(account).sendRequest(TL_account.getPassword(), { response, _ ->
            if (response is TL_account.Password) {
                if (!response.has_password) {
                    AndroidUtilities.runOnUIThread {
                        show2faAlert(ctx, account, response)
                    }
                }
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors)
    }

    private fun showPrivacyAlert(ctx: Context, account: Int, type: Int) {
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(LocaleController.getString("PrivacyNotice", R.string.PrivacyNotice))
        builder.setMessage(
            AndroidUtilities.replaceTags(
                when (type) {
                    0 -> LocaleController.getString(
                        "PrivacyNoticePhoneVisible", R.string.PrivacyNoticePhoneVisible
                    )
                    1 -> LocaleController.getString(
                        "PrivacyNoticeAddByPhone", R.string.PrivacyNoticeAddByPhone
                    )
                    else -> LocaleController.getString(
                        "PrivacyNoticeP2p", R.string.PrivacyNoticeP2p
                    )
                }
            )
        )
        builder.setPositiveButton(LocaleController.getString("ApplySuggestion", R.string.ApplySuggestion)) { _, _ ->
            ConnectionsManager.getInstance(account).sendRequest(TL_account.setPrivacy().apply {
                key = when (type) {
                    0 -> TLRPC.TL_inputPrivacyKeyPhoneNumber()
                    1 -> TLRPC.TL_inputPrivacyKeyAddedByPhone()
                    else -> TLRPC.TL_inputPrivacyKeyPhoneP2P()
                }
                rules = arrayListOf(
                    when (type) {
                        0 -> TLRPC.TL_inputPrivacyValueDisallowAll()
                        1 -> TLRPC.TL_inputPrivacyValueAllowContacts()
                        else -> TLRPC.TL_inputPrivacyValueDisallowAll()
                    }
                )
            }) { _, _ -> /* ignored */ }

        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
        builder.setNeutralButton(
            LocaleController.getString(
                "DoNotRemindAgain", R.string.DoNotRemindAgain
            )
        ) { _, _ ->
            MessagesController.getMainSettings(account).edit().putBoolean(
                "privacy_warning_skip_${
                    when (type) {
                        0 -> "phone_number"
                        1 -> "add_by_phone"
                        2 -> "p2p"
                        else -> "2fa"
                    }
                }", true
            ).apply()
        }
        runCatching {
            (builder.show().getButton(DialogInterface.BUTTON_NEUTRAL) as TextView?)?.setTextColor(
                Theme.getColor(Theme.key_text_RedRegular)
            )
        }
    }

    private fun show2faAlert(ctx: Context, account: Int, password: TL_account.Password) {
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(LocaleController.getString("PrivacyNotice", R.string.PrivacyNotice))
        builder.setMessage(
            AndroidUtilities.replaceTags(
                LocaleController.getString("PrivacyNotice2fa", R.string.PrivacyNotice2fa)
            )
        )
        builder.setPositiveButton(LocaleController.getString("Set", R.string.Set)) { _, _ ->
            if (ctx is LaunchActivity) {
                ApplicationLoader.applicationHandler.post{
                    ctx.presentFragment(TwoStepVerificationActivity(account, password))
                }
            }
        }
        builder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), null)
        builder.setNeutralButton(
            LocaleController.getString(
                "DoNotRemindAgain", R.string.DoNotRemindAgain
            )
        ) { _, _ ->
            MessagesController.getMainSettings(account).edit().putBoolean("privacy_warning_skip_2fa", true).apply()
        }
        runCatching {
            (builder.show().getButton(DialogInterface.BUTTON_NEUTRAL) as TextView?)?.setTextColor(
                Theme.getColor(Theme.key_text_RedRegular)
            )
        }
    }
}
