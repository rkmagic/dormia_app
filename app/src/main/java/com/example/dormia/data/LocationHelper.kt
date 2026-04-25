package com.example.dormia.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

data class LocationDetails(
    val continent: String,
    val country: String,
    val city: String
)

data class DetectedLocation(
    val latitude: Double,
    val longitude: Double,
    val details: LocationDetails
)

class LocationHelper(private val context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        return try {
            val lastKnown = fusedClient.lastLocation.await()
            if (lastKnown != null) return lastKnown
            val tokenSource = CancellationTokenSource()
            val current = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tokenSource.token
            ).await()
            current
        } catch (_: Exception) {
            null
        }
    }

    fun getContinent(latitude: Double, longitude: Double): String {
        val continent = when {
            latitude in 15.0..72.0 && longitude in -170.0..-50.0 -> "North America"
            latitude in -56.0..13.0 && longitude in -82.0..-35.0 -> "South America"
            latitude in 35.0..71.0 && longitude in -10.0..40.0 -> "Europe"
            latitude in -35.0..37.0 && longitude in -18.0..52.0 -> "Africa"
            latitude in 1.0..80.0 && longitude in 40.0..180.0 -> "Asia"
            latitude in -50.0..0.0 && longitude in 110.0..180.0 -> "Oceania"
            else -> "Unknown"
        }
        return continent
    }

    @Suppress("DEPRECATION")
    suspend fun getLocationDetails(latitude: Double, longitude: Double): LocationDetails = withContext(Dispatchers.IO) {
        val continent = getContinent(latitude, longitude)
        val geocoder = Geocoder(context, Locale.getDefault())
        return@withContext try {
            val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            val fallbackCountry = Locale.getDefault().displayCountry.takeIf { it.isNotBlank() } ?: "Unknown"
            val country = address?.countryName?.takeIf { it.isNotBlank() } ?: fallbackCountry
            val city = address?.locality?.takeIf { it.isNotBlank() }
                ?: address?.subAdminArea?.takeIf { it.isNotBlank() }
                ?: "Unknown"
            LocationDetails(continent = continent, country = country, city = city)
        } catch (_: Exception) {
            val fallbackCountry = Locale.getDefault().displayCountry.takeIf { it.isNotBlank() } ?: "Unknown"
            LocationDetails(continent = continent, country = fallbackCountry, city = "Unknown")
        }
    }

    suspend fun detectBestLocation(): DetectedLocation? {
        val gpsLocation = getLastKnownLocation()
        if (gpsLocation != null) {
            val details = getLocationDetails(gpsLocation.latitude, gpsLocation.longitude)
            if (!isCompletelyUnknown(details)) {
                return DetectedLocation(
                    latitude = gpsLocation.latitude,
                    longitude = gpsLocation.longitude,
                    details = details
                )
            }
        }
        return fetchLocationFromIp()
    }

    private fun isCompletelyUnknown(details: LocationDetails): Boolean {
        return details.continent == "Unknown" && details.country == "Unknown" && details.city == "Unknown"
    }

    private suspend fun fetchLocationFromIp(): DetectedLocation? = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = URL("https://ipapi.co/json/")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(body)
            val continent = continentFromCode(json.optString("continent_code"))
            val country = json.optString("country_name").ifBlank { "Unknown" }
            val city = json.optString("city").ifBlank { "Unknown" }
            val latitude = if (json.has("latitude")) json.optDouble("latitude", 0.0) else json.optDouble("lat", 0.0)
            val longitude = if (json.has("longitude")) json.optDouble("longitude", 0.0) else json.optDouble("lon", 0.0)
            if (latitude == 0.0 && longitude == 0.0 && country == "Unknown") return@withContext null
            DetectedLocation(
                latitude = latitude,
                longitude = longitude,
                details = LocationDetails(continent = continent, country = country, city = city)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun continentFromCode(code: String): String {
        return when (code.uppercase(Locale.US)) {
            "AF" -> "Africa"
            "AN" -> "Antarctica"
            "AS" -> "Asia"
            "EU" -> "Europe"
            "NA" -> "North America"
            "OC" -> "Oceania"
            "SA" -> "South America"
            else -> "Unknown"
        }
    }
}
