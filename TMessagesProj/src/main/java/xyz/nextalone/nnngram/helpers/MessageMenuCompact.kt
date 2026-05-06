/*
 * Copyright (C) 2019-2026 qwq233 <qwq233@qwq2333.top>
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
package xyz.nextalone.nnngram.helpers

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ChatActivity
import xyz.nextalone.gen.Config
import xyz.nextalone.nnngram.config.ConfigManager

/**
 * Manages the per-option "render as icon-only in a compact bar" preference for
 * the chat message context menu. Persisted as a CSV of OPTION_* ints in
 * [Defines.compactMessageOptions]. See docs/compact-menu-mock.html for UX.
 */
object MessageMenuCompact {

    /**
     * Options that have special UI / sub-popups / mutable subtext and therefore
     * cannot be rendered as a simple icon button. Keep these always in the full
     * text popup regardless of user preference.
     */
    private val BLACKLIST: Set<Int> = setOf(
        ChatActivity.OPTION_TRANSLATE,                  // swipe-back to translator settings
        ChatActivity.OPTION_REMOVE_ADS,                 // gap separator before it
        ChatActivity.OPTION_ABOUT_REVENUE_SHARING_ADS,
        ChatActivity.OPTION_REPORT_AD,
        ChatActivity.OPTION_HIDE_SPONSORED_MESSAGE,
        ChatActivity.OPTION_QR,                         // dynamic visibility + subtext
        ChatActivity.OPTION_TRANSCRIBE,
        ChatActivity.OPTION_CANCEL_SENDING,
        ChatActivity.OPTION_SUGGESTION_EDIT_MESSAGE,
        ChatActivity.OPTION_SUGGESTION_EDIT_PRICE,
        ChatActivity.OPTION_SUGGESTION_EDIT_TIME,
        ChatActivity.OPTION_FACT_CHECK,
        ChatActivity.OPTION_RATE_CALL,
    )

    private const val MIGRATION_FLAG = "compactMenuMigrated_v1"

    private val migrated: Boolean by lazy {
        if (!ConfigManager.getBooleanOrFalse(MIGRATION_FLAG)) {
            // Earlier (reverted) builds shipped a user-facing "compact bar position"
            // int preference. Bar position now derives from tap location, so drop
            // the stale key. show* booleans + compactMessageOptions CSV are kept
            // as-is — old show=true silently maps to MODE_TEXT, show=false to
            // MODE_HIDE, and any pre-existing compact entries become MODE_ICON.
            ConfigManager.deleteValue("compactBarPosition")
            ConfigManager.putBoolean(MIGRATION_FLAG, true)
        }
        true
    }

    @JvmStatic
    fun isAllowed(option: Int): Boolean = option !in BLACKLIST

    // ---------- Compact (icon-only) ----------

    @JvmStatic
    fun isCompact(option: Int): Boolean {
        if (!isAllowed(option)) return false
        return getCompactSet().contains(option)
    }

    @JvmStatic
    fun getCompactSet(): Set<Int> {
        @Suppress("UNUSED_EXPRESSION") migrated
        return parseCsv(Config.compactMessageOptions)
    }

    @JvmStatic
    fun setCompact(option: Int, compact: Boolean) {
        if (!isAllowed(option)) return
        val set = getCompactSet().toMutableSet()
        if (compact) set.add(option) else set.remove(option)
        Config.compactMessageOptions = set.joinToString(",")
    }

    @JvmStatic
    fun setCompact(options: IntArray, compact: Boolean) {
        val set = getCompactSet().toMutableSet()
        for (o in options) {
            if (!isAllowed(o)) continue
            if (compact) set.add(o) else set.remove(o)
        }
        Config.compactMessageOptions = set.joinToString(",")
    }

    // ---------- Hidden ----------

    @JvmStatic
    fun isHidden(option: Int): Boolean = getHiddenSet().contains(option)

    @JvmStatic
    fun getHiddenSet(): Set<Int> {
        @Suppress("UNUSED_EXPRESSION") migrated
        return parseCsv(Config.hiddenMessageOptions)
    }

    @JvmStatic
    fun setHidden(options: IntArray, hidden: Boolean) {
        val set = getHiddenSet().toMutableSet()
        for (o in options) {
            if (hidden) set.add(o) else set.remove(o)
        }
        Config.hiddenMessageOptions = set.joinToString(",")
    }

    private fun parseCsv(raw: String): Set<Int> {
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').mapNotNullTo(mutableSetOf()) { it.trim().toIntOrNull() }
    }

    @JvmStatic
    fun pickCols(n: Int): Int = if (n <= 4) n.coerceAtLeast(1) else 4

    /**
     * One row in the Message Menu settings dialog. A single label may map to
     * multiple OPTION codes (e.g. SAVE_TO_GALLERY / SAVE_TO_GALLERY2 /
     * SAVE_STICKER_TO_GALLERY are aliases of "Save to gallery"). When the user
     * toggles a row, the change applies to every alias OPTION at once.
     */
    class Candidate(val options: IntArray, private val resId: Int) {
        val primaryOption: Int get() = options[0]
        val label: String get() = LocaleController.getString(resId)
    }

    @JvmField
    val CANDIDATES: List<Candidate> = listOf(
        Candidate(intArrayOf(ChatActivity.OPTION_REPLY), R.string.Reply),
        Candidate(intArrayOf(ChatActivity.OPTION_VIEW_REPLIES_OR_THREAD), R.string.ViewThread),
        Candidate(intArrayOf(ChatActivity.OPTION_EDIT), R.string.Edit),
        Candidate(intArrayOf(ChatActivity.OPTION_PIN), R.string.PinMessage),
        Candidate(intArrayOf(ChatActivity.OPTION_UNPIN), R.string.UnpinMessage),
        Candidate(intArrayOf(ChatActivity.OPTION_FORWARD), R.string.Forward),
        Candidate(intArrayOf(ChatActivity.OPTION_COPY), R.string.Copy),
        Candidate(intArrayOf(ChatActivity.OPTION_COPY_LINK), R.string.CopyLink),
        Candidate(
            intArrayOf(
                ChatActivity.OPTION_SAVE_TO_GALLERY,
                ChatActivity.OPTION_SAVE_TO_GALLERY2,
                ChatActivity.OPTION_SAVE_STICKER_TO_GALLERY,
            ),
            R.string.SaveToGallery,
        ),
        Candidate(intArrayOf(ChatActivity.OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC), R.string.SaveToDownloads),
        Candidate(intArrayOf(ChatActivity.OPTION_ADD_TO_STICKERS_OR_MASKS), R.string.AddToStickers),
        Candidate(intArrayOf(ChatActivity.OPTION_ADD_STICKER_TO_FAVORITES), R.string.AddToFavorites),
        Candidate(intArrayOf(ChatActivity.OPTION_DELETE_STICKER_FROM_FAVORITES), R.string.DeleteFromFavorites),
        Candidate(intArrayOf(ChatActivity.OPTION_ADD_TO_GIFS), R.string.SaveToGIFs),
        Candidate(intArrayOf(ChatActivity.OPTION_VIEW_IN_TOPIC), R.string.ViewInTopic),
        Candidate(intArrayOf(ChatActivity.OPTION_STATISTICS), R.string.Statistics),
        Candidate(intArrayOf(ChatActivity.OPTION_OPEN_PROFILE), R.string.OpenProfile),
        Candidate(intArrayOf(ChatActivity.OPTION_SHARE, ChatActivity.OPTION_SHARE_PHOTO), R.string.ShareFile),
    )
}
