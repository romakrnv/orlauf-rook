package com.orlauf.rook

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var page by rememberSaveable { mutableStateOf(Page.MAIN) }
                val toMain = { page = Page.MAIN }
                when (page) {
                    Page.MAIN -> Screen(vm, onStats = { page = Page.STATS }, onSettings = { page = Page.SETTINGS })
                    Page.STATS -> StatsScreen(vm, onBack = toMain)
                    Page.SETTINGS -> SettingsScreen(vm, onBack = toMain)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.saveStats()
    }
}

private enum class Page { MAIN, STATS, SETTINGS }

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
private fun Screen(vm: MainViewModel, onStats: () -> Unit, onSettings: () -> Unit) {
    val s by vm.ui.collectAsStateWithLifecycle()
    var confirmOff by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> if (granted.values.all { it }) vm.connect() }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = when (s.link) {
                    Link.IDLE -> "Не подключено"
                    Link.SCANNING -> "Поиск дорожки…"
                    Link.CONNECTING -> "Подключение…"
                    Link.READY -> if (s.paused) "Пауза (1,0 км/ч)" else "Подключено"
                    Link.LOST -> "Связь потеряна"
                },
                fontSize = 16.sp,
                color = Color.Gray,
            )
            TextButton(onClick = onStats) { Text("Статистика") }
            TextButton(onClick = onSettings) { Text("Настройки") }
        }

        if (s.profileMissing) {
            Text(
                "Укажите вес и рост в настройках, иначе калории и шаги будут неточными.",
                color = Color(0xFFFFB74D),
                fontSize = 14.sp,
            )
        }

        Metric("Скорость, км/ч", "%.1f".format(s.speedKmh), 88)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { Metric("Дистанция, км", "%.2f".format(s.distanceM / 1000), 44) }
            Column(Modifier.weight(1f)) { Metric("Время", formatTime(s.seconds), 44) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { Metric("Шаги", s.steps.toString(), 44) }
            Column(Modifier.weight(1f)) { Metric("Ккал", "%.0f".format(s.kcal), 44) }
        }

        Spacer(Modifier.weight(1f, fill = true))

        s.message?.let {
            Text(it, color = Color(0xFFFFB74D), modifier = Modifier.fillMaxWidth())
            TextButton(onClick = vm::dismissMessage) { Text("Скрыть") }
        }

        val ready = s.link == Link.READY
        if (!ready) {
            Button(
                onClick = { permLauncher.launch(requiredPermissions()) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Подключиться", fontSize = 20.sp) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::slower, enabled = !s.paused,
                    modifier = Modifier.weight(1f).height(72.dp)) { Text("− 0,1", fontSize = 24.sp) }
                Button(onClick = vm::faster, enabled = !s.paused,
                    modifier = Modifier.weight(1f).height(72.dp)) { Text("+ 0,1", fontSize = 24.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::start, modifier = Modifier.weight(1f).height(56.dp)) { Text("Старт") }
                Button(onClick = vm::togglePause, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(if (s.paused) "Продолжить" else "Пауза")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::resetSession, modifier = Modifier.weight(1f)) { Text("Сброс") }
                Button(
                    onClick = { confirmOff = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    modifier = Modifier.weight(1f),
                ) { Text("Выключить") }
            }
        }
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Выключить дорожку?") },
            text = { Text("Дорожка отключится полностью. Включить её снова можно только кнопкой на самой дорожке.") },
            confirmButton = {
                TextButton(onClick = { confirmOff = false; vm.powerOff() }) { Text("Выключить") }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Metric(label: String, value: String, sizeSp: Int) {
    Column {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text(value, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
