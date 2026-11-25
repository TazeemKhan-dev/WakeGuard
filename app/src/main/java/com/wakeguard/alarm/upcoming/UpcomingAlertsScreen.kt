package com.wakeguard.alarm.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun UpcomingAlertsScreen(alerts: List<UpcomingAlert>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    fun resolve(items: List<UpcomingAlert>) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                items.sortedBy { if (it is UpcomingAlert.Snooze) 0 else 1 }.forEach { UpcomingAlertsManager.resolve(context.applicationContext, it) }
                message = "Alert list refreshed. Alerts already ringing cannot be cancelled here."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = "Could not complete every action. Please check the remaining alerts." }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().background(W.background).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("‹ Back", color = W.primary) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }, enabled = alerts.isNotEmpty() && !busy) { Text("Clear all") }
        }
        Text("Upcoming Alerts", color = W.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (alerts.size == 1) "1 pending alert" else "${alerts.size} pending alerts", color = W.secondary)
        Spacer(Modifier.height(12.dp))
        message?.let { Text(it, color = W.secondary, modifier = Modifier.padding(vertical = 8.dp)) }
        if (alerts.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).background(W.card, CircleShape), contentAlignment = Alignment.Center) {
                        val ink = W.primary
                        Canvas(Modifier.size(40.dp)) {
                            val stroke = 2.dp.toPx()
                            drawCircle(ink, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                            drawLine(ink, center, center.copy(y = center.y - size.height / 4), stroke)
                            drawLine(ink, center, center.copy(x = center.x + size.width / 5), stroke)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("You're all caught up", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = W.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Pending snoozes and Awake Checks will appear here.", color = W.secondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(onClick = onBack) { Text("Back to alarms") }
                }
            }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            items(alerts, key = { it.key }) { alert ->
                val awake = alert as? UpcomingAlert.Awake
                val awaiting = awake != null && now >= awake.record.promptAt
                val target = if (awaiting) awake!!.record.deadlineAt else alert.scheduledAt
                val seconds = ((target - now + 999L) / 1000L).coerceAtLeast(0L)
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = W.card), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (awake == null) "Snooze" else if (awaiting) "Awake Check · Awaiting response" else "Awake Check", color = W.primary)
                        Text(alert.name, style = MaterialTheme.typography.titleLarge, color = W.text)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(target)), color = W.secondary)
                        Text("%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60), fontSize = 38.sp, fontWeight = FontWeight.SemiBold, color = W.text)
                        Text(if (awaiting) "Until alarm rings again" else if (awake != null) "Until Awake Check" else "Until next ring", color = W.secondary)
                        OutlinedButton(onClick = { resolve(listOf(alert)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (awake == null) "Cancel snooze" else "I'm Awake", color = W.primary)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear pending alerts?") }, text = { Text("Cancel all listed snoozes and confirm all listed Awake Checks. Linked alarms may be skipped for the current wake session. Future repeating alarms remain enabled.") }, confirmButton = { TextButton(onClick = { confirmClear = false; resolve(alerts.toList()) }) { Text("Clear all") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
