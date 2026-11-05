package com.example.monitoreoambiental.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.monitoreoambiental.model.AlertLevel

// CAPA C: semaforo compartido por la pantalla del nodo y la del supervisor.
// level = null significa "sin datos": las tres luces quedan apagadas.
@Composable
fun TrafficLight(level: AlertLevel?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Light(Color(0xFF2E7D32), level == AlertLevel.NORMAL)
        Light(Color(0xFFF9A825), level == AlertLevel.WARNING)
        Light(Color(0xFFC62828), level == AlertLevel.CRITICAL)
    }
}

@Composable
private fun Light(color: Color, active: Boolean) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                if (active) color else color.copy(alpha = 0.2f),
                CircleShape
            )
    )
}
