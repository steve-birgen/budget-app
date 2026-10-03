package com.yourapp.budgetapp

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("budget_app_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_CURRENCY = "currency"
        const val KEY_LANGUAGE = "language"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_BACKUP_ENABLED = "backup_enabled"
        const val KEY_BUDGET_DEFAULT = "budget_default"
        const val KEY_DATE_FORMAT = "date_format"
        const val KEY_FIRST_DAY_WEEK = "first_day_week"
        const val KEY_PIN_ENABLED = "pin_enabled"
        const val KEY_PIN_CODE = "pin_code"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_HIDE_BALANCES = "hide_balances"
        const val KEY_PRIVACY_MODE = "privacy_mode"
    }

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var currency: String
        get() = prefs.getString(KEY_CURRENCY, "KSh") ?: "KSh"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var backupEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKUP_ENABLED, value).apply()

    var budgetDefault: Double
        get() = prefs.getFloat(KEY_BUDGET_DEFAULT, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_BUDGET_DEFAULT, value.toFloat()).apply()

    var dateFormat: String
        get() = prefs.getString(KEY_DATE_FORMAT, "MMM d, yyyy") ?: "MMM d, yyyy"
        set(value) = prefs.edit().putString(KEY_DATE_FORMAT, value).apply()

    var firstDayOfWeek: Int
        get() = prefs.getInt(KEY_FIRST_DAY_WEEK, 1) // 1 = Sunday
        set(value) = prefs.edit().putInt(KEY_FIRST_DAY_WEEK, value).apply()

    var pinEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PIN_ENABLED, value).apply()

    var pinCode: String
        get() = prefs.getString(KEY_PIN_CODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN_CODE, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var hideBalances: Boolean
        get() = prefs.getBoolean(KEY_HIDE_BALANCES, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_BALANCES, value).apply()

    var privacyMode: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_MODE, value).apply()
}
