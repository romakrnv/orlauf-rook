package com.orlauf.rook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val JourneyBg = Color(0xFF1E1E1E)
private val Done = Color(0xFF81C784)

/** Сколько карточка с цитатой висит на главном экране, если её не закрыли. */
private const val MILESTONE_SHOW_MS = 20_000L

/** Карточка «вы дошли до …» с цитатой. Не модальная: кнопки управления остаются доступны. */
@Composable
fun MilestoneCard(m: Reached, onDismiss: () -> Unit) {
    LaunchedEffect(m) {
        delay(MILESTONE_SHOW_MS)
        onDismiss()
    }
    val routeIndex = Journey.ROUTES.indexOf(m.route)
    val title = when {
        m.stop == m.route.stops.last() -> "Маршрут «${m.route.title}» пройден!"
        m.stop.km == 0.0 && routeIndex > 0 -> "Открыт маршрут «${m.route.title}»"
        else -> "Вы дошли: ${m.stop.name}"
    }
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${m.route.emoji}  $title", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK") }
        }
        Text("«${m.stop.quote}»", fontStyle = FontStyle.Italic, fontSize = 15.sp)
        Text(m.route.source, fontSize = 12.sp, color = Color.Gray)
    }
}

/** Блок внизу статистики: пройденные маршруты, текущий и прогресс. Будущие не показываем. */
@Composable
fun JourneySection(state: JourneyState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Путешествие", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        state.completed.forEach { r ->
            Row(
                Modifier.fillMaxWidth().background(JourneyBg, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${r.emoji}  ${r.title}", modifier = Modifier.weight(1f))
                Text("✓ ${km0(r.lengthKm)} км", color = Done, fontSize = 14.sp)
            }
        }

        val r = state.current
        if (r == null) {
            Text("Все маршруты пройдены 🎉", color = Done)
            return@Column
        }
        Column(
            Modifier.fillMaxWidth().background(JourneyBg, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.emoji}  ${r.title}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(r.source, fontSize = 12.sp, color = Color.Gray)
            }
            LinearProgressIndicator(
                progress = { state.routeProgress.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text("${km1(state.kmInRoute)} из ${km0(r.lengthKm)} км", fontSize = 13.sp, color = Color.Gray)

            state.lastStop?.let {
                Text("Сейчас: ${it.name}", fontWeight = FontWeight.SemiBold)
                Text("«${it.quote}»", fontStyle = FontStyle.Italic, fontSize = 14.sp)
            }
            state.nextStop?.let {
                Text("Дальше: ${it.name} — через ${km1(state.kmToNext)} км", fontSize = 14.sp, color = Color.Gray)
            }
        }
        if (Journey.ROUTES.last() != r) {
            Text("🔒 Следующий маршрут откроется после финиша", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

private fun km0(v: Double) = "%,.0f".format(v)
private fun km1(v: Double) = "%,.1f".format(v)
