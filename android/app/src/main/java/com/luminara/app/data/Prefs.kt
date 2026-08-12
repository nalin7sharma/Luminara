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
        get() {
            val stored = sp.getString(KEY_BASE_URL, null) ?: return LuminaraApi.DEFAULT_BASE_URL
            // A release build must never inherit a development address — for
            // instance from an earlier debug install on the same device.
            if (!LuminaraApi.isDebugBuild && LOCAL_HOSTS.any { stored.contains(it) }) {
                return LuminaraApi.DEFAULT_BASE_URL
            }
            return stored
        }
        set(value) = sp.edit().putString(KEY_BASE_URL, value).apply()

    /** How the student described themselves at onboarding, before any account. */
    var role: String
        get() = sp.getString(KEY_ROLE, "student") ?: "student"
        set(value) = sp.edit().putString(KEY_ROLE, value).apply()

    var name: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    /** Bearer token. Cleared on sign out; absent means guest, which is allowed. */
    var token: String?
        get() = sp.getString(KEY_TOKEN, null)
        set(value) = sp.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    fun clearAccount() {
        sp.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ROLE = "role"
        private const val KEY_NAME = "name"
        private const val KEY_TOKEN = "auth_token"
        const val DEFAULT_LANGUAGE = "en"
        private val LOCAL_HOSTS = listOf(
            "10.0.2.2", "127.0.0.1", "localhost", "192.168.", "172.16.", "172.18.",
        )
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
