package com.benekapps.myslnik.domain

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.benekapps.myslnik.R
import java.util.Locale

/** App languages. SYSTEM follows the phone (Polish when the phone uses neither). */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null), POLISH("pl"), UKRAINIAN("uk");

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return entries.firstOrNull { it.tag != null && tag.startsWith(it.tag) } ?: SYSTEM
        }

        /** AppCompat stores the choice and recreates the activity on every Android version. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList(),
            )
        }
    }
}

/**
 * The language the app's texts are actually shown in. On a phone in English
 * the texts fall back to Polish, so dates must be Polish too, not the
 * system locale's.
 */
fun Resources.appLocale(): Locale = Locale.forLanguageTag(getString(R.string.content_locale))

/** Set by MainActivity on every (re)creation, used by the date formatters. */
object AppLocale {
    @Volatile
    var current: Locale = Locale.forLanguageTag("pl-PL")
}
