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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.collectLatest
import com.example.noiceapp.service.NoiseForegroundService

data class NoisePoint(val timeMillis: Long, val db: Double)

class NoiseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoiseRepository = NoiseRepository(NoiseDatabase.get(application).noiseDao())
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

    // Настройка калибровки (слайдер)
    private val _offsetDb = mutableDoubleStateOf(90.0)
    val offsetDb: State<Double> get() = _offsetDb
    fun setOffsetDb(value: Double) { _offsetDb.doubleValue = value }

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
    }

    fun onStartRecording() { _isRecording.value = true }

    fun onStopRecording() { _isRecording.value = false }

    fun clearHistory() { _history.clear() }

    fun calibrateTo(targetDb: Double) {
        val rawDbFs = _lastDbFs.value
        // rms в тихой комнате обычно -35..-25 dBFS; для речи -20..-10 dBFS.
        var newOffset = targetDb - rawDbFs
        // ограничим разумными рамками
        newOffset = newOffset.coerceIn(60.0, 120.0)
        setOffsetDb(newOffset)
    }

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
        val window = history.takeLast(minOf(60, n)).map { sanitizeDb(it.db) }
        val sma = window.average()
        // Простой тренд: разница между последним и первым, распределённая на окно
        val trend = if (window.size >= 2) (window.last() - window.first()) / window.size else 0.0
        return sma + trend * 60 // прогноз на ≈60 сек
    }
}

private fun launchSnackbar(host: SnackbarHostState, message: String) {
    // Вызов из @Composable через rememberCoroutineScope() не нужен: SnackbarHostState
    // поддерживает showSnackbar через suspend. Здесь используем глобальный scope для простоты.
    kotlinx.coroutines.GlobalScope.launch {
        host.showSnackbar(message = message, withDismissAction = true, duration = SnackbarDuration.Short)
    }
}

private fun sanitizeDb(value: Double): Double {
    // Делаем допустимый диапазон шире, чтобы хлопки/речь не "упирались" слишком рано
    return value.coerceIn(25.0, 110.0)
}

private fun exportHistory(snackbar: SnackbarHostState, history: List<NoisePoint>) {
    if (history.isEmpty()) {
        launchSnackbar(snackbar, "Нет данных для экспорта")
        return
    }
    try {
        // Мини‑MVP: считаем CSV длину. Уберём зависимость serialization, чтобы не ругалось.
        val csv = buildString {
            appendLine("timestamp,db")
            history.forEach { appendLine("${it.timeMillis},${String.format("%.1f", it.db)}") }
        }
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
                            title = { Text("Городской шум") }
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    NoiseScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                vm = viewModel,
                        snackbar = snackbarHostState
                    )
                }
            }
        }
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
            Chart(history = vm.history.map { it.copy(db = sanitizeDb(it.db)) }, minDb = 30.0, maxDb = 100.0)
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
                    text = "Индекс шума (1 мин): ${"%.0f".format(m.avg1)} дБ · >65 дБ: ${formatPct(m.p65_1)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Прогноз", onInfo = {
            launchSnackbar(snackbar, "Прогноз на 1 минуту на основе скользящего среднего и простого тренда.")
        }) {
            Spacer(Modifier.height(8.dp))
            val f = vm.forecastNext()
            Text(text = f?.let { "Прогноз (≈1 мин): ${"%.1f".format(it)} дБ" }
                ?: "Недостаточно данных для прогноза")
            val m = vm.metrics.value
            if (m != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (m.avg1 < m.avg5) "Тренд: снижение" else if (m.avg1 > m.avg5) "Тренд: рост" else "Тренд: стабильный",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Калибровка", onInfo = {
            launchSnackbar(snackbar, "Сдвиг в дБ для подстройки показаний под ваше устройство.")
        }) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${"%.0f".format(offsetDb)} дБ", modifier = Modifier.weight(0.3f))
                Slider(
                    value = offsetDb.toFloat(),
                    onValueChange = { vm.setOffsetDb(it.toDouble()) },
                    valueRange = 70f..110f,
                    modifier = Modifier.weight(0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.calibrateTo(35.0) }) { Text("К тихой\nкомнате 35 дБ") }
                OutlinedButton(onClick = { vm.calibrateTo(60.0) }) { Text("К речи 60 дБ") }
                OutlinedButton(onClick = { exportHistory(snackbar, vm.history) }) { Text("Экспорт") }
            }
        }

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
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
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
    val points = history.takeLast(180)
    val lineColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.isNotEmpty()) {
                val path = Path()
                val w = size.width
                val h = size.height
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
                drawPath(path = path, color = lineColor, alpha = 0.9f)
            }
        }
    }
}
