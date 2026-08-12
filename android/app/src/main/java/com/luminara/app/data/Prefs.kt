package com.luminara.app.data

import android.content.Context

/**
 * Local, offline-first preferences.
 *
 * The student's language is a client-side choice, so it lives on the device
 * rather than behind a network call — the app must know it before the first
 * request, and must still know it if the backend is unreachable.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("luminara.prefs", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = sp.edit().putString(KEY_LANGUAGE, value).apply()

    /** True once the student has completed the welcome flow. */
    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var baseUrl: String
        get() = sp.getString(KEY_BASE_URL, LuminaraApi.DEFAULT_BASE_URL)
            ?: LuminaraApi.DEFAULT_BASE_URL
        set(value) = sp.edit().putString(KEY_BASE_URL, value).apply()

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_BASE_URL = "base_url"
        const val DEFAULT_LANGUAGE = "en"
    }
}

/** Languages offered at onboarding, with the name written in the language itself. */
data class LanguageOption(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val sample: String,
    val verified: Boolean,
)

val LANGUAGE_OPTIONS = listOf(
    LanguageOption("en", "English", "English", "Understand the lecture.", true),
    LanguageOption("hi", "Hindi", "हिन्दी", "व्याख्यान को समझें।", true),
    LanguageOption("bn", "Bangla", "বাংলা", "বক্তৃতা বুঝুন।", false),
    LanguageOption("ar", "Arabic", "العربية", "افهم المحاضرة.", false),
)

fun languageOption(code: String): LanguageOption =
    LANGUAGE_OPTIONS.firstOrNull { it.code == code } ?: LANGUAGE_OPTIONS.first()
