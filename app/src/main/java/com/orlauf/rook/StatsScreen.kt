package com.orlauf.rook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Accent = Color(0xFF64B5F6)
private val CardBg = Color(0xFF1E1E1E)
private val DateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val stats = remember(workouts, period, today) { Stats.period(workouts, period, today, zone) }
    val streaks = remember(workouts, today) { Stats.streaks(Stats.byDay(workouts, zone).keys, today) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("Статистика", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Назад") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) { Metric("Стрик сейчас", days(streaks.current)) }
            Card(Modifier.weight(1f)) { Metric("Лучший стрик", days(streaks.longest)) }
        }

        TabRow(selectedTabIndex = period.ordinal) {
            Period.entries.forEach { p ->
                Tab(selected = p == period, onClick = { period = p }, text = { Text(p.title, fontSize = 13.sp) })
            }
        }

        if (stats.workouts == 0) {
            Text("За этот период тренировок нет.", color = Color.Gray)
            return@Column
        }

        Card {
            BarChart(stats.bars)
        }

        Section("Итого") {
            Grid(
                "Дистанция, км" to km(stats.distanceM),
                "Время" to duration(stats.seconds),
                "Шаги" to "%,d".format(stats.steps),
                "Ккал" to "%.0f".format(stats.kcal),
                "Тренировок" to stats.workouts.toString(),
                "Активных дней" to "${stats.activeDays} из ${stats.calendarDays}",
            )
        }

        Section("В среднем за день") {
            Grid(
                "Дистанция, км" to km(stats.avgDistancePerDay),
                "Шаги" to "%,d".format(stats.avgStepsPerDay),
            )
        }

        Section("В среднем за тренировку") {
            Grid(
                "Дистанция, км" to km(stats.avgDistancePerWorkout),
                "Время" to duration(stats.avgSecondsPerWorkout),
                "Скорость, км/ч" to "%.1f".format(stats.avgSpeedKmh),
            )
        }

        Section("Рекорды") {
            stats.bestDay?.let { Record("Лучший день", "${km(it.distanceM)} км", it.date.format(DateFmt)) }
            stats.longest?.let {
                Record("Самая длинная тренировка", duration(it.seconds), Stats.dayOf(it, zone).format(DateFmt))
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.background(CardBg, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth(), content)
    }
}

/** Пары «подпись — значение» в две колонки. */
@Composable
private fun Grid(vararg items: Pair<String, String>) {
    items.toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (label, value) -> Column(Modifier.weight(1f)) { Metric(label, value) } }
            if (row.size == 1) Column(Modifier.weight(1f)) {}
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Record(label: String, value: String, date: String) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = Color.Gray)
            Text(date, fontSize = 13.sp, color = Color.Gray)
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Столбики дистанции. При большом числе столбиков подписываем не каждый. */
@Composable
private fun BarChart(bars: List<Bar>) {
    val max = bars.maxOfOrNull { it.distanceM }?.takeIf { it > 0 } ?: 1.0
    val labelEvery = if (bars.size > 12) 5 else 1
    Text("Дистанция, км · максимум ${km(max)}", fontSize = 13.sp, color = Color.Gray)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / bars.size
        val barW = slot * 0.7f
        bars.forEachIndexed { i, b ->
            val h = (b.distanceM / max * size.height).toFloat().coerceAtLeast(if (b.distanceM > 0) 2f else 0f)
            drawRoundRect(
                color = Accent,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        bars.forEachIndexed { i, b ->
            val show = labelEvery == 1 || i == 0 || (i + 1) % labelEvery == 0
            Text(
                if (show) b.label else "",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun km(m: Double) = "%.2f".format(m / 1000)

private fun duration(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

private fun days(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "дней"
        n % 10 == 1 -> "день"
        n % 10 in 2..4 -> "дня"
        else -> "дней"
    }
    return "$n $word"
}
