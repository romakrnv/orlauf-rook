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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
                // Surface задаёт фон и цвет текста по умолчанию, иначе Text рисуется чёрным.
                Surface(Modifier.fillMaxSize()) {
                    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
                    // Кого редактируем на Page.EDIT; null — новый пользователь.
                    var editId by rememberSaveable { mutableStateOf<String?>(null) }
                    val toMain = { page = Page.MAIN }
                    val edit = { id: String? -> editId = id; page = Page.EDIT }
                    when (page) {
                        Page.MAIN -> Screen(
                            vm,
                            onStats = { page = Page.STATS },
                            onUsers = { page = Page.USERS },
                            onEditCurrent = { edit(vm.currentId.value) },
                        )
                        Page.STATS -> StatsScreen(vm, onBack = toMain)
                        Page.USERS -> UsersScreen(vm, onBack = toMain, onEdit = edit)
                        Page.EDIT -> UserEditScreen(vm, editId, onDone = { page = Page.USERS })
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.saveStats()
    }
}

private enum class Page { MAIN, STATS, USERS, EDIT }

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
private fun Screen(vm: MainViewModel, onStats: () -> Unit, onUsers: () -> Unit, onEditCurrent: () -> Unit) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val favoriteId by vm.favoriteId.collectAsStateWithLifecycle()
    var confirmOff by remember { mutableStateOf(false) }
    // Переключение во время тренировки — только после подтверждения.
    var switchTo by remember { mutableStateOf<User?>(null) }

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
            TextButton(onClick = onStats) { Text("Stats") }
            TextButton(onClick = onUsers) { Text("Users") }
        }

        UserPicker(users, currentId, favoriteId, onSelect = { id ->
            if (id == currentId) return@UserPicker
            if (s.seconds > 0) switchTo = users.firstOrNull { it.id == id } else vm.selectUser(id)
        })

        if (s.needsSetup) {
            Text(
                "Укажите имя, вес и рост, иначе калории и шаги будут неточными. Нажмите, чтобы настроить.",
                color = Color(0xFFFFB74D),
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onEditCurrent),
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

        // Дошли до точки маршрута — карточка с цитатой, по одной.
        AnimatedContent(
            targetState = s.milestones.firstOrNull(),
            transitionSpec = { fadeIn(tween(ANIM_MS)) togetherWith fadeOut(tween(ANIM_MS)) },
            label = "milestone",
        ) { m -> if (m != null) MilestoneCard(m, onDismiss = vm::dismissMilestone) }

        s.message?.let {
            Text(it, color = Color(0xFFFFB74D), modifier = Modifier.fillMaxWidth())
            TextButton(onClick = vm::dismissMessage) { Text("Hide") }
        }

        val ready = s.link == Link.READY
        // Connect ↔ панель управления: плавная смена вместо скачка.
        Crossfade(targetState = ready, animationSpec = tween(ANIM_MS), label = "controls") { isReady ->
            if (!isReady) {
                Button(
                    onClick = { permLauncher.launch(requiredPermissions()) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text("Connect", fontSize = 20.sp) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmoothButton("− 0.1", vm::slower, enabled = s.canChangeSpeed,
                            modifier = Modifier.weight(1f).height(72.dp), fontSize = 24.sp)
                        SmoothButton("+ 0.1", vm::faster, enabled = s.canChangeSpeed,
                            modifier = Modifier.weight(1f).height(72.dp), fontSize = 24.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Одно место на две кнопки: стоит — Start, едет — Power off.
                        val label = when (s.pending) {
                            BeltCommand.START -> "Starting… ${s.pendingLeft}"
                            BeltCommand.POWER_OFF -> "Stopping… ${s.pendingLeft}"
                            null -> if (s.beltMoving) "Power off" else "Start"
                        }
                        val offSlot = s.pending == BeltCommand.POWER_OFF || (s.pending == null && s.beltMoving)
                        // Приглушённый красный из темы: заметно, но не выбивается из остального.
                        val container by animateColorAsState(
                            if (offSlot) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            tween(ANIM_MS), label = "slot",
                        )
                        val content by animateColorAsState(
                            if (offSlot) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                            tween(ANIM_MS), label = "slotText",
                        )
                        SmoothButton(
                            label,
                            onClick = { if (s.beltMoving) confirmOff = true else vm.start() },
                            enabled = s.canStart || s.canPowerOff,
                            containerColor = container,
                            contentColor = content,
                            modifier = Modifier.weight(1f).height(56.dp),
                        )
                        SmoothButton(
                            if (s.paused) "Resume" else "Pause",
                            vm::togglePause,
                            enabled = s.beltMoving,
                            modifier = Modifier.weight(1f).height(56.dp),
                        )
                    }
                }
            }
        }
    }

    switchTo?.let { u ->
        val current = users.firstOrNull { it.id == currentId }
        AlertDialog(
            onDismissRequest = { switchTo = null },
            title = { Text("Сменить на «${u.name}»?") },
            text = { Text("Текущая тренировка сохранится за «${current?.name ?: ""}», счётчики обнулятся.") },
            confirmButton = {
                TextButton(onClick = { switchTo = null; vm.selectUser(u.id) }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { switchTo = null }) { Text("Cancel") } },
        )
    }

    // Лента остановилась, пока был открыт диалог, — закрываем его.
    LaunchedEffect(s.canPowerOff) { if (!s.canPowerOff) confirmOff = false }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Выключить дорожку?") },
            // Крупные кнопки на всю ширину: нажимать приходится на ходу.
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { confirmOff = false },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Cancel", fontSize = 18.sp) }
                    Button(
                        onClick = { confirmOff = false; vm.powerOff() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Power off", fontSize = 18.sp) }
                }
            },
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
