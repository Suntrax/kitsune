package com.blissless.manga.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "kitsune_settings"
        private const val KEY_SYNC_THRESHOLD = "anilist_sync_threshold"
        private const val DEFAULT_SYNC_THRESHOLD = 90
    }

    fun getAniListSyncThreshold(): Int {
        return prefs.getInt(KEY_SYNC_THRESHOLD, DEFAULT_SYNC_THRESHOLD)
    }

    fun setAniListSyncThreshold(percent: Int) {
        prefs.edit().putInt(KEY_SYNC_THRESHOLD, percent.coerceIn(75, 100)).apply()
    }
}
