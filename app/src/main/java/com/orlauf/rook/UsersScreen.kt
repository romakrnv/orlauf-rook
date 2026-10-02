package com.orlauf.rook

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

private val Gold = Color(0xFFFFC107)

/** Эмодзи на цветном кружке. Выбранный обведён рамкой. */
@Composable
fun Avatar(emoji: String, color: Long, size: Dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .padding(if (selected) 5.dp else 0.dp)
            .background(Color(color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.42f).sp)
    }
}

/**
 * Ряд аватарок с именами. Звёздочка — избранный (выбирается при запуске).
 * Используется на главном экране (кто тренируется) и в статистике (чью смотрим).
 */
@Composable
fun UserPicker(users: List<User>, selectedId: String, favoriteId: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        users.forEach { u ->
            Column(
                Modifier.width(72.dp).clickable { onSelect(u.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Avatar(u.emoji, u.color, 60.dp, selected = u.id == selectedId)
                    if (u.id == favoriteId) {
                        Text("★", color = Gold, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
                Text(
                    u.name,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (u.id == selectedId) Color.Unspecified else Color.Gray,
                )
            }
        }
    }
}

@Composable
fun UsersScreen(vm: MainViewModel, onBack: () -> Unit, onEdit: (userId: String?) -> Unit) {
    BackHandler(onBack = onBack)
    val users by vm.users.collectAsStateWithLifecycle()
    val favoriteId by vm.favoriteId.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("Пользователи", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("★ — избранный, выбирается при запуске приложения.", color = Color.Gray, fontSize = 13.sp)

        users.forEach { u ->
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(u.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Avatar(u.emoji, u.color, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(u.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${num(u.profile.weightKg)} кг · ${num(u.profile.heightCm)} см", fontSize = 13.sp, color = Color.Gray)
                }
                TextButton(onClick = { vm.toggleFavorite(u.id) }) {
                    Text(if (u.id == favoriteId) "★" else "☆", fontSize = 26.sp, color = if (u.id == favoriteId) Gold else Color.Gray)
                }
            }
        }

        OutlinedButton(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("+ Add user")
        }

        BackupSection(vm)
    }
}

/**
 * Резервная копия. Автоматически Android сам сохраняет данные в Google-аккаунт (allowBackup).
 * Вручную — файл через системное окно выбора: можно сразу в Google Диск.
 */
@Composable
private fun BackupSection(vm: MainViewModel) {
    val status by vm.backupStatus.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Резервная копия", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Автоматически: Android раз в сутки сохраняет данные в Google-аккаунт " +
                "(если в настройках телефона включено резервное копирование Google). " +
                "Вручную: файл со всеми пользователями и статистикой — можно сохранить в Google Диск.",
            fontSize = 13.sp,
            color = Color.Gray,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("rook-backup-${LocalDate.now()}.json") },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Export") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Import") }
        }
        status?.let { Text(it, fontSize = 13.sp) }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Восстановить из копии?") },
            text = { Text("Все текущие пользователи и статистика будут заменены данными из файла.") },
            confirmButton = { TextButton(onClick = { pendingImport = null; vm.importBackup(uri) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

/** Создание (userId == null) или редактирование пользователя. */
@Composable
fun UserEditScreen(vm: MainViewModel, userId: String?, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val users by vm.users.collectAsStateWithLifecycle()
    val original = remember(userId) { users.firstOrNull { it.id == userId } ?: UserRules.newUser(users) }

    var name by rememberSaveable { mutableStateOf(original.name) }
    var emoji by rememberSaveable { mutableStateOf(original.emoji) }
    var color by rememberSaveable { mutableLongStateOf(original.color) }
    var weight by rememberSaveable { mutableStateOf(num(original.profile.weightKg)) }
    var height by rememberSaveable { mutableStateOf(num(original.profile.heightCm)) }
    var stride by rememberSaveable { mutableStateOf(original.profile.strideCm?.let(::num) ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val profile = Profile.parse(weight, height, stride)
    val canSave = name.isNotBlank() && profile != null
    val isNew = users.none { it.id == original.id }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (isNew) "Новый пользователь" else "Пользователь",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text("Cancel") }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(emoji, color, 96.dp) }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("Имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Аватар", color = Color.Gray, fontSize = 13.sp)
        Avatars.EMOJI.chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { e ->
                    Box(
                        Modifier.size(40.dp).clickable { emoji = e }
                            .then(if (e == emoji) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 22.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Avatars.COLORS.forEach { c ->
                Box(
                    Modifier.size(36.dp)
                        .then(if (c == color) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                        .padding(4.dp)
                        .background(Color(c), CircleShape)
                        .clickable { color = c },
                )
            }
        }

        Text("Для расчёта калорий и шагов", color = Color.Gray, fontSize = 13.sp)
        NumberField("Вес, кг", weight, { weight = it }, Profile.WEIGHT_RANGE)
        NumberField("Рост, см", height, { height = it }, Profile.HEIGHT_RANGE)
        NumberField("Длина шага, см", stride, { stride = it }, Profile.STRIDE_RANGE, optional = true)
        val autoStride = Profile.parse(weight, height, "")?.let { num(it.strideM * 100) }
        Text(
            "Пусто — считается по росту" + (autoStride?.let { " ($it см)" } ?: "") +
                ". Точнее: пройдите 100 шагов и разделите дистанцию на 100.",
            fontSize = 13.sp,
            color = Color.Gray,
        )

        Button(
            onClick = {
                profile?.let { vm.saveUser(original.copy(name = name.trim(), emoji = emoji, color = color, profile = it)) }
                onDone()
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Save") }

        if (!isNew && users.size > 1) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete user", color = Color(0xFFEF5350))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${original.name}»?") },
            text = { Text("Вся статистика этого пользователя будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteUser(original.id); onDone() }) {
                    Text("Delete", color = Color(0xFFEF5350))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    optional: Boolean = false,
) {
    val n = value.trim().replace(',', '.').toDoubleOrNull()
    val error = !(optional && value.isBlank()) && (n == null || n !in range)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error,
        supportingText = if (error) {
            { Text("от ${num(range.start)} до ${num(range.endInclusive)}") }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 92.0 -> "92", 78.3 -> "78.3". */
private fun num(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}
