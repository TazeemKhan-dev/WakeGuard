package com.wakeguard.alarm.ui.ringing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wakeguard.alarm.awake.AwakeCheckManager as A
import com.wakeguard.alarm.ui.theme.ThemeMode
import com.wakeguard.alarm.ui.theme.WakeGuardTheme
import kotlinx.coroutines.delay

class AwakeCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("alarm_id", 0)
        val token = intent.getStringExtra(A.EXTRA_TOKEN) ?: ""
        setContent {
            WakeGuardTheme(ThemeMode.SYSTEM) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(id, token) {
                    while (true) { now = System.currentTimeMillis(); delay(250) }
                }
                val check = A.matches(this, id, token)
                if (check == null || now >= check.deadlineAt) {
                    LaunchedEffect(check, now >= (check?.deadlineAt ?: 0L)) { finish() }
                } else {
                    val seconds = ((check.deadlineAt - now + 999) / 1000).coerceAtLeast(0)
                    Column(Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text("Are you still awake?", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(20.dp))
                        Text("${seconds / 60}:%02d".format(seconds % 60), style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            sendBroadcast(Intent(this@AwakeCheckActivity, com.wakeguard.alarm.receiver.AwakeCheckReceiver::class.java).apply {
                                action = A.ACTION_CONFIRM
                                putExtra("alarm_id", id)
                                putExtra(A.EXTRA_TOKEN, token)
                            })
                            finish()
                        }) { Text("I'm Awake") }
                    }
                }
            }
        }
    }
}
