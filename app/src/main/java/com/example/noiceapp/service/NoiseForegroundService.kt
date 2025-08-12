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
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.noiceapp.R
import com.example.noiceapp.audio.AWeightingFilter44100
import com.example.noiceapp.audio.LeqCalculator
import com.example.noiceapp.audio.TimeWeighter
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
                // Offset хранится как абсолютный целевой LAeq при -0 dBFS ~ reference
                // Здесь используем как сдвиг, типичный диапазон 70..110 дБ
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
        val aFilter = AWeightingFilter44100().apply { reset() }
        val weighterFast = TimeWeighter(sampleRate = sampleRate, tauSeconds = 0.125).apply { reset() }

        // Проверка разрешения RECORD_AUDIO перед созданием/запуском AudioRecord
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            stopSelf()
            return
        }

        fun createRecorder(): AudioRecord? {
            val sources = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) add(MediaRecorder.AudioSource.UNPROCESSED)
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                add(MediaRecorder.AudioSource.MIC)
            }
            for (src in sources) {
                try {
                    val ar = AudioRecord(src, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (ar.state == AudioRecord.STATE_INITIALIZED) return ar else ar.release()
                } catch (_: Throwable) { /* try next */ }
            }
            return null
        }
        var audio = createRecorder() ?: run { stopSelf(); return }
        // Попробовать отключить обработчики (AGC/NS/AEC) для большей точности SPL
        try {
            val sid = audio.audioSessionId
            // Явно выключаем эффекты обработки речи
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sid)?.setEnabled(false)
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sid)?.setEnabled(false)
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sid)?.setEnabled(false)
        } catch (_: Throwable) { }
        val shortBuf = ShortArray(bufferSize)
        val aBuf = DoubleArray(bufferSize)
        try {
            audio.startRecording()
        } catch (_: Throwable) {
            stopSelf()
            return
        }

        var currentSecond = System.currentTimeMillis() / 1000L
        var sumSquaresSec = 0.0
        var countSec = 0
        var lafMaxSec = Double.NEGATIVE_INFINITY
        var lastActiveTs = System.currentTimeMillis()

        while (scope.isActive) {
            val read = audio.read(shortBuf, 0, shortBuf.size)
            if (read <= 0) {
                // если долго нет данных — перезапускаем рекордер
                if (System.currentTimeMillis() - lastActiveTs > 3000) {
                    try { audio.stop() } catch (_: Throwable) {}
                    audio.release()
                    val restarted = createRecorder()
                    if (restarted == null) { stopSelf(); return }
                    try { restarted.startRecording() } catch (_: Throwable) { stopSelf(); return }
                    audio = restarted
                    // reset state
                    weighterFast.reset(); aFilter.reset();
                    sumSquaresSec = 0.0; countSec = 0; lafMaxSec = Double.NEGATIVE_INFINITY
                    lastActiveTs = System.currentTimeMillis()
                }
                continue
            }

            // Обработка блока
            aFilter.process(shortBuf, read, aBuf, normalizeToUnit = true)

            var blockNonZero = false
            for (i in 0 until read) {
                val s = aBuf[i]
                if (!blockNonZero && kotlin.math.abs(s) > 1e-8) blockNonZero = true
                sumSquaresSec += s * s
                countSec++
                val rmsFast = weighterFast.processSample(s)
                val dbFast = 20.0 * kotlin.math.log10((rmsFast).coerceAtLeast(1e-9)) + currentOffsetDb
                if (dbFast > lafMaxSec) lafMaxSec = dbFast
            }
            if (blockNonZero) lastActiveTs = System.currentTimeMillis()

            // Завершение секунды и запись 1 точки/сек
            val nowSec = System.currentTimeMillis() / 1000L
            if (nowSec != currentSecond && countSec > 0) {
                val rms = kotlin.math.sqrt(sumSquaresSec / countSec)
                val laeqDb = 20.0 * kotlin.math.log10(rms.coerceAtLeast(1e-9)) + currentOffsetDb
                val loc = locationProvider.getCurrentOrNull()
                val entity = NoiseMeasurement(
                    timestampMillis = currentSecond * 1000L,
                    laeqDb = laeqDb,
                    lamaxDb = if (lafMaxSec.isFinite()) lafMaxSec else null,
                    latitude = loc?.lat,
                    longitude = loc?.lon,
                    locationAccuracyMeters = loc?.accuracy
                )
                try { dao.insert(entity) } catch (_: Throwable) { }

                // reset для новой секунды
                currentSecond = nowSec
                sumSquaresSec = 0.0
                countSec = 0
                lafMaxSec = Double.NEGATIVE_INFINITY
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


