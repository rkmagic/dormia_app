package com.example.dormia.data

import android.content.Context

object SessionStore {
    private const val PREFS_NAME = "dormia_prefs"
    private const val KEY_UID = "uid"

    fun setUid(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UID, uid)
            .apply()
    }

    fun getUid(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UID, null)
    }
}
