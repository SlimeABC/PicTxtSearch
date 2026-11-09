package com.slime.pictxtsearch.data

import android.content.Context

/**
 * 语言设置的同步镜像。
 *
 * DataStore 仍是唯一真相，这里只用 SharedPreferences 保存一份副本，供
 * [android.app.Activity.attachBaseContext] 在启动时同步读取（DataStore 只能异步读）。
 */
object AppLanguageCache {
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE = "language"

    fun read(context: Context): AppLanguage {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return name?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: AppLanguage.FOLLOW_SYSTEM
    }

    fun write(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}
