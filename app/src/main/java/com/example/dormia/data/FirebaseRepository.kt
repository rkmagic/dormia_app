package com.example.dormia.data

import com.google.firebase.Timestamp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

data class PlayerProfile(
    val sleepTargetHours: Int = 7,
    val currentStreak: Int = 0,
    val isAsleep: Boolean = false,
    val continent: String = "",
    val country: String = "",
    val city: String = "",
    val lastSleepStartMs: Long? = null,
    val lastSleepEndMs: Long? = null
)

class FirebaseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    suspend fun ensurePlayerProfile(
        uid: String,
        displayName: String,
        email: String
    ): Boolean {
        val playerRef = firestore.collection("players").document(uid)
        val snapshot = playerRef.get().await()
        if (snapshot.exists()) return false

        playerRef.set(
            mapOf(
                "uid" to uid,
                "displayName" to displayName,
                "email" to email,
                "sleepTargetHours" to 7,
                "xp" to 0,
                "credits" to 0,
                "currentStreak" to 0,
                "longestStreak" to 0,
                "isAsleep" to false,
                "continent" to "",
                "hasAndroidApk" to false,
                "country" to "",
                "city" to "",
                "lastSleepStart" to null,
                "lastSleepEnd" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()

        rtdb.reference.child("dormia/players").child(uid).setValue(
            mapOf(
                "isAsleep" to false,
                "displayName" to displayName,
                "continent" to "",
                "country" to "",
                "city" to "",
                "lat" to 0.0,
                "lng" to 0.0
            )
        ).await()

        return true
    }

    suspend fun updateSleepState(
        uid: String,
        isAsleep: Boolean,
        updateStart: Boolean = false,
        updateEnd: Boolean = false
    ) {
        val updates = mutableMapOf<String, Any>(
            "isAsleep" to isAsleep
        )
        if (updateStart) updates["lastSleepStart"] = FieldValue.serverTimestamp()
        if (updateEnd) updates["lastSleepEnd"] = FieldValue.serverTimestamp()

        firestore.collection("players").document(uid).update(updates).await()
        rtdb.reference.child("dormia/players").child(uid).updateChildren(
            mapOf("isAsleep" to isAsleep)
        ).await()
    }

    suspend fun updateSegmentWindow(uid: String, startMs: Long?, endMs: Long?) {
        val updates = mutableMapOf<String, Any>()
        startMs?.let { updates["lastSleepStart"] = Timestamp(it / 1000, 0) }
        endMs?.let { updates["lastSleepEnd"] = Timestamp(it / 1000, 0) }
        if (updates.isNotEmpty()) {
            firestore.collection("players").document(uid).update(updates).await()
        }
    }

    suspend fun markAndroidApkPresent(uid: String) {
        firestore.collection("players").document(uid).update(
            mapOf("hasAndroidApk" to true)
        ).await()
    }

    suspend fun updateLocation(
        uid: String,
        continent: String,
        country: String,
        city: String,
        latitude: Double,
        longitude: Double
    ) {
        firestore.collection("players").document(uid).update(
            mapOf(
                "continent" to continent,
                "country" to country,
                "city" to city,
                "lat" to latitude,
                "lng" to longitude
            )
        ).await()
        rtdb.reference.child("dormia/players").child(uid).updateChildren(
            mapOf(
                "continent" to continent,
                "country" to country,
                "city" to city,
                "lat" to latitude,
                "lng" to longitude
            )
        ).await()
    }

    fun observePlayer(uid: String, onChange: (PlayerProfile) -> Unit): ListenerRegistration {
        return firestore.collection("players").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                onChange(
                    PlayerProfile(
                        sleepTargetHours = (snapshot.getLong("sleepTargetHours") ?: 7L).toInt(),
                        currentStreak = (snapshot.getLong("currentStreak") ?: 0L).toInt(),
                        isAsleep = snapshot.getBoolean("isAsleep") == true,
                        continent = snapshot.getString("continent").orEmpty(),
                        country = snapshot.getString("country").orEmpty(),
                        city = snapshot.getString("city").orEmpty(),
                        lastSleepStartMs = snapshot.getTimestamp("lastSleepStart")?.toDate()?.time,
                        lastSleepEndMs = snapshot.getTimestamp("lastSleepEnd")?.toDate()?.time
                    )
                )
            }
    }
}
