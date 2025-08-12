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
import com.example.noiceapp.data.NoiseAnalysis
import com.example.noiceapp.data.NoiseAnalysesRepository
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import com.example.noiceapp.data.CityNoiseAnalysis
import com.example.noiceapp.data.CityNoiseAnalysisRepository
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.FlowRow
// import removed: ExperimentalLayoutApi not needed after removing FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocationCity

data class NoisePoint(val timeMillis: Long, val db: Double)

class NoiseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoiseRepository = NoiseRepository(NoiseDatabase.get(application).noiseDao())
    private val samplesRepository: CitySamplesRepository = CitySamplesRepository(NoiseDatabase.get(application).citySampleDao())
    // analyses repository
    private val analysesRepository: com.example.noiceapp.data.NoiseAnalysesRepository = NoiseAnalysesRepository(NoiseDatabase.get(application).analysisDao())
    // city noise analysis repository
    private val cityNoiseRepository: CityNoiseAnalysisRepository = CityNoiseAnalysisRepository(NoiseDatabase.get(application).cityNoiseAnalysisDao())
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

    // Экран Анализы: сохранённые агрегаты
    private val _analyses = mutableStateListOf<NoiseAnalysis>()
    val analyses: List<NoiseAnalysis> get() = _analyses

    // Экран Город: анализы шума в городах Казахстана
    private val _cityNoiseAnalyses = mutableStateListOf<CityNoiseAnalysis>()
    val cityNoiseAnalyses: List<CityNoiseAnalysis> get() = _cityNoiseAnalyses

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
                kotlinx.coroutines.withContext(Dispatchers.Main) {
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
        }
        // Подписка на городские выборки
        viewModelScope.launch(Dispatchers.IO) {
            samplesRepository.observe().collectLatest { samples ->
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                _citySamples.clear(); _citySamples.addAll(samples)
                }
            }
        }
        // Подписка на сохранённый offset из DataStore
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.offsetDbFlow.collectLatest { value ->
                kotlinx.coroutines.withContext(Dispatchers.Main) { _offsetDb.doubleValue = value }
            }
        }
        // Подписка на настройки выгрузки
        viewModelScope.launch(Dispatchers.IO) { settingsStore.studentIdFlow.collectLatest { val v = it; kotlinx.coroutines.withContext(Dispatchers.Main) { _studentId.value = v } } }
        viewModelScope.launch(Dispatchers.IO) { settingsStore.uploadUrlFlow.collectLatest { val v = it; kotlinx.coroutines.withContext(Dispatchers.Main) { _uploadUrl.value = v } } }
        viewModelScope.launch(Dispatchers.IO) { settingsStore.apiKeyFlow.collectLatest { val v = it; kotlinx.coroutines.withContext(Dispatchers.Main) { _apiKey.value = v } } }
        // Подписка на список сохранённых анализов
        viewModelScope.launch(Dispatchers.IO) {
            analysesRepository.observe().collectLatest { list ->
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _analyses.clear(); _analyses.addAll(list)
                }
            }
        }
        // Подписка на городские анализы шума
        viewModelScope.launch(Dispatchers.IO) {
            cityNoiseRepository.observe().collectLatest { list ->
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _cityNoiseAnalyses.clear(); _cityNoiseAnalyses.addAll(list)
                }
            }
        }
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

    // Универсальный разбор временных меток для CSV: ISO8601 с Z, epoch millis или seconds
    private fun parseTimestampMillis(text: String): Long? {
        val s = text.trim().replace("\uFEFF", "")
        // epoch millis or seconds
        s.toLongOrNull()?.let { num ->
            // if looks like seconds (10-11 digits), convert to millis
            return if (num in 1_000_000_000L..99_999_999_999L) num * 1000L else num
        }
        return try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (_: Throwable) {
            null
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

    // Сохранить текущий анализ (последние N секунд) в БД
    fun saveCurrentAnalysis(windowSec: Int = 300) {
        val now = System.currentTimeMillis()
        val from = now - windowSec * 1000L
        val slice = history.filter { it.timeMillis >= from }
        if (slice.isEmpty()) return
        val values = slice.map { sanitizeDb(it.db) }
        val analysis = NoiseAnalysis(
            timestampMillis = now,
            avgDb = values.average(),
            minDb = values.minOrNull() ?: 0.0,
            maxDb = values.maxOrNull() ?: 0.0,
            count = values.size,
            lastDb = values.last()
        )
        viewModelScope.launch(Dispatchers.IO) { analysesRepository.add(analysis) }
    }

    fun deleteAnalysis(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { analysesRepository.delete(id) }
    }

    fun databaseSizeBytes(context: android.content.Context): Long {
        return try { context.getDatabasePath("noise_db").length() } catch (_: Throwable) { 0L }
    }

    fun exportAnalysesCsv(context: android.content.Context, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = analyses.toList()
                if (list.isEmpty()) { launchSnackbar(snackbar, "Нет данных для экспорта"); return@launch }
                val csv = buildString {
                    appendLine("timestamp_utc,avg_db,min_db,max_db,count,last_db")
                    list.forEach { a ->
                        val iso = java.time.Instant.ofEpochMilli(a.timestampMillis).atOffset(java.time.ZoneOffset.UTC).toInstant().toString()
                        appendLine("$iso,${String.format("%.1f", a.avgDb)},${String.format("%.1f", a.minDb)},${String.format("%.1f", a.maxDb)},${a.count},${String.format("%.1f", a.lastDb)}")
                    }
                }
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

    // Импорт CSV: измерения (timestamp,db[,lat,lon])
    fun importMeasurementsCsv(context: android.content.Context, uri: android.net.Uri, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = ArrayList<com.example.noiceapp.data.NoiseMeasurement>()
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { br ->
                    val header = br.readLine()?.replace("\uFEFF", "")?.lowercase() ?: ""
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
                        val ts = parseTimestampMillis(tsStr) ?: continue
                        val dbStr = get(iDb) ?: continue
                        val dbParsed = dbStr.replace(',', '.').toDoubleOrNull() ?: continue
                        val db = sanitizeDb(dbParsed)
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
                    val header = br.readLine()?.replace("\uFEFF", "")?.lowercase() ?: ""
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
                        val ts = parseTimestampMillis(tsStr) ?: continue
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

    // Добавить анализ шума для города Казахстана
    fun addCityNoiseAnalysis(
        cityName: String,
        district: String,
        street: String,
        coordinates: String?,
        avgDb: Double,
        minDb: Double,
        maxDb: Double,
        peakDb: Double,
        measurementCount: Int,
        durationMinutes: Int,
        noiseType: String,
        timeOfDay: String,
        weatherConditions: String?,
        trafficLevel: Int,
        notes: String?
    ) {
        val now = System.currentTimeMillis()
        val analysis = CityNoiseAnalysis(
            timestampMillis = now,
            cityName = cityName,
            district = district,
            street = street,
            coordinates = coordinates,
            avgDb = avgDb,
            minDb = minDb,
            maxDb = maxDb,
            peakDb = peakDb,
            measurementCount = measurementCount,
            durationMinutes = durationMinutes,
            noiseType = noiseType,
            timeOfDay = timeOfDay,
            weatherConditions = weatherConditions,
            trafficLevel = trafficLevel,
            notes = notes,
            forecastNextHour = calculateForecast(avgDb, trafficLevel, timeOfDay),
            forecastNextDay = calculateForecast(avgDb, trafficLevel, timeOfDay, isDaily = true),
            riskLevel = calculateRiskLevel(avgDb, peakDb)
        )
        viewModelScope.launch(Dispatchers.IO) { cityNoiseRepository.add(analysis) }
    }

    // Прогноз шума на основе текущих данных
    private fun calculateForecast(currentDb: Double, trafficLevel: Int, timeOfDay: String, isDaily: Boolean = false): Double {
        val baseChange = when (timeOfDay) {
            "утро" -> 5.0
            "день" -> 8.0
            "вечер" -> 3.0
            "ночь" -> -10.0
            else -> 0.0
        }
        
        val trafficFactor = (trafficLevel - 3) * 2.0
        val timeMultiplier = if (isDaily) 0.8 else 1.0
        
        return (currentDb + baseChange + trafficFactor * timeMultiplier).coerceIn(0.0, 120.0)
    }

    // Расчёт уровня риска
    private fun calculateRiskLevel(avgDb: Double, peakDb: Double): String {
        return when {
            avgDb < 50 && peakDb < 70 -> "низкий"
            avgDb < 65 && peakDb < 80 -> "средний"
            else -> "высокий"
        }
    }

    // Импорт CSV файлов для городских анализов
    fun importCityNoiseCsv(context: android.content.Context, uri: android.net.Uri, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = ArrayList<CityNoiseAnalysis>()
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { br ->
                    val header = br.readLine()?.replace("\uFEFF", "")?.lowercase() ?: ""
                    val cols = header.split(',').map { it.trim() }
                    
                    // Определяем индексы колонок
                    val iTs = cols.indexOfFirst { it in setOf("timestamp","timestamp_utc","time","ts") }
                    val iCity = cols.indexOfFirst { it in setOf("city","city_name","город") }
                    val iDistrict = cols.indexOfFirst { it in setOf("district","район") }
                    val iStreet = cols.indexOfFirst { it in setOf("street","улица") }
                    val iAvgDb = cols.indexOfFirst { it in setOf("avg_db","average_db","среднее") }
                    val iMinDb = cols.indexOfFirst { it in setOf("min_db","minimum_db","минимум") }
                    val iMaxDb = cols.indexOfFirst { it in setOf("max_db","maximum_db","максимум") }
                    val iCount = cols.indexOfFirst { it in setOf("count","n","samples") }
                    val iLastDb = cols.indexOfFirst { it in setOf("last_db","last","current_db") }
                    val iType = cols.indexOfFirst { it in setOf("noise_type","type","тип") }
                    
                    if (iTs == -1 || iAvgDb == -1) {
                        launchSnackbar(snackbar, "CSV должен содержать колонки: timestamp/timestamp_utc, avg_db (остальные необязательны)")
                        return@launch
                    }
                    
                    br.lineSequence().forEach { line ->
                        val values = line.split(',').map { it.trim() }
                        if (values.size > cols.size) return@forEach
                        
                        val tsStr = values.getOrNull(iTs) ?: return@forEach
                        val ts = parseTimestampMillis(tsStr) ?: return@forEach
                        
                        val city = values.getOrNull(iCity) ?: "Алматы"
                        val district = values.getOrNull(iDistrict) ?: "Центральный"
                        val street = values.getOrNull(iStreet) ?: "Неизвестно"
                        val avgDbRaw = values.getOrNull(iAvgDb)?.replace(',', '.')?.toDoubleOrNull() ?: return@forEach
                        val minDbRaw = values.getOrNull(iMinDb)?.replace(',', '.')?.toDoubleOrNull()
                        val maxDbRaw = values.getOrNull(iMaxDb)?.replace(',', '.')?.toDoubleOrNull()
                        val countVal = values.getOrNull(iCount)?.toIntOrNull()
                        val lastDbRaw = values.getOrNull(iLastDb)?.replace(',', '.')?.toDoubleOrNull()
                        var avgDb = sanitizeDb(avgDbRaw)
                        var minDb = sanitizeDb(minDbRaw ?: avgDb)
                        var maxDb = sanitizeDb(maxDbRaw ?: avgDb)
                        if (minDb > maxDb) {
                            val tmp = minDb
                            minDb = maxDb
                            maxDb = tmp
                        }
                        val lastDb = lastDbRaw?.let { sanitizeDb(it) }
                        val peakDb = maxOf(maxDb, lastDb ?: 0.0)
                        val noiseType = values.getOrNull(iType) ?: "Транспорт"
                        
                        val analysis = CityNoiseAnalysis(
                            timestampMillis = ts,
                            cityName = city,
                            district = district,
                            street = street,
                            coordinates = null,
                            avgDb = avgDb,
                            minDb = minDb,
                            maxDb = maxDb,
                            peakDb = peakDb,
                            measurementCount = countVal ?: 100,
                            durationMinutes = 10,
                            noiseType = noiseType,
                            timeOfDay = "день",
                            weatherConditions = null,
                            trafficLevel = 3,
                            notes = "Импортировано из CSV",
                            forecastNextHour = calculateForecast(avgDb, 3, "день"),
                            forecastNextDay = calculateForecast(avgDb, 3, "день", true),
                            riskLevel = calculateRiskLevel(avgDb, maxDb)
                        )
                        parsed.add(analysis)
                    }
                }
                
                if (parsed.isEmpty()) { 
                    launchSnackbar(snackbar, "CSV пуст/не распознан"); return@launch 
                }
                
                parsed.forEach { cityNoiseRepository.add(it) }
                launchSnackbar(snackbar, "Импортировано городских анализов: ${parsed.size}")
            } catch (_: Throwable) {
                launchSnackbar(snackbar, "Ошибка импорта CSV")
            }
        }
    }

    // Экспорт городских анализов в CSV
    fun exportCityNoiseCsv(context: android.content.Context, snackbar: SnackbarHostState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = cityNoiseAnalyses.toList()
                if (list.isEmpty()) { 
                    launchSnackbar(snackbar, "Нет данных для экспорта"); return@launch 
                }
                
                val csv = buildString {
                    appendLine("timestamp_utc,city_name,district,street,avg_db,min_db,max_db,peak_db,noise_type,time_of_day,traffic_level,risk_level,forecast_next_hour,forecast_next_day")
                    list.forEach { a ->
                        val iso = java.time.Instant.ofEpochMilli(a.timestampMillis).atOffset(java.time.ZoneOffset.UTC).toInstant().toString()
                        appendLine("$iso,${a.cityName},${a.district},${a.street},${String.format("%.1f", a.avgDb)},${String.format("%.1f", a.minDb)},${String.format("%.1f", a.maxDb)},${String.format("%.1f", a.peakDb)},${a.noiseType},${a.timeOfDay},${a.trafficLevel},${a.riskLevel},${a.forecastNextHour?.let { String.format("%.1f", it) } ?: ""},${a.forecastNextDay?.let { String.format("%.1f", it) } ?: ""}")
                    }
                }
                
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

    // Получить статистику по городам
    fun getCityStatistics(): Map<String, CityStats> {
        val stats = mutableMapOf<String, CityStats>()
        cityNoiseAnalyses.forEach { analysis ->
            val city = analysis.cityName
            val existing = stats[city] ?: CityStats(city, 0, 0.0, 0.0, 0.0, 0, 0.0)
            stats[city] = existing.copy(
                analysisCount = existing.analysisCount + 1,
                totalDb = existing.totalDb + analysis.avgDb,
                minDb = kotlin.math.min(existing.minDb, analysis.minDb),
                maxDb = kotlin.math.max(existing.maxDb, analysis.maxDb),
                totalTraffic = existing.totalTraffic + analysis.trafficLevel,
                avgRisk = (existing.avgRisk + (if (analysis.riskLevel == "высокий") 3.0 else if (analysis.riskLevel == "средний") 2.0 else 1.0)) / 2.0
            )
        }
        return stats
    }

    data class CityStats(
        val cityName: String,
        val analysisCount: Int,
        val totalDb: Double,
        val minDb: Double,
        val maxDb: Double,
        val totalTraffic: Int,
        val avgRisk: Double
    ) {
        val avgDb: Double get() = if (analysisCount > 0) totalDb / analysisCount else 0.0
        val avgTraffic: Double get() = if (analysisCount > 0) totalTraffic.toDouble() / analysisCount else 0.0
    }

    // Удалить городской анализ
    fun deleteCityNoiseAnalysis(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { cityNoiseRepository.delete(id) }
    }

    // Создать демо-данные для городов Казахстана
    fun addDemoCityData() {
        viewModelScope.launch(Dispatchers.IO) {
            val demoData = listOf(
                CityNoiseAnalysis(
                    timestampMillis = System.currentTimeMillis() - 3600000, // 1 час назад
                    cityName = "Алматы",
                    district = "Центральный",
                    street = "Достык",
                    coordinates = "43.238949,76.889709",
                    avgDb = 68.5,
                    minDb = 45.2,
                    maxDb = 89.7,
                    peakDb = 95.3,
                    measurementCount = 120,
                    durationMinutes = 15,
                    noiseType = "Транспорт",
                    timeOfDay = "день",
                    weatherConditions = "Солнечно",
                    trafficLevel = 4,
                    notes = "Основная магистраль, оживлённое движение",
                    forecastNextHour = 72.1,
                    forecastNextDay = 65.8,
                    riskLevel = "высокий"
                ),
                CityNoiseAnalysis(
                    timestampMillis = System.currentTimeMillis() - 7200000, // 2 часа назад
                    cityName = "Алматы",
                    district = "Медеуский",
                    street = "Абая",
                    coordinates = "43.215000,76.851000",
                    avgDb = 55.3,
                    minDb = 38.1,
                    maxDb = 72.4,
                    peakDb = 78.9,
                    measurementCount = 90,
                    durationMinutes = 12,
                    noiseType = "Транспорт",
                    timeOfDay = "утро",
                    weatherConditions = "Облачно",
                    trafficLevel = 3,
                    notes = "Умеренное движение, жилой район",
                    forecastNextHour = 58.2,
                    forecastNextDay = 52.1,
                    riskLevel = "средний"
                ),
                CityNoiseAnalysis(
                    timestampMillis = System.currentTimeMillis() - 10800000, // 3 часа назад
                    cityName = "Астана",
                    district = "Алматинский",
                    street = "Республика",
                    coordinates = "51.180100,71.446000",
                    avgDb = 62.7,
                    minDb = 42.8,
                    maxDb = 81.2,
                    peakDb = 87.5,
                    measurementCount = 100,
                    durationMinutes = 14,
                    noiseType = "Транспорт",
                    timeOfDay = "день",
                    weatherConditions = "Ветрено",
                    trafficLevel = 4,
                    notes = "Центральная улица, много офисов",
                    forecastNextHour = 67.3,
                    forecastNextDay = 59.8,
                    riskLevel = "средний"
                ),
                CityNoiseAnalysis(
                    timestampMillis = System.currentTimeMillis() - 14400000, // 4 часа назад
                    cityName = "Шымкент",
                    district = "Центральный",
                    street = "Туркестанская",
                    coordinates = "42.300000,69.600000",
                    avgDb = 59.1,
                    minDb = 41.5,
                    maxDb = 76.8,
                    peakDb = 82.1,
                    measurementCount = 85,
                    durationMinutes = 11,
                    noiseType = "Транспорт",
                    timeOfDay = "утро",
                    weatherConditions = "Ясно",
                    trafficLevel = 3,
                    notes = "Торговая улица, оживлённая",
                    forecastNextHour = 61.9,
                    forecastNextDay = 55.4,
                    riskLevel = "средний"
                )
            )
            
            demoData.forEach { cityNoiseRepository.add(it) }
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
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
private fun CityScreen(vm: NoiseViewModel) {
    var selectedCity by remember { mutableStateOf("Алматы") }
    var selectedDistrict by remember { mutableStateOf("Центральный") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    
    val cities = remember { listOf("Алматы", "Астана", "Шымкент", "Актобе", "Караганда", "Тараз", "Павлодар", "Семей", "Усть-Каменогорск", "Уральск") }
    val districts = remember(selectedCity) {
        when (selectedCity) {
            "Алматы" -> listOf("Центральный", "Алмалинский", "Ауэзовский", "Бостандыкский", "Жетысуский", "Медеуский", "Наурызбайский", "Турксибский")
            "Астана" -> listOf("Алматинский", "Есильский", "Сарыаркинский", "Байконурский")
            else -> listOf("Центральный", "Северный", "Южный", "Восточный", "Западный")
        }
    }
    
    val cityAnalyses = remember(vm.cityNoiseAnalyses, selectedCity) {
        vm.cityNoiseAnalyses.filter { it.cityName == selectedCity }
    }
    
    val cityStats = remember(cityAnalyses) {
        vm.getCityStatistics()[selectedCity]
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Заголовок
        Text("Анализ шума в городах Казахстана", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Выбор города (улучшенный UI)
        SectionCard(title = "Выбор города") {
            Text("Город", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cities) { city ->
                    FilterChip(
                        selected = selectedCity == city,
                        onClick = { selectedCity = city },
                        label = { Text(city) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Район", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(districts) { d ->
                    AssistChip(
                        onClick = { selectedDistrict = d },
                        label = { Text(d) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selectedDistrict == d) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Статистика города
        cityStats?.let { stats ->
            SectionCard(title = "Статистика $selectedCity") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Анализов: ${stats.analysisCount}", fontWeight = FontWeight.Medium)
                        Text("Средний уровень: ${String.format("%.1f", stats.avgDb)} дБ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("Диапазон: ${String.format("%.1f", stats.minDb)} - ${String.format("%.1f", stats.maxDb)} дБ", fontWeight = FontWeight.Medium)
                        Text("Трафик: ${String.format("%.1f", stats.avgTraffic)}/5", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val riskColor = when {
                    stats.avgRisk < 1.5 -> Color(0xFF4CAF50)
                    stats.avgRisk < 2.5 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Уровень риска: ", fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            stats.avgRisk < 1.5 -> "Низкий"
                            stats.avgRisk < 2.5 -> "Средний"
                            else -> "Высокий"
                        },
                        color = riskColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Кнопки действий
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(MaterialIcons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Добавить анализ")
            }
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(MaterialIcons.Outlined.Upload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Импорт CSV")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.exportCityNoiseCsv(ctx, snackbar) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(MaterialIcons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Экспорт CSV")
            }
            OutlinedButton(
                onClick = { vm.addDemoCityData() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(MaterialIcons.Outlined.LocationCity, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Демо данные")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Список анализов
        Text("Анализы шума в $selectedCity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (cityAnalyses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        MaterialIcons.Outlined.LocationCity,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Нет данных для $selectedCity",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Добавьте первый анализ или импортируйте CSV",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column {
                cityAnalyses.forEach { analysis ->
                    CityAnalysisCard(
                        analysis = analysis,
                        onDelete = { vm.deleteCityNoiseAnalysis(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Диалог добавления анализа
    if (showAddDialog) {
        AddCityAnalysisDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { cityName, district, street, avgDb, minDb, maxDb, noiseType, timeOfDay, trafficLevel, notes ->
                vm.addCityNoiseAnalysis(
                    cityName = cityName,
                    district = district,
                    street = street,
                    coordinates = null,
                    avgDb = avgDb,
                    minDb = minDb,
                    maxDb = maxDb,
                    peakDb = maxDb,
                    measurementCount = 100,
                    durationMinutes = 10,
                    noiseType = noiseType,
                    timeOfDay = timeOfDay,
                    weatherConditions = null,
                    trafficLevel = trafficLevel,
                    notes = notes
                )
                showAddDialog = false
            },
            selectedCity = selectedCity,
            selectedDistrict = selectedDistrict,
            cities = cities,
            districts = districts
        )
    }

    // Диалог импорта CSV
    if (showImportDialog) {
        val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.importCityNoiseCsv(ctx, uri, snackbar)
            showImportDialog = false
        }
        LaunchedEffect(Unit) {
            pickCsv.launch("text/*")
        }
    }

    // Snackbar для уведомлений
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbar)
    }
}

@Composable
private fun CityAnalysisCard(
    analysis: CityNoiseAnalysis,
    onDelete: (Long) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Заголовок с датой и уровнем риска
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(analysis.timestampMillis)),
                        fontWeight = FontWeight.Medium
                    )
                    Text("${analysis.district}, ${analysis.street}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val riskColor = when (analysis.riskLevel) {
                    "низкий" -> Color(0xFF4CAF50)
                    "средний" -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(analysis.riskLevel.replaceFirstChar { it.uppercase() }) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = riskColor.copy(alpha = 0.2f))
                )
            }

            Spacer(Modifier.height(12.dp))

            // Метрики шума
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${String.format("%.1f", analysis.avgDb)} дБ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Среднее", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${String.format("%.1f", analysis.minDb)} дБ", fontWeight = FontWeight.Medium)
                    Text("Минимум", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${String.format("%.1f", analysis.maxDb)} дБ", fontWeight = FontWeight.Medium)
                    Text("Максимум", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Дополнительная информация
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Тип: ${analysis.noiseType}", fontSize = 12.sp)
                    Text("Время: ${analysis.timeOfDay}", fontSize = 12.sp)
                    Text("Трафик: ${analysis.trafficLevel}/5", fontSize = 12.sp)
                }
                Column {
                    analysis.forecastNextHour?.let { forecast ->
                        Text("Прогноз (1ч): ${String.format("%.1f", forecast)} дБ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    analysis.notes?.let { notes ->
                        if (notes.isNotBlank()) {
                            Text(notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
                IconButton(onClick = { onDelete(analysis.id) }) {
                    Icon(MaterialIcons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddCityAnalysisDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Double, Double, Double, String, String, Int, String?) -> Unit,
    selectedCity: String,
    selectedDistrict: String,
    cities: List<String>,
    districts: List<String>
) {
    var city by remember { mutableStateOf(selectedCity) }
    var district by remember { mutableStateOf(selectedDistrict) }
    var street by remember { mutableStateOf("") }
    var avgDb by remember { mutableStateOf("") }
    var minDb by remember { mutableStateOf("") }
    var maxDb by remember { mutableStateOf("") }
    var noiseType by remember { mutableStateOf("Транспорт") }
    var timeOfDay by remember { mutableStateOf("день") }
    var trafficLevel by remember { mutableStateOf(3) }
    var notes by remember { mutableStateOf("") }

    val noiseTypes = listOf("Транспорт", "Строительство", "Люди", "Промышленность", "Природа")
    val timeOfDayOptions = listOf("утро", "день", "вечер", "ночь")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить анализ шума") },
        text = {
            Column {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Город") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = district,
                    onValueChange = { district = it },
                    label = { Text("Район") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text("Улица") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = avgDb,
                        onValueChange = { avgDb = it },
                        label = { Text("Среднее (дБ)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minDb,
                        onValueChange = { minDb = it },
                        label = { Text("Мин (дБ)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxDb,
                    onValueChange = { maxDb = it },
                    label = { Text("Макс (дБ)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noiseType,
                        onValueChange = { noiseType = it },
                        label = { Text("Тип шума") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeOfDay,
                        onValueChange = { timeOfDay = it },
                        label = { Text("Время суток") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Уровень трафика: $trafficLevel", fontSize = 14.sp)
                Slider(
                    value = trafficLevel.toFloat(),
                    onValueChange = { trafficLevel = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val avg = avgDb.toDoubleOrNull() ?: 0.0
                    val min = minDb.toDoubleOrNull() ?: avg
                    val max = maxDb.toDoubleOrNull() ?: avg
                    if (street.isNotBlank() && avg > 0) {
                        onConfirm(city, district, street, avg, min, max, noiseType, timeOfDay, trafficLevel, notes.takeIf { it.isNotBlank() })
                    }
                }
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun AnalysesScreen(vm: NoiseViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val analyses = vm.analyses
    val dbSizeKb = remember(analyses) { (vm.databaseSizeBytes(ctx) / 1024.0).coerceAtLeast(0.0) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Мои анализы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        // Статистика хранилища
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Статистика хранилища", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Сохранено анализов:"); Text("${analyses.size}", fontWeight = FontWeight.Bold) }
                    Column { Text("Размер данных:"); Text("${String.format("%.1f", dbSizeKb)} КБ", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveCurrentAnalysis() }) { Text("Сохранить анализ") }
                    OutlinedButton(onClick = { vm.exportAnalysesCsv(ctx, snackbar) }) { Text("Экспорт CSV") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Список анализов
        LazyColumn {
            items(analyses) { a ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(a.timestampMillis)), modifier = Modifier.weight(1f))
                            val cls = vm.classify(a.avgDb)
                            AssistChip(onClick = {}, label = { Text(cls.replaceFirstChar { it.uppercase() }) })
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${String.format("%.1f", a.avgDb)} дБ", fontWeight = FontWeight.Bold); Text("Среднее", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${String.format("%.1f", a.minDb)} дБ", fontWeight = FontWeight.Bold); Text("Минимум", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${String.format("%.1f", a.maxDb)} дБ", fontWeight = FontWeight.Bold); Text("Максимум", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("${a.count}"); Text("Замеров", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Column { Text("${String.format("%.1f", a.lastDb)} дБ"); Text("Текущий", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = { vm.deleteAnalysis(a.id) }) { Icon(MaterialIcons.Outlined.Delete, contentDescription = "Удалить") }
                        }
                    }
                }
            }
        }
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
        
        // Иконка и прогноз (без отрицательных значений)
        val avgRecent = if (recent.isNotEmpty()) recent.map { sanitizeDb(it.db) }.average() else sanitizeDb(currentDb)
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

private fun saveCsvToDownloads(context: android.content.Context, csv: String): String? {
    val filename = "noise_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
            val file = java.io.File(downloads, filename)
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
                                composable("analyses") { AnalysesScreen(vm = viewModel, snackbar = snackbarHostState) }
                                composable("city") { CityScreen(vm = viewModel) }
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
                                selected = route == "analyses",
                                onClick = { nav.navigate("analyses") },
                                icon = { Icon(MaterialIcons.Outlined.Save, contentDescription = null) },
                                label = { Text("Анализы") }
                            )
                            NavigationBarItem(
                                selected = route == "city",
                                onClick = { nav.navigate("city") },
                                icon = { Icon(MaterialIcons.Outlined.LocationCity, contentDescription = null) },
                                label = { Text("Город") }
                            )
                            NavigationBarItem(
                                selected = route == "about",
                                onClick = { nav.navigate("about") },
                                icon = { Icon(MaterialIcons.Outlined.Info, contentDescription = null) },
                                label = { Text("Проект") }
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
        SectionCard(title = "Уровень шума за последние минуты") {
            Spacer(Modifier.height(8.dp))
            InteractiveChart(
                history = vm.history.map { it.copy(db = sanitizeDb(it.db)) },
                minDb = 0.0,
                maxDb = 120.0,
                height = 220
            )
        }
        Spacer(Modifier.height(16.dp))
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

        SectionCard(title = "Что нового в этом обновлении") {
            Text(
                text = "• Интерактивный график в стиле трейдинга: масштабирование, панорамирование, маркер точки, подсветка диапазона.\n" +
                       "• Экран \"Город\": Казахстан (Алматы, Астана и др.), выбор районов, список анализов, демо-данные.\n" +
                       "• Импорт/экспорт CSV для обмена: поддержка timestamp_utc, avg_db, min_db, max_db, count, last_db.\n" +
                       "• Корректность дБ: значения всегда ≥ 0 и ≤ 120, отрицательные исключены при записи и импорте.\n" +
                       "• Автообновление: UI реагирует на новые измерения и анализы без перезахода.\n" +
                       "• \"Мои анализы\": сохранение метрик, список, удаление, экспорт.",
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

        SectionCard(title = "Формат CSV для городских анализов") {
            Text(
                text = "Заголовок:\n" +
                       "timestamp_utc,avg_db,min_db,max_db,count,last_db\n\n" +
                       "Пример строки:\n" +
                       "2025-08-12T21:54:26.436Z,20.3,15.1,62.2,16,25.6\n\n" +
                       "Примечания:\n" +
                       "• timestamp_utc — ISO8601 (UTC) или эпоха (мс/сек).\n" +
                       "• avg/min/max/last_db — числа; обрезаются в диапазон 0..120 дБ.\n" +
                       "• city_name/district/street — необязательные колонки; при отсутствии используются значения по умолчанию.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        
        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Города Казахстана и прогноз") {
            Text(
                text = "Поддерживаются города Казахстана (по умолчанию Алматы). Можно добавлять анализы, импортировать CSV, " +
                       "просматривать статистику и получать простой прогноз на основе последних данных.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        
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

@Composable
fun InteractiveChart(history: List<NoisePoint>, minDb: Double, maxDb: Double, height: Int = 200) {
    val raw = history.takeLast(600)
    val points = if (raw.size < 3) raw else smooth(raw, window = 5)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(0f) }
    var markerX by remember { mutableStateOf<Float?>(null) }
    var selectedPoint by remember { mutableStateOf<NoisePoint?>(null) }

    val primaryColor = Color(0xFF2196F3)
    val secondaryColor = Color(0xFF4CAF50)
    val accentColor = Color(0xFFFF9800)
    val gridColor = Color(0xFFE0E0E0)
    val backgroundColor = Color(0xFFFAFAFA)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .border(1.dp, gridColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .pointerInput(points) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                    val contentW = size.width * scale
                    if (contentW > size.width) {
                        val maxOffset = 1f - size.width / contentW
                        if (maxOffset > 0f) offset = (offset - pan.x / contentW).coerceIn(0f, maxOffset)
                    } else {
                        offset = 0f
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pos -> 
                    markerX = pos.x
                    // Find closest point
                    val w = size.width
                    val n = points.size
                    val visibleN = kotlin.math.max(10, (n / scale).toInt())
                    val maxStart = kotlin.math.max(0, n - visibleN)
                    val start = (maxStart * offset).toInt()
                    val end = kotlin.math.min(n, start + visibleN)
                    val window = points.subList(start, end)
                    if (window.isNotEmpty()) {
                        val idx = ((pos.x / w) * (window.size - 1)).toInt().coerceIn(0, window.size - 1)
                        selectedPoint = window[idx]
                    }
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background gradient
            val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(backgroundColor, Color.White),
                startY = 0f,
                endY = h
            )
            drawRect(brush = gradient)

            // Grid lines with better styling
            for (db in (minDb.toInt()..maxDb.toInt() step 5)) {
                val t = (db - minDb) / (maxDb - minDb + 1e-9)
                val y = (h - (t * h)).toFloat()
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(w, y),
                    strokeWidth = 0.5f
                )
            }

            // Time grid lines
            if (points.isNotEmpty()) {
                val timeStep = kotlin.math.max(1, points.size / 10)
                for (i in 0..points.size step timeStep) {
                    if (i < points.size) {
                        val x = (i.toFloat() / points.size) * w
                        drawLine(
                            color = gridColor.copy(alpha = 0.2f),
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, h),
                            strokeWidth = 0.5f
                        )
                    }
                }
            }

            if (points.isNotEmpty()) {
                val n = points.size
                val visibleN = kotlin.math.max(10, (n / scale).toInt())
                val maxStart = kotlin.math.max(0, n - visibleN)
                val start = (maxStart * offset).toInt()
                val end = kotlin.math.min(n, start + visibleN)
                val window = points.subList(start, end)

                // Area chart with gradient
                val areaPath = Path()
                val linePath = Path()
                
                fun norm(y: Double): Float {
                    val clamped = y.coerceIn(minDb, maxDb)
                    val t = (clamped - minDb) / (maxDb - minDb + 1e-9)
                    return (h - (t * h)).toFloat()
                }

                // Create area path
                areaPath.moveTo(0f, h)
                for (i in window.indices) {
                    val x = if (window.size == 1) 0f else i.toFloat() / (window.size - 1).toFloat() * w
                    val y = norm(window[i].db)
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        areaPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }
                areaPath.lineTo(w, h)
                areaPath.close()

                // Draw area with gradient
                val areaGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.3f),
                        primaryColor.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h
                )
                drawPath(path = areaPath, brush = areaGradient)

                // Draw main line
                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                // Draw data points as circles
                for (i in window.indices) {
                    val x = if (window.size == 1) 0f else i.toFloat() / (window.size - 1).toFloat() * w
                    val y = norm(window[i].db)
                    val pointColor = when {
                        window[i].db < 50 -> secondaryColor
                        window[i].db < 65 -> accentColor
                        else -> Color(0xFFF44336)
                    }
                    drawCircle(
                        color = pointColor,
                        radius = 3f,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }

                // Interactive marker
                markerX?.let { mx ->
                    val idx = ((mx / w) * (window.size - 1)).toInt().coerceIn(0, window.size - 1)
                    val sel = window[idx]
                    val y = norm(sel.db)
                    
                    // Vertical line
                    drawLine(
                        color = accentColor,
                        start = androidx.compose.ui.geometry.Offset(mx, 0f),
                        end = androidx.compose.ui.geometry.Offset(mx, h),
                        strokeWidth = 2f
                    )
                    
                    // Horizontal line
                    drawLine(
                        color = accentColor.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(w, y),
                        strokeWidth = 1f
                    )
                    
                    // Marker circle
                    drawCircle(
                        color = accentColor,
                        radius = 6f,
                        center = androidx.compose.ui.geometry.Offset(mx, y),
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3f,
                        center = androidx.compose.ui.geometry.Offset(mx, y),
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                }
            }
        }

        // Floating info card
        selectedPoint?.let { point ->
            val y = norm(point.db, minDb, maxDb, height.dp.value)
            val x = markerX ?: 0f
            
            Box(
                modifier = Modifier
                    .offset(x = (x - 100).dp, y = (y - 80).dp)
                    .background(
                        Color.White,
                        RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, gridColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "${String.format("%.1f", point.db)} дБ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = primaryColor
                    )
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(point.timeMillis)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Scale indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(4.dp)
                )
                .padding(4.dp)
        ) {
            Text(
                text = "Масштаб: ${String.format("%.1fx", scale)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun norm(value: Double, min: Double, max: Double, height: Float): Float {
    val clamped = value.coerceIn(min, max)
    val t = (clamped - min) / (max - min + 1e-9)
    return height - (t * height).toFloat()
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
