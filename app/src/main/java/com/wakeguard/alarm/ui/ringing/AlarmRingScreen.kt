
package com.wakeguard.alarm.ui.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.ringing.components.SwipeUpControl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SnoozeBlue = Color(0xFF6385FF)

@Composable
fun AlarmRingScreen(
    label: String,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val time = remember {
        SimpleDateFormat(
            "h:mm",
            Locale.getDefault()
        ).format(Date())
    }

    val date = remember {
        SimpleDateFormat(
            "EEEE, d MMMM",
            Locale.getDefault()
        ).format(Date())
    }

    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF071631),
            Color(0xFF162945),
            Color(0xFF46515E)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1.2f))

        Text(
            text = time,
            color = Color.White,
            fontSize = 74.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = date,
            color = Color(0xFFADB8CA),
            fontSize = 17.sp
        )

        Spacer(Modifier.weight(0.65f))

        Text(
            text = label,
            color = Color.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSnooze,
            modifier = Modifier
                .width(240.dp)
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = SnoozeBlue,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(
                horizontal = 18.dp,
                vertical = 8.dp
            )
        ) {
            Text(
                text = "☾",
                fontSize = 25.sp,
                color = Color.White
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = "Snooze · $snoozeMinutes min",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(Modifier.weight(1.8f))

        SwipeUpControl(
            onSwipeComplete = onDismiss
        )

        Spacer(Modifier.height(32.dp))
    }
}
