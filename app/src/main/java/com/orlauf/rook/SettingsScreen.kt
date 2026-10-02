package com.orlauf.rook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val saved by vm.profile.collectAsStateWithLifecycle()

    var weight by rememberSaveable { mutableStateOf(num(saved.weightKg)) }
    var height by rememberSaveable { mutableStateOf(num(saved.heightCm)) }
    var stride by rememberSaveable { mutableStateOf(saved.strideCm?.let(::num) ?: "") }

    val parsed = Profile.parse(weight, height, stride)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("Настройки", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Назад") }
        }
        Text("Нужны для расчёта калорий и шагов. Хранятся только на телефоне.", color = Color.Gray)

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
            onClick = { parsed?.let { vm.saveProfile(it); onBack() } },
            enabled = parsed != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Сохранить") }
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

/** 92.0 -> "92", 78.3 -> "78.3" (без хвостов от Float из SharedPreferences). */
private fun num(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}
