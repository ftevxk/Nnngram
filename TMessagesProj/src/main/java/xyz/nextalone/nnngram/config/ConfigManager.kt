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
package xyz.nextalone.nnngram.config

import android.annotation.SuppressLint
import android.app.Activity
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.json.JSONException
import org.json.JSONObject
import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.UserConfig
import xyz.nextalone.nnngram.utils.Log
import java.util.function.Function

object ConfigManager {
    private fun getPreferences(account: Int = UserConfig.selectedAccount): SharedPreferences {
        return ApplicationLoader.applicationContext.getSharedPreferences(
            "globalConfig$account",
            Activity.MODE_PRIVATE
        )
    }

    /**
     * 获取Int值
     *
     * @param key key
     * @param def 默认值
     * @return key所对应值
     */
    @JvmStatic
    @JvmOverloads
    fun getIntOrDefault(key: String, def: Int, account: Int = UserConfig.selectedAccount): Int {
        return getPreferences(account).getInt(key, def)
    }

    /**
     * 获取Long值
     *
     * @param key key
     * @param def 默认值
     */
    @JvmStatic
    @JvmOverloads
    fun getLongOrDefault(key: String, def: Long, account: Int = UserConfig.selectedAccount): Long {
        return getPreferences(account).getLong(key, def)
    }

    /**
     * 获取boolean值
     *
     * @param key key
     * @param def 默认值
     * @return key所对应值
     */
    @JvmStatic
    @JvmOverloads
    fun getBooleanOrDefault(key: String, def: Boolean, account: Int = UserConfig.selectedAccount): Boolean {
        return getPreferences(account).getBoolean(key, def)
    }

    /**
     * 获取boolean值
     *
     * @param key key
     * @return key所对应值 默认为false
     */
    @JvmStatic
    @JvmOverloads
    fun getBooleanOrFalse(key: String, account: Int = UserConfig.selectedAccount): Boolean {
        return getPreferences(account).getBoolean(key, false)
    }

    /**
     * 获取String值
     *
     * @param key key
     * @param def 默认值
     * @return key所对应值
     */
    @JvmStatic
    @JvmOverloads
    fun getStringOrDefault(key: String, def: String?, account: Int = UserConfig.selectedAccount): String? {
        return getPreferences(account).getString(key, def)
    }

    /**
     * 获取Float值
     *
     * @param key key
     * @param def 默认值
     * @return key所对应值
     */
    @JvmStatic
    @JvmOverloads
    fun getFloatOrDefault(key: String, def: Float, account: Int = UserConfig.selectedAccount): Float {
        return getPreferences(account).getFloat(key, def)
    }

    /**
     * 获取一个 StringSet
     *
     * @param key key
     * @param def 默认值
     * @return key对应值
     */
    @JvmOverloads
    fun getStringSetOrDefault(key: String, def: Set<String?>?, account: Int = UserConfig.selectedAccount): Set<String>? = runCatching {
        getPreferences(account).getStringSet(key, def)
    }.getOrDefault(setOf())

    /**
     * 设置Int值
     *
     * @param key   key
     * @param value 值
     */
    @JvmStatic
    @JvmOverloads
    fun putInt(key: String, value: Int, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                prefs.edit().putInt(key, value).apply()
            } catch (thr: Throwable) {
                Log.e("putInt: ", thr)
            }
        }
    }

    /**
     * 设置Long值
     *
     * @param key   key
     * @param value 值
     */
    @JvmStatic
    @JvmOverloads
    fun putLong(key: String, value: Long, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                prefs.edit().putLong(key, value).apply()
            } catch (thr: Throwable) {
                Log.e("putLong: ", thr)
            }
        }
    }

    /**
     * 设置boolean值
     *
     * @param key   key
     * @param value 值
     */
    @JvmStatic
    @JvmOverloads
    fun putBoolean(key: String, value: Boolean, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                prefs.edit().putBoolean(key, value).apply()
            } catch (thr: Throwable) {
                Log.e("putBoolean: ", thr)
            }
        }
    }

    /**
     * 设置String值
     *
     * @param key   key
     * @param value 值
     */
    @JvmStatic
    @JvmOverloads
    fun putString(key: String, value: String, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                if (value == "") {
                    prefs.edit().remove(key).apply()
                }
                prefs.edit().putString(key, value).apply()
            } catch (thr: Throwable) {
                Log.e("putString: ", thr)
            }
        }
    }

    /**
     * 设置Float值
     *
     * @param key   key
     * @param value 值
     */
    @JvmStatic
    @JvmOverloads
    fun putFloat(key: String, value: Float, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                prefs.edit().putFloat(key, value).apply()
            } catch (thr: Throwable) {
                Log.e("putFloat: ", thr)
            }
        }
    }

    /**
     * 设置一个 StringSet
     *
     * @param key   key
     * @param value 值
     */
    @JvmOverloads
    fun putStringSet(key: String, value: Set<String?>, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                prefs.edit().putStringSet(key, value).apply()
            } catch (thr: Throwable) {
                Log.e("putStringSet: ", thr)
            }
        }
    }

    /**
     * 切换boolean值 若原为false或未设置则切换为true 若原为true则切换为false
     *
     * @param key key
     *
     * @deprecated 使用Config.toggleXXX代替
     */
    @JvmStatic
    @JvmOverloads
    @Deprecated("使用Config.toggleXXX代替")
    fun toggleBoolean(key: String, account: Int = UserConfig.selectedAccount) {
        val prefs = getPreferences(account)
        synchronized(prefs) {
            try {
                val originValue = prefs.getBoolean(key, false)
                prefs.edit().putBoolean(key, !originValue).apply()
            } catch (thr: Throwable) {
                Log.e(thr)
            }
        }
    }

    /**
     * 删除key所对应Value 无视value类型
     *
     * @param key key
     */
    @JvmStatic
    @JvmOverloads
    fun deleteValue(key: String, account: Int = UserConfig.selectedAccount) = synchronized(getPreferences(account)) {
        try {
            getPreferences(account).edit().remove(key).apply()
        } catch (thr: Throwable) {
            Log.e(thr)
        }
    }

    /**
     * 导出配置
     *
     * @return json格式的配置
     */
    @JvmStatic
    @Throws(JSONException::class)
    fun exportConfigurationToJson(): String {
        val json = JSONObject()
        val userconfig = ArrayList<String>().apply {
            add("saveIncomingPhotos")
            add("passcodeHash")
            add("passcodeType")
            add("passcodeHash")
            add("autoLockIn")
            add("useFingerprint")
        }

        SharedPreferenceToJSON("userconfing", json) { o: String -> userconfig.contains(o) }
        val mainconfig = ArrayList<String>().apply {
            add("saveToGallery")
            add("autoplayGifs")
            add("autoplayVideo")
            add("mapPreviewType")
            add("raiseToSpeak")
            add("customTabs")
            add("directShare")
            add("shuffleMusic")
            add("playOrderReversed")
            add("inappCamera")
            add("repeatMode")
            add("fontSize")
            add("bubbleRadius")
            add("ivFontSize")
            add("allowBigEmoji")
            add("streamMedia")
            add("saveStreamMedia")
            add("smoothKeyboard")
            add("pauseMusicOnRecord")
            add("streamAllVideo")
            add("streamMkv")
            add("suggestStickers")
            add("sortContactsByName")
            add("sortFilesByName")
            add("noSoundHintShowed")
            add("directShareHash")
            add("useThreeLinesLayout")
            add("archiveHidden")
            add("distanceSystemType")
            add("loopStickers")
            add("keepMedia")
            add("noStatusBar")
            add("lastKeepMediaCheckTime")
            add("searchMessagesAsListHintShows")
            add("searchMessagesAsListUsed")
            add("stickersReorderingHintUsed")
            add("textSelectionHintShows")
            add("scheduledOrNoSoundHintShows")
            add("lockRecordAudioVideoHint")
            add("disableVoiceAudioEffects")
            add("chatSwipeAction")
            add("theme")
            add("selectedAutoNightType")
            add("autoNightScheduleByLocation")
            add("autoNightBrighnessThreshold")
            add("autoNightDayStartTime")
            add("autoNightDayEndTime")
            add("autoNightSunriseTime")
            add("autoNightCityName")
            add("autoNightSunsetTime")
            add("autoNightLocationLatitude3")
            add("autoNightLocationLongitude3")
            add("autoNightLastSunCheckDay")
            add("lang_code")
        }

        SharedPreferenceToJSON("mainconfig", json) { o: String -> mainconfig.contains(o) }
        SharedPreferenceToJSON("themeconfig", json, null)
        SharedPreferenceToJSON("globalConfig", json, null)
        return json.toString()
    }

    /**
     * 将SharePreference的数据转换成json
     *
     * @param sp     SharePreferences name
     * @param obj 传入的JsonObject将会被传入SharePreferences中的配置
     * @param filter 过滤 只接收哪些key
     * @throws JSONException Ignore 一般不会发生
     */
    @Throws(JSONException::class)
    private fun SharedPreferenceToJSON(
        sp: String, obj: JSONObject,
        filter: Function<String, Boolean>?
    ) {
        val preferences = ApplicationLoader.applicationContext.getSharedPreferences(
            sp, Activity.MODE_PRIVATE
        )
        val jsonConfig = JSONObject()
        for (entry in preferences.all.entries) {
            var key = entry.key
            if (filter != null && !filter.apply(key)) {
                continue
            }
            if (entry.value is Long) {
                key += "_long"
            } else if (entry.value is Float) {
                key += "_float"
            } else if (entry.value is Set<*>) {
                continue
            }
            jsonConfig.put(key, entry.value)
        }
        obj.put(sp, jsonConfig)
    }

    /**
     * 导入配置
     *
     * @param configJson 待导入配置 格式为Json
     * @throws JSONException 若传入配置不为json或者json不合法就抛出这个错误
     */
    @JvmStatic
    @SuppressLint("ApplySharedPref")
    @Throws(JSONException::class)
    fun importSettings(configJson: JsonObject) {
        for ((key1, value1) in configJson.entrySet()) {
            val preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                key1, Activity.MODE_PRIVATE
            )
            val editor = preferences.edit()
            for (config in (value1 as JsonObject).entrySet()) {
                var key = config.key
                val value = config.value as JsonPrimitive
                if (value.isBoolean) {
                    editor.putBoolean(key, value.asBoolean)
                } else if (value.isNumber) {
                    var isLong = false
                    var isFloat = false
                    if (key.endsWith("_long")) {
                        key = key.substringBeforeLast("_long", key)
                        isLong = true
                    } else if (key.endsWith("_float")) {
                        key = key.substringBeforeLast("_float", key)
                        isFloat = true
                    }
                    if (isLong) {
                        editor.putLong(key, value.asLong)
                    } else if (isFloat) {
                        editor.putFloat(key, value.asFloat)
                    } else {
                        editor.putInt(key, value.asInt)
                    }
                } else {
                    editor.putString(key, value.asString)
                }
            }
            editor.commit()
        }
    }
}
