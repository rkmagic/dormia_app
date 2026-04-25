package com.example.dormia.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.dormia.data.SessionStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (SessionStore.getUid(context).isNullOrEmpty()) return
        val serviceIntent = Intent(context, SleepService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
