package com.slime.pictxtsearch.util

import android.content.Context
import android.content.res.Configuration
import com.slime.pictxtsearch.data.AppLanguage
import java.util.Locale

/** 语言设置对应的 [Locale]；返回 null 表示跟随系统。 */
fun AppLanguage.toLocale(): Locale? = when (this) {
    AppLanguage.FOLLOW_SYSTEM -> null
    AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
    AppLanguage.ENGLISH -> Locale.ENGLISH
}

/** 返回按语言设置切换了 [Locale] 的 Context（跟随系统时原样返回）。 */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = language.toLocale() ?: return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
