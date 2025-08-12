package com.example.noiceapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoiseMeasurement::class, CitySample::class, NoiseAnalysis::class],
    version = 4,
    exportSchema = false
)
abstract class NoiseDatabase : RoomDatabase() {
    abstract fun noiseDao(): NoiseDao
    abstract fun citySampleDao(): CitySampleDao
    abstract fun analysisDao(): NoiseAnalysisDao

    companion object {
        @Volatile private var instance: NoiseDatabase? = null

        fun get(context: Context): NoiseDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NoiseDatabase::class.java,
                "noise_db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}


