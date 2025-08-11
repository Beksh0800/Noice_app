package com.example.noiceapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.noiceapp.R
import com.example.noiceapp.audio.AWeightingFilter44100
import com.example.noiceapp.audio.LeqCalculator
import com.example.noiceapp.data.NoiseDatabase
import com.example.noiceapp.data.NoiseMeasurement
import com.example.noiceapp.location.LocationProvider
import com.example.noiceapp.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class NoiseForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize by lazy {
        kotlin.math.max(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            4096
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // подписка на калибровку offset из DataStore
        val settings = SettingsStore(applicationContext)
        scope.launch(Dispatchers.IO) {
            settings.offsetDbFlow.collectLatest { current ->
                currentOffsetDb = current
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive == true) return START_STICKY
        // Проверка RECORD_AUDIO до старта FGS с типом microphone (Android 14+ требование)
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Теперь можно поднимать foreground с уведомлением
        startForeground(NOTIF_ID, buildNotification("Идёт запись шума"))
        job = scope.launch { recordLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun recordLoop() {
        val db = NoiseDatabase.get(applicationContext)
        val dao = db.noiseDao()
        val locationProvider = LocationProvider(applicationContext)
        val aFilter = AWeightingFilter44100()

        // Проверка разрешения RECORD_AUDIO перед созданием/запуском AudioRecord
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            stopSelf()
            return
        }

        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (_: SecurityException) {
            stopSelf(); return
        } catch (_: Throwable) {
            stopSelf(); return
        }
        val shortBuf = ShortArray(bufferSize)
        val aBuf = DoubleArray(bufferSize)
        try {
            audio.startRecording()
        } catch (_: Throwable) {
            stopSelf()
            return
        }

        while (scope.isActive) {
            val read = audio.read(shortBuf, 0, shortBuf.size)
            if (read > 0) {
                aFilter.process(shortBuf, read, aBuf)
                val result = LeqCalculator.computeAWeightedLeqAndMax(aBuf, read, reference = 32768.0, offsetDb = currentOffsetDb)
                val ts = System.currentTimeMillis()
                val loc = locationProvider.getCurrentOrNull()
                val entity = NoiseMeasurement(
                    timestampMillis = ts,
                    laeqDb = result.laeqDb,
                    lamaxDb = result.lamaxDb,
                    latitude = loc?.lat,
                    longitude = loc?.lon,
                    locationAccuracyMeters = loc?.accuracy
                )
                try {
                    dao.insert(entity)
                } catch (_: Throwable) {
                    // ignore write errors for now
                }
            }
        }

        try {
            audio.stop()
        } catch (_: Throwable) {}
        audio.release()
    }

    private fun buildNotification(text: String): Notification {
        val channelId = NOTIF_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Шумомер", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_CHANNEL_ID = "noise_rec_channel"
        private const val NOTIF_ID = 1001
        @Volatile private var currentOffsetDb: Double = 90.0
    }
}


