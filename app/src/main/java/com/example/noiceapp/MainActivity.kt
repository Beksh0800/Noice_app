@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.noiceapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.noiceapp.ui.theme.NoiceAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import com.example.noiceapp.data.NoiseDatabase
import com.example.noiceapp.data.NoiseRepository
import com.example.noiceapp.data.CitySample
import com.example.noiceapp.data.CitySamplesRepository
import kotlinx.coroutines.flow.collectLatest
import com.example.noiceapp.service.NoiseForegroundService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Paint as NPaint
import android.graphics.Color as NColor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

data class NoisePoint(val timeMillis: Long, val db: Double)

class NoiseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoiseRepository = NoiseRepository(NoiseDatabase.get(application).noiseDao())
    private val samplesRepository: CitySamplesRepository = CitySamplesRepository(NoiseDatabase.get(application).citySampleDao())
    private val settingsStore = com.example.noiceapp.settings.SettingsStore(application)
    private var observeJob: Job? = null

    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> get() = _isRecording

    private val _currentDb = mutableDoubleStateOf(0.0)
    val currentDb: State<Double> get() = _currentDb

    // Последнее значение dBFS (без offset), для быстрой калибровки
    private val _lastDbFs = mutableDoubleStateOf(-120.0)

    private val _history = mutableStateListOf<NoisePoint>()
    val history: List<NoisePoint> get() = _history

    // Метрики для "анализа": обновляются каждую секунду
    data class Metrics(
        val min1: Double, val avg1: Double, val max1: Double,
        val min5: Double, val avg5: Double, val max5: Double,
        val p55_1: Double, val p65_1: Double, val p75_1: Double,
        val p55_5: Double, val p65_5: Double, val p75_5: Double
    )
    private val _metrics = mutableStateOf<Metrics?>(null)
    val metrics: State<Metrics?> get() = _metrics

    // Экран 2: городские выборки
    private val _citySamples = mutableStateListOf<CitySample>()
    val citySamples: List<CitySample> get() = _citySamples

    // Настройки выгрузки
    private val _studentId = mutableStateOf("")
    val studentId: State<String> get() = _studentId
    fun setStudentId(value: String) { _studentId.value = value; viewModelScope.launch(Dispatchers.IO) { settingsStore.setStudentId(value) } }

    private val _uploadUrl = mutableStateOf("")
    val uploadUrl: State<String> get() = _uploadUrl
    fun setUploadUrl(value: String) { _uploadUrl.value = value; viewModelScope.launch(Dispatchers.IO) { settingsStore.setUploadUrl(value) } }

    private val _apiKey = mutableStateOf("SCHOOL_SHARED_KEY")
    val apiKey: State<String> get() = _apiKey
    fun setApiKey(value: String) { _apiKey.value = value; viewModelScope.launch(Dispatchers.IO) { settingsStore.setApiKey(value) } }

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder().addInterceptor(logging).build()
    }

    // Калибровка: храним и сам offset (в DataStore), и целевое значение (для UI)
    private val _offsetDb = mutableDoubleStateOf(90.0)
    val offsetDb: State<Double> get() = _offsetDb
    private val _calibrationTarget = mutableDoubleStateOf(60.0)
    val calibrationTarget: State<Double> get() = _calibrationTarget
    fun setOffsetDb(value: Double) {
        _offsetDb.doubleValue = value
        viewModelScope.launch(Dispatchers.IO) { settingsStore.setOffsetDb(value) }
    }
    fun setCalibrationTarget(value: Double) {
        _calibrationTarget.doubleValue = value
        // Берём усреднение за 10 секунд; если данных мало или тишина — не дергаем offset
        val recent = analytics(10)?.second
        if (recent == null || recent < 20.0) return
        val delta = (value - recent).coerceIn(-15.0, 15.0)
        val newOffset = (_offsetDb.value + delta).coerceIn(50.0, 100.0)
        setOffsetDb(newOffset)
    }

    init {
        // Наблюдаем за последними точками из БД и обновляем историю/текущие значения
        observeJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeLast(300).collectLatest { list ->
                val points = list.sortedBy { it.timestampMillis }
                    .map { NoisePoint(it.timestampMillis, it.laeqDb) }
                _history.clear()
                _history.addAll(points)
                val last = points.lastOrNull()
                if (last != null) {
                    _currentDb.doubleValue = last.db
                }
                val a1 = analytics(60)
                val a5 = analytics(300)
                if (a1 != null && a5 != null) {
                    _metrics.value = Metrics(
                        min1 = a1.first, avg1 = a1.second, max1 = a1.third,
                        min5 = a5.first, avg5 = a5.second, max5 = a5.third,
                        p55_1 = ratioAbove(55.0, 60) ?: 0.0,
                        p65_1 = ratioAbove(65.0, 60) ?: 0.0,
                        p75_1 = ratioAbove(75.0, 60) ?: 0.0,
                        p55_5 = ratioAbove(55.0, 300) ?: 0.0,
                        p65_5 = ratioAbove(65.0, 300) ?: 0.0,
                        p75_5 = ratioAbove(75.0, 300) ?: 0.0,
                    )
                }
            }
        }
        // Подписка на городские выборки
        viewModelScope.launch(Dispatchers.IO) {
            samplesRepository.observe().collectLatest { samples ->
                _citySamples.clear(); _citySamples.addAll(samples)
            }
        }
        // Подписка на сохранённый offset из DataStore
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.offsetDbFlow.collectLatest { value ->
                _offsetDb.doubleValue = value
            }
        }
        // Подписка на настройки выгрузки
        viewModelScope.launch(Dispatchers.IO) { settingsStore.studentIdFlow.collectLatest { _studentId.value = it } }
        viewModelScope.launch(Dispatchers.IO) { settingsStore.uploadUrlFlow.collectLatest { _uploadUrl.value = it } }
        viewModelScope.launch(Dispatchers.IO) { settingsStore.apiKeyFlow.collectLatest { _apiKey.value = it } }
    }

    fun onStartRecording() { _isRecording.value = true }

    fun onStopRecording() { _isRecording.value = false }

    fun clearHistory() { _history.clear() }

    // Управление списком городских выборок (демо CRUD)
    fun addDemoSample() {
        viewModelScope.launch(Dispatchers.IO) {
            val demo = CitySample(
                timestampMillis = System.currentTimeMillis(),
                locationText = "Санкт-Петербург, Невский проспект",
                noiseType = "Транспорт",
                rating10 = (3..7).random(),
                note = "Демо запись"
            )
            samplesRepository.add(demo)
        }
    }

    fun calibrateTo(targetDb: Double) { setCalibrationTarget(targetDb) }

    fun analytics(windowSec: Int): Triple<Double, Double, Double>? {
        val now = System.currentTimeMillis()
        val from = now - windowSec * 1000L
        val slice = history.filter { it.timeMillis >= from }
        if (slice.isEmpty()) return null
        val values = slice.map { sanitizeDb(it.db) }
        val min = values.minOrNull() ?: return null
        val avg = values.average()
        val max = values.maxOrNull() ?: return null
        return Triple(min, avg, max)
    }

    fun ratioAbove(threshold: Double, windowSec: Int): Double? {
        val now = System.currentTimeMillis()
        val from = now - windowSec * 1000L
        val slice = history.filter { it.timeMillis >= from }
        if (slice.isEmpty()) return null
        val count = slice.count { sanitizeDb(it.db) >= threshold }
        return count.toDouble() / slice.size
    }

    fun classify(db: Double): String {
        val v = sanitizeDb(db)
        return when {
            v < 50 -> "тихо"
            v < 65 -> "умеренно"
            v < 80 -> "шумно"
            else -> "очень шумно"
        }
    }

    fun forecastNext(): Double? {
        val n = history.size
        if (n < 20) return null
        val window = history.takeLast(minOf(60, n)).map { it.db.coerceIn(0.0, 120.0) }
        val sma = window.average()
        // Простой тренд: разница между последним и первым, распределённая на окно
        val trend = if (window.size >= 2) (window.last() - window.first()) / window.size else 0.0
        val predicted = sma + trend * 60 // прогноз на ≈60 сек
        return predicted.coerceIn(0.0, 120.0)
    }

    // Экспорт CSV: агрегирование по минутам (среднее и максимум за минуту) + последняя доступная GPS-точка за минуту
    fun exportCsv(context: android.content.Context, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val all = repository.all()
                if (all.isEmpty()) {
                    launchSnackbar(snackbar, "Нет данных для экспорта")
                    return@launch
                }
                val csv = buildCsvStringAggregated(all, studentId.value, Build.MODEL)
                // Сохраняем напрямую в «Загрузки», чтобы файл был виден в «Файлы» и не был пустым
                val savedName = saveCsvToDownloads(context, csv)
                launch(Dispatchers.Main) {
                    if (savedName != null) launchSnackbar(snackbar, "CSV сохранён: Папка Загрузки/$savedName")
                    else launchSnackbar(snackbar, "Ошибка экспорта CSV")
                }
            } catch (_: Throwable) {
                launchSnackbar(snackbar, "Ошибка экспорта CSV")
            }
        }
    }

    

    private fun saveCsvToDownloads(context: android.content.Context, csv: String): String? {
        val filename = "noise_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    filename
                } else null
            } else {
                // Pre-Android 10: сохраняем в публичные Загрузки + просим медиа-сканер проиндексировать
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val file = File(downloads, filename)
                file.writeText(csv, Charsets.UTF_8)
                try {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("text/csv"), null)
                } catch (_: Throwable) {}
                filename
            }
        } catch (_: Throwable) {
            null
        }
    }

    // Загрузка одной записи в Apps Script
    private suspend fun uploadOne(measurement: com.example.noiceapp.data.NoiseMeasurement, uploadUrl: String, studentId: String, apiKey: String): Boolean {
        if (uploadUrl.isBlank()) return false
        val json = JSONObject().apply {
            put("student_id", studentId)
            put("timestamp", Instant.ofEpochMilli(measurement.timestampMillis).atOffset(ZoneOffset.UTC).toInstant().toString())
            put("db", measurement.laeqDb)
            if (measurement.latitude != null) put("lat", measurement.latitude) else put("lat", JSONObject.NULL)
            if (measurement.longitude != null) put("lon", measurement.longitude) else put("lon", JSONObject.NULL)
            put("device", Build.MODEL)
            put("api_key", apiKey)
        }
        val body: RequestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(uploadUrl).post(body).build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun uploadAllPending(snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = uploadUrl.value
            val sid = studentId.value
            val key = apiKey.value
            if (url.isBlank() || sid.isBlank()) {
                launchSnackbar(snackbar, "Укажите Student ID и Upload URL в настройках")
                return@launch
            }
            val list = repository.getNotUploaded()
            if (list.isEmpty()) {
                launchSnackbar(snackbar, "Нет записей для загрузки")
                return@launch
            }
            var ok = 0
            list.forEach { m ->
                val success = uploadOne(m, url, sid, key)
                if (success) {
                    repository.markUploaded(m.id)
                    ok++
                }
            }
            launchSnackbar(snackbar, "Загружено: $ok из ${list.size}")
        }
    }

    // Импорт CSV: измерения (timestamp,db[,lat,lon])
    fun importMeasurementsCsv(context: android.content.Context, uri: android.net.Uri, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = ArrayList<com.example.noiceapp.data.NoiseMeasurement>()
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { br ->
                    val header = br.readLine()?.lowercase() ?: ""
                    val cols = header.split(',').map { it.trim() }
                    val iTs = cols.indexOfFirst { it in setOf("timestamp","timestamp_utc","time","ts","minute_start_utc") }
                    val iDb = cols.indexOfFirst { it in setOf("db","laeq","laeq_db","avg_db") }
                    val iLat = cols.indexOfFirst { it == "lat" }
                    val iLon = cols.indexOfFirst { it == "lon" || it == "lng" }
                    var line: String?
                    while (true) {
                        line = br.readLine() ?: break
                        if (line!!.isBlank()) continue
                        val parts = line!!.split(',')
                        fun get(i: Int): String? = if (i >= 0 && i < parts.size) parts[i].trim().takeIf { it.isNotEmpty() } else null
                        val tsStr = get(iTs) ?: continue
                        val ts = tsStr.toLongOrNull() ?: run { try { Instant.parse(tsStr).toEpochMilli() } catch (_: Throwable) { null } } ?: continue
                        val dbStr = get(iDb) ?: continue
                        val db = dbStr.replace(',', '.').toDoubleOrNull() ?: continue
                        val lat = get(iLat)?.replace(',', '.')?.toDoubleOrNull()
                        val lon = get(iLon)?.replace(',', '.')?.toDoubleOrNull()
                        parsed.add(
                            com.example.noiceapp.data.NoiseMeasurement(
                                timestampMillis = ts,
                                laeqDb = db,
                                lamaxDb = null,
                                latitude = lat,
                                longitude = lon,
                                locationAccuracyMeters = null
                            )
                        )
                    }
                }
                if (parsed.isEmpty()) { launchSnackbar(snackbar, "CSV пуст/не распознан"); return@launch }
                val dao = NoiseDatabase.get(getApplication()).noiseDao()
                parsed.forEach { dao.insert(it) }
                launchSnackbar(snackbar, "Импортировано измерений: ${parsed.size}")
            } catch (_: Throwable) {
                launchSnackbar(snackbar, "Ошибка импорта CSV")
            }
        }
    }

    // Импорт CSV: городские выборки (timestamp,location,noiseType,rating10[,note])
    fun importCitySamplesCsv(context: android.content.Context, uri: android.net.Uri, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = ArrayList<CitySample>()
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { br ->
                    val header = br.readLine()?.lowercase() ?: ""
                    val cols = header.split(',').map { it.trim() }
                    val iTs = cols.indexOfFirst { it in setOf("timestamp","timestamp_millis","timestamp_ms","time","ts") }
                    val iLoc = cols.indexOfFirst { it in setOf("location","location_text","place") }
                    val iType = cols.indexOfFirst { it in setOf("noise_type","type","category") }
                    val iRating = cols.indexOfFirst { it in setOf("rating10","rating","score") }
                    val iNote = cols.indexOfFirst { it in setOf("note","comment") }
                    var line: String?
                    while (true) {
                        line = br.readLine() ?: break
                        if (line!!.isBlank()) continue
                        val parts = line!!.split(',')
                        fun get(i: Int): String? = if (i >= 0 && i < parts.size) parts[i].trim().takeIf { it.isNotEmpty() } else null
                        val tsStr = get(iTs) ?: continue
                        val ts = tsStr.toLongOrNull() ?: run { try { Instant.parse(tsStr).toEpochMilli() } catch (_: Throwable) { null } } ?: continue
                        val loc = get(iLoc) ?: ""
                        val type = get(iType) ?: ""
                        val rating = get(iRating)?.toIntOrNull() ?: 0
                        val note = get(iNote)
                        parsed.add(CitySample(timestampMillis = ts, locationText = loc, noiseType = type, rating10 = rating, note = note))
                    }
                }
                if (parsed.isEmpty()) { launchSnackbar(snackbar, "CSV пуст/не распознан"); return@launch }
                val dao = NoiseDatabase.get(getApplication()).citySampleDao()
                parsed.forEach { dao.insert(it) }
                launchSnackbar(snackbar, "Импортировано выборок: ${parsed.size}")
            } catch (_: Throwable) {
                launchSnackbar(snackbar, "Ошибка импорта CSV")
            }
        }
    }
}

private fun launchSnackbar(host: SnackbarHostState, message: String) {
    // Вызов из @Composable через rememberCoroutineScope() не нужен: SnackbarHostState
    // поддерживает showSnackbar через suspend. Здесь используем глобальный scope для простоты.
    kotlinx.coroutines.GlobalScope.launch {
        host.showSnackbar(message = message, withDismissAction = true, duration = SnackbarDuration.Short)
    }
}

@Composable
private fun SamplesListScreen(vm: NoiseViewModel, onOpen: (Long) -> Unit) {
    val all = vm.citySamples
    var q by remember { mutableStateOf("") }
    val filtered = remember(all, q) {
        if (q.isBlank()) all else all.filter { it.locationText.contains(q, ignoreCase = true) || it.noiseType.contains(q, true) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Деректeр", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FilledTonalButton(onClick = { vm.addDemoSample() }) { Text("+ Add") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            leadingIcon = { Icon(MaterialIcons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Поиск") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(filtered) { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).clickable { onOpen(s.id) }) {
                    Text(text = s.timestampMillis.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Text(text = s.rating10.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(text = s.locationText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DatasetScreen(vm: NoiseViewModel) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pickMeasurements = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importMeasurementsCsv(ctx, uri, snackbar)
    }
    val pickSamples = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importCitySamplesCsv(ctx, uri, snackbar)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Датасет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickMeasurements.launch("text/*") }) { Text("Импорт измерений CSV") }
            OutlinedButton(onClick = { pickSamples.launch("text/*") }) { Text("Импорт выборок CSV") }
        }
        Spacer(Modifier.height(12.dp))
        SamplesListScreen(vm = vm, onOpen = { })
    }
}

@Composable
private fun SampleDetailsScreen(vm: NoiseViewModel, id: Long) {
    val sample = remember(vm.citySamples) { vm.citySamples.find { it.id == id } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Noise Sample Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (sample == null) {
            Text("Не найдено")
        } else {
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sample Metrics", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Noise Level", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sample.rating10.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Sample Date", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sample.timestampMillis)))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Sample Details", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Location\n${sample.locationText}")
            Spacer(Modifier.height(8.dp))
            Text("Noise Type: ${sample.noiseType}")
            Spacer(Modifier.height(8.dp))
            sample.note?.let { Text(it) }
        }
    }
}

@Composable
private fun PredictionScreen(vm: NoiseViewModel) {
    val lastForecast = remember(vm.history) { vm.forecastNext() }
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Прогноз", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text = lastForecast?.let { "Прогноз на минуту: ${"%.1f".format(it)} дБ" } ?: "Недостаточно данных")
        Spacer(Modifier.height(20.dp))
        SectionCard(title = "Выгрузка данных", onInfo = {
            launchSnackbar(snackbar, "Укажите идентификатор ученика и URL веб‑приложения Apps Script.")
        }) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = vm.studentId.value,
                onValueChange = { vm.setStudentId(it) },
                label = { Text("Student ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = vm.uploadUrl.value,
                onValueChange = { vm.setUploadUrl(it) },
                label = { Text("Upload URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = vm.apiKey.value,
                onValueChange = { vm.setApiKey(it) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.uploadAllPending(snackbar) }) { Text("Upload to Class Sheet") }
                OutlinedButton(onClick = { vm.exportCsv(ctx, snackbar) }) { Text("Export CSV (per-minute)") }
            }
        }
    }
}

private fun sanitizeDb(value: Double): Double {
    // Минимум 0.0 дБ, максимум 120 дБ
    return value.coerceIn(0.0, 120.0)
}

private fun exportHistory(snackbar: SnackbarHostState, history: List<NoisePoint>, sid: String = "") {
    if (history.isEmpty()) {
        launchSnackbar(snackbar, "Нет данных для экспорта")
        return
    }
    try {
        // Мини‑MVP: считаем CSV длину. Уберём зависимость serialization, чтобы не ругалось.
            val csv = buildCsvStringAggregated(
                // преобразуем отображаемую историю в формат измерений для повторного использования агрегатора
                history.map { com.example.noiceapp.data.NoiseMeasurement(0, it.timeMillis, it.db, null, null, null, null) },
                sid,
                Build.MODEL
            )
        launchSnackbar(snackbar, "Экспортировано: ${csv.length} символов CSV")
    } catch (_: Throwable) {
        launchSnackbar(snackbar, "Ошибка экспорта CSV")
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: NoiseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoiceAppTheme {
                val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Шумомер") }
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    val nav = rememberNavController()
                    val backStack by nav.currentBackStackEntryAsState()
                    val route = backStack?.destination?.route ?: "measurement"
                    Column(Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            NavHost(navController = nav, startDestination = "measurement", modifier = Modifier.padding(innerPadding)) {
                                composable("measurement") {
                                    NoiseMeasurementScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        vm = viewModel,
                                        snackbar = snackbarHostState
                                    )
                                }
                                composable("graph") { 
                                    NoiseGraphScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        vm = viewModel,
                                        snackbar = snackbarHostState
                                    ) 
                                }
                                composable("forecast") { NoiseForecastScreen(vm = viewModel) }
                                composable("about") { AboutScreen() }
                            }
                        }
                        NavigationBar {
                            NavigationBarItem(
                                selected = route == "measurement",
                                onClick = { nav.navigate("measurement") },
                                icon = { Icon(MaterialIcons.Outlined.PieChart, contentDescription = null) },
                                label = { Text("Замер") }
                            )
                            NavigationBarItem(
                                selected = route == "graph",
                                onClick = { nav.navigate("graph") },
                                icon = { Icon(MaterialIcons.Outlined.Timeline, contentDescription = null) },
                                label = { Text("График") }
                            )
                            NavigationBarItem(
                                selected = route == "forecast",
                                onClick = { nav.navigate("forecast") },
                                icon = { Icon(MaterialIcons.Outlined.ListAlt, contentDescription = null) },
                                label = { Text("Прогноз") }
                            )
                            NavigationBarItem(
                                selected = route == "about",
                                onClick = { nav.navigate("about") },
                                icon = { Icon(MaterialIcons.Outlined.Info, contentDescription = null) },
                                label = { Text("О проекте") }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildCsvStringAggregated(
    all: List<com.example.noiceapp.data.NoiseMeasurement>,
    studentId: String,
    deviceModel: String
): String {
    data class Agg(
        var sum: Double = 0.0,
        var cnt: Int = 0,
        var max: Double = Double.NEGATIVE_INFINITY,
        var lat: Double? = null,
        var lon: Double? = null
    )
    val map = java.util.TreeMap<Long, Agg>()
    all.forEach { m ->
        val minuteStart = (m.timestampMillis / 60000L) * 60000L
        val a = map.getOrPut(minuteStart) { Agg() }
        a.sum += m.laeqDb
        a.cnt += 1
        if (m.laeqDb > a.max) a.max = m.laeqDb
        if (m.latitude != null && m.longitude != null) { a.lat = m.latitude; a.lon = m.longitude }
    }
    return buildString {
        appendLine("minute_start_utc,student_id,avg_db,max_db,lat,lon,device_model")
        map.forEach { (minuteStart, a) ->
            val avg = if (a.cnt > 0) a.sum / a.cnt else 0.0
            val iso = java.time.Instant.ofEpochMilli(minuteStart).atOffset(java.time.ZoneOffset.UTC).toInstant().toString()
            val lat = a.lat?.let { String.format("%.6f", it) } ?: ""
            val lon = a.lon?.let { String.format("%.6f", it) } ?: ""
            appendLine("$iso,$studentId,${String.format("%.1f", avg.coerceIn(0.0, 120.0))},${String.format("%.1f", a.max.coerceIn(0.0, 120.0))},$lat,$lon,$deviceModel")
        }
    }
}

// Экран 1: Замер шума
@Composable
fun NoiseMeasurementScreen(modifier: Modifier = Modifier, vm: NoiseViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val isRecording by vm.isRecording
    val currentDb by vm.currentDb

    // Локальные функции должны быть объявлены до первого использования
    fun startRecordingService() {
        ctx.startForegroundService(Intent(ctx, NoiseForegroundService::class.java))
        vm.onStartRecording()
    }

    fun stopRecordingService() {
        ctx.stopService(Intent(ctx, NoiseForegroundService::class.java))
        vm.onStopRecording()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecordingService() }

    fun ensurePermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecordingService() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(32.dp))
        
        // Большой круглый индикатор
        val dbValue = sanitizeDb(currentDb)
        val color = when {
            dbValue < 50 -> Color(0xFF4CAF50) // Зелёный
            dbValue < 65 -> Color(0xFFFF9800) // Жёлтый
            else -> Color(0xFFF44336) // Красный
        }
        
        Box(
            modifier = Modifier
                .size(240.dp)
                .border(8.dp, color, CircleShape)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%.1f", dbValue),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "дБ",
                    fontSize = 24.sp,
                    color = color
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            text = vm.classify(dbValue),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(Modifier.height(48.dp))
        
        // Кнопка Старт/Стоп
        Button(
            onClick = { 
                if (isRecording) stopRecordingService() 
                else ensurePermissionAndStart() 
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error 
                                else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isRecording) "СТОП ЗАМЕРА" else "СТАРТ ЗАМЕРА",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        if (isRecording) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.6f),
                color = color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Идёт замер...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(Modifier.weight(1f))
        
        Text(
            text = "Показания приблизительные (без сертифицированной калибровки)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

// Экран 2: График
@Composable
fun NoiseGraphScreen(modifier: Modifier = Modifier, vm: NoiseViewModel, snackbar: SnackbarHostState) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "График шума",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Линейный график
        SectionCard(title = "Уровень шума за последние минуты") {
            Spacer(Modifier.height(8.dp))
            Chart(
                history = vm.history.map { it.copy(db = sanitizeDb(it.db)) },
                minDb = 0.0,
                maxDb = 120.0,
                height = 200
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Подписи уровней
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Тихо",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        "< 50 дБ",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFFFF9800), CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Средне",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        "50-65 дБ",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(0xFFF44336), CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Шумно",
                        fontSize = 12.sp,
                        color = Color(0xFFF44336)
                    )
                    Text(
                        "> 65 дБ",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Аналитика
        SectionCard(title = "Статистика") {
            AnalyticsRow(title = "1 мин", vm = vm, windowSec = 60)
            Spacer(Modifier.height(8.dp))
            AnalyticsRow(title = "5 мин", vm = vm, windowSec = 300)
        }
    }
}

// Экран 3: Прогноз
@Composable
fun NoiseForecastScreen(vm: NoiseViewModel) {
    val lastForecast = remember(vm.history) { vm.forecastNext() }
    val currentDb by vm.currentDb
    val recent = vm.history.takeLast(20)
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Прогноз",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            "Вероятно, в ближайшие 5 минут:",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Иконка и прогноз
        val avgRecent = if (recent.isNotEmpty()) recent.map { it.db }.average() else currentDb
        val icon = when {
            avgRecent < 50 -> MaterialIcons.Outlined.WbSunny // Солнышко - тишина
            avgRecent < 65 -> MaterialIcons.Outlined.VolumeOff // Тишина
            else -> MaterialIcons.Outlined.MusicNote // Колонки - шумно
        }
        
        val iconColor = when {
            avgRecent < 50 -> Color(0xFF4CAF50)
            avgRecent < 65 -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
        
        val prediction = when {
            avgRecent < 50 -> "Будет тихо"
            avgRecent < 65 -> "Умеренный шум"
            else -> "Будет шумно"
        }
        
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = iconColor
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            prediction,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = iconColor
        )
        
        Spacer(Modifier.height(8.dp))
        
        lastForecast?.let { forecast ->
            Text(
                "Ожидаемый уровень: ${"%.1f".format(forecast)} дБ",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Детали прогноза
        SectionCard(title = "На основе последних измерений") {
            Text("Количество замеров: ${recent.size}")
            if (recent.isNotEmpty()) {
                Text("Средний уровень: ${"%.1f".format(avgRecent)} дБ")
                Text("Тенденция: ${if (recent.size >= 2 && recent.last().db > recent.first().db) "рост" else "снижение"}")
            }
        }
    }
}

// Экран 4: О проекте
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "О проекте",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        SectionCard(title = "Как работает приложение") {
            Text(
                text = "Приложение использует микрофон вашего устройства для измерения уровня шума " +
                       "в окружающей среде. Звуковые волны преобразуются в цифровые данные и анализируются " +
                       "для определения громкости в децибелах (дБ).\n\n" +
                       "Измерения проводятся в реальном времени с частотой один раз в секунду. " +
                       "Данные сохраняются в локальной базе данных для построения графиков и статистики.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        SectionCard(title = "Что такое децибел") {
            Text(
                text = "Децибел (дБ) — это единица измерения громкости звука. " +
                       "Шкала децибелов логарифмическая, что означает:\n\n" +
                       "• 0-30 дБ — Очень тихо (шёпот, тихая библиотека)\n" +
                       "• 30-50 дБ — Тихо (спокойная комната, офис)\n" +
                       "• 50-65 дБ — Умеренно (обычная речь, фоновая музыка)\n" +
                       "• 65-80 дБ — Громко (оживлённая улица, ресторан)\n" +
                       "• 80+ дБ — Очень громко (транспорт, строительство)\n\n" +
                       "Важно: длительное воздействие звука свыше 85 дБ может повредить слух.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        SectionCard(title = "Примеры уровней шума") {
            Column {
                ExampleRow("10 дБ", "Дыхание", Color(0xFF4CAF50))
                ExampleRow("20 дБ", "Шёпот", Color(0xFF4CAF50))
                ExampleRow("40 дБ", "Тихая библиотека", Color(0xFF4CAF50))
                ExampleRow("60 дБ", "Обычная речь", Color(0xFFFF9800))
                ExampleRow("70 дБ", "Пылесос", Color(0xFFFF9800))
                ExampleRow("80 дБ", "Городская улица", Color(0xFFF44336))
                ExampleRow("90 дБ", "Мотоцикл", Color(0xFFF44336))
                ExampleRow("100 дБ", "Отбойный молоток", Color(0xFFF44336))
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        SectionCard(title = "Точность измерений") {
            Text(
                text = "Измерения являются приблизительными и зависят от:\n\n" +
                       "• Качества микрофона устройства\n" +
                       "• Калибровки приложения\n" +
                       "• Окружающих условий\n" +
                       "• Положения устройства\n\n" +
                       "Для точных измерений используйте профессиональные шумомеры.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExampleRow(db: String, description: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = db,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = description,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun NoiseScreen(modifier: Modifier = Modifier, vm: NoiseViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val isRecording by vm.isRecording
    val currentDb by vm.currentDb
    val offsetDb by vm.offsetDb

    // Локальные функции должны быть объявлены до первого использования
    fun startRecordingService() {
        ctx.startForegroundService(Intent(ctx, NoiseForegroundService::class.java))
        vm.onStartRecording()
    }

    fun stopRecordingService() {
        ctx.stopService(Intent(ctx, NoiseForegroundService::class.java))
        vm.onStopRecording()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecordingService() }

    fun ensurePermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecordingService() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = "Сейчас", onInfo = {
            launchSnackbar(snackbar, "Текущий приблизительный уровень шума, рассчитанный по сигналу микрофона.")
        }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val shown = sanitizeDb(currentDb)
                Text(
                    text = String.format("%.1f дБ", shown),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Сейчас: ${vm.classify(shown)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "История (2–5 мин)", onInfo = {
            launchSnackbar(snackbar, "График показывает последние измерения за 2–5 минут, 1 точка в секунду.")
        }) {
            Spacer(Modifier.height(4.dp))
            Chart(history = vm.history.map { it.copy(db = sanitizeDb(it.db)) }, minDb = 0.0, maxDb = 120.0)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { exportHistory(snackbar, vm.history, vm.studentId.value) }) { Text("Экспортировать видимую историю (минуты)") }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Аналитика", onInfo = {
            launchSnackbar(snackbar, "Минимум/среднее/максимум и доля времени выше порогов 55/65/75 дБ.")
        }) {
            Spacer(Modifier.height(8.dp))
            AnalyticsRow(title = "1 мин", vm = vm, windowSec = 60)
            Spacer(Modifier.height(6.dp))
            AnalyticsRow(title = "5 мин", vm = vm, windowSec = 300)
            Spacer(Modifier.height(8.dp))
            val m = vm.metrics.value
            if (m != null) {
                Text(
                    text = "Среднее (1 мин): ${"%.0f".format(m.avg1)} дБ · >65 дБ: ${formatPct(m.p65_1)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Прогноз", onInfo = {
            launchSnackbar(snackbar, "Прогноз на 1 минуту; статический снимок, обновляется при открытии экрана.")
        }) {
            Spacer(Modifier.height(8.dp))
            val f = remember(vm.history) { vm.forecastNext() }
            Text(text = f?.let { "Прогноз (≈1 мин): ${"%.1f".format(it)} дБ" }
                ?: "Недостаточно данных для прогноза")
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Калибровка", onInfo = {
            launchSnackbar(snackbar, "Сдвиг в дБ для подстройки показаний под ваше устройство.")
        }) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Смещение: ${"%.0f".format(offsetDb)} дБ", modifier = Modifier.weight(0.5f))
                Slider(
                    value = offsetDb.toFloat(),
                    onValueChange = { vm.setOffsetDb(it.toDouble()) },
                    valueRange = 50f..100f,
                    modifier = Modifier.weight(0.5f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.calibrateTo(35.0) }) { Text("Тихая\nкомната 35 дБ") }
                OutlinedButton(onClick = { vm.calibrateTo(60.0) }) { Text("Речь 60 дБ") }
            }
        }

        Spacer(Modifier.height(12.dp))


        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = { if (!isRecording) ensurePermissionAndStart() },
                enabled = !isRecording,
                modifier = Modifier.weight(1f)
            ) { Text("Старт") }
            FilledTonalButton(
                onClick = { if (isRecording) stopRecordingService() },
                enabled = isRecording,
                modifier = Modifier.weight(1f)
            ) { Text("Стоп") }
            OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.weight(1f)) { Text("Сброс") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Показания приблизительные (без сертифицированной калибровки).",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(title: String, onInfo: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (onInfo != null) {
                    IconButton(onClick = onInfo) {
                        Icon(
                            imageVector = MaterialIcons.Outlined.Info,
                            contentDescription = "Информация"
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun AnalyticsRow(title: String, vm: NoiseViewModel, windowSec: Int) {
    val triple = vm.analytics(windowSec)
    val p55 = vm.ratioAbove(55.0, windowSec)
    val p65 = vm.ratioAbove(65.0, windowSec)
    val p75 = vm.ratioAbove(75.0, windowSec)
    if (triple == null) {
        Text("$title: недостаточно данных")
    } else {
        val (min, avg, max) = triple
        Text(
            "$title — min ${"%.0f".format(min)} / avg ${"%.0f".format(avg)} / max ${"%.0f".format(max)} дБ\n" +
                    ">55 дБ: ${formatPct(p55)}  ·  >65 дБ: ${formatPct(p65)}  ·  >75 дБ: ${formatPct(p75)}"
        )
    }
}

private fun formatPct(p: Double?): String = p?.let { "${"%.0f".format(it * 100)}%" } ?: "—"
private fun formatPct(p: Double): String = "${"%.0f".format(p * 100)}%"

@Composable
fun Chart(history: List<NoisePoint>, minDb: Double, maxDb: Double, height: Int = 160) {
    val raw = history.takeLast(240)
    val points = if (raw.size < 3) raw else smooth(raw, window = 5)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val accent = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Сетка по 10 дБ
            for (db in (minDb.toInt()..maxDb.toInt() step 10)) {
                val t = (db - minDb) / (maxDb - minDb + 1e-9)
                val y = (h - (t * h)).toFloat()
                drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
            }

            if (points.isNotEmpty()) {
                val path = Path()
                val n = points.size
                fun norm(y: Double): Float {
                    val clamped = y.coerceIn(minDb, maxDb)
                    val t = (clamped - minDb) / (maxDb - minDb + 1e-9)
                    return (h - (t * h)).toFloat()
                }
                for (i in points.indices) {
                    val x = if (n == 1) 0f else i.toFloat() / (n - 1).toFloat() * w
                    val y = norm(points[i].db)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = lineColor, alpha = 0.95f)

                // Метка последней точки
                val lastX = w
                val lastDb = points.last().db
                val lastY = norm(lastDb)
                drawCircle(color = accent, radius = 4f, center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                // Подпись текста через drawIntoCanvas
                drawIntoCanvas { c ->
                    val p = NPaint().apply {
                        color = NColor.GRAY
                        textSize = 28f
                        isAntiAlias = true
                    }
                    c.nativeCanvas.drawText(String.format("%.1f дБ", lastDb), lastX - 160f, lastY - 12f, p)
                }
            }
        }
    }
}

private fun smooth(data: List<NoisePoint>, window: Int): List<NoisePoint> {
    if (data.isEmpty() || window <= 1) return data
    val half = window / 2
    val result = ArrayList<NoisePoint>(data.size)
    for (i in data.indices) {
        val from = maxOf(0, i - half)
        val to = minOf(data.lastIndex, i + half)
        val avg = data.subList(from, to + 1).map { it.db }.average()
        result.add(NoisePoint(data[i].timeMillis, avg))
    }
    return result
}
