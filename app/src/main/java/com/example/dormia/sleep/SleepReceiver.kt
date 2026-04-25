package com.example.dormia.sleep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.dormia.data.FirebaseRepository
import com.example.dormia.data.SessionStore
import com.example.dormia.data.SleepDiagnosticsStore
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SleepReceiver : BroadcastReceiver() {
    private val repository = FirebaseRepository()

    override fun onReceive(context: Context, intent: Intent) {
        val uid = SessionStore.getUid(context) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SleepClassifyEvent.hasEvents(intent)) {
                    handleClassify(context, uid, intent)
                }
                if (SleepSegmentEvent.hasEvents(intent)) {
                    handleSegment(context, uid, intent)
                }
                SleepDiagnosticsStore.clearError(context)
                SleepDiagnosticsStore.setSubscriptionActive(context, true)
                SleepDiagnosticsStore.setServiceRunning(context, true)
            } catch (e: Exception) {
                SleepDiagnosticsStore.setLastError(context, e.message ?: "Sleep receiver error")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleClassify(context: Context, uid: String, intent: Intent) {
        val events = SleepClassifyEvent.extractEvents(intent)
        if (events.isNullOrEmpty()) return
        val confidence = events.maxOf { it.confidence }
        Log.d(TAG, "SleepClassifyEvent received. confidence=$confidence count=${events.size}")
        showToast(context, "Sleep classify: confidence $confidence")
        SleepDiagnosticsStore.setLastEvent(context, "classify", confidence)
        when {
            confidence >= ASLEEP_THRESHOLD -> {
                repository.updateSleepState(uid, isAsleep = true, updateStart = true)
            }

            confidence <= AWAKE_THRESHOLD -> {
                repository.updateSleepState(uid, isAsleep = false, updateEnd = true)
            }
        }
    }

    private suspend fun handleSegment(context: Context, uid: String, intent: Intent) {
        val events = SleepSegmentEvent.extractEvents(intent)
        if (events.isNullOrEmpty()) return
        val latest = events.maxByOrNull { it.endTimeMillis } ?: return
        Log.d(
            TAG,
            "SleepSegmentEvent received. start=${latest.startTimeMillis} end=${latest.endTimeMillis} count=${events.size}"
        )
        showToast(context, "Sleep segment event received")
        SleepDiagnosticsStore.setLastEvent(context, "segment")
        repository.updateSegmentWindow(uid, latest.startTimeMillis, latest.endTimeMillis)
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "DormiaSleepReceiver"
        private const val ASLEEP_THRESHOLD = 70
        private const val AWAKE_THRESHOLD = 30
    }
}
