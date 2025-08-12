package com.example.noiceapp.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class LocationProvider(context: Context) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentOrNull(): LocationSnapshot? {
        return try {
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
            val loc = client.getCurrentLocation(req, null).await() ?: return null
            LocationSnapshot(lat = loc.latitude, lon = loc.longitude, accuracy = loc.accuracy)
        } catch (_: Throwable) {
            null
        }
    }
}

data class LocationSnapshot(val lat: Double, val lon: Double, val accuracy: Float)





