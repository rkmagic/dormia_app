package com.example.dormia.sleep

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.dormia.data.SleepDiagnosticsStore
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest

object SleepSubscriptionManager {
    fun subscribe(context: Context) {
        val pendingIntent = pendingIntent(context)
        val request = SleepSegmentRequest.getDefaultSleepSegmentRequest()
        ActivityRecognition.getClient(context)
            .requestSleepSegmentUpdates(pendingIntent, request)
            .addOnSuccessListener {
                SleepDiagnosticsStore.setSubscriptionActive(context, true)
                SleepDiagnosticsStore.clearError(context)
            }
            .addOnFailureListener { error ->
                SleepDiagnosticsStore.setSubscriptionActive(context, false)
                SleepDiagnosticsStore.setLastError(
                    context,
                    error.message ?: "Sleep API subscription failed."
                )
            }
    }

    fun unsubscribe(context: Context) {
        ActivityRecognition.getClient(context)
            .removeSleepSegmentUpdates(pendingIntent(context))
            .addOnSuccessListener {
                SleepDiagnosticsStore.setSubscriptionActive(context, false)
            }
            .addOnFailureListener { error ->
                SleepDiagnosticsStore.setLastError(
                    context,
                    error.message ?: "Sleep API unsubscribe failed."
                )
            }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SleepReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
