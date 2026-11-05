package com.example.monitoreoambiental.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.monitoreoambiental.data.GeoEdgeDatabase
import com.example.monitoreoambiental.data.LocalNodeRepository
import com.example.monitoreoambiental.domain.GeoEdgeEngine
import com.example.monitoreoambiental.model.AlertLevel
import com.example.monitoreoambiental.net.LinkProtocol
import com.example.monitoreoambiental.net.NodeServer
import com.example.monitoreoambiental.net.localIpAddresses
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// CAPA C: MODO NODO (equipo del tunel).
// Decide localmente (sin red), acciona la sirena, guarda eventos en SQLite
// y publica el estado a superficie por TCP.
@Composable
fun NodeScreen(
    user: String,
    state: DevicesUiState,
    repository: LocalNodeRepository,
    database: GeoEdgeDatabase,
    onLogout: () -> Unit
) {
    val reading = state.devices.firstOrNull()?.reading
    val level = reading?.let { GeoEdgeEngine.evaluate(it) } ?: AlertLevel.NORMAL

    var vibration by remember { mutableFloatStateOf(0f) }
    var distance by remember { mutableFloatStateOf(20f) }
    var sirenMuted by remember { mutableStateOf(false) }
    var clientCount by remember { mutableStateOf(0) }
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var lastLevel by remember { mutableStateOf<AlertLevel?>(null) }

    val addLog: (String) -> Unit = { text ->
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logLines = (listOf("[$time] $text") + logLines).take(30)
    }

    // Servidor: se crea una vez y se detiene al salir de la pantalla
    val server = remember {
        NodeServer(
            onLog = { addLog(it) },
            onCommand = { action ->
                when (action) {
                    LinkProtocol.CMD_SILENCE -> {
                        sirenMuted = true
                        addLog("COMANDO REMOTO: sirena silenciada desde superficie")
                    }
                    LinkProtocol.CMD_RECALIBRATE -> {
                        repository.calibrateBaseline()
                        addLog("COMANDO REMOTO: base recalibrada desde superficie")
                    }
                    else -> addLog("Comando desconocido, descartado")
                }
            },
            onClientCountChanged = { clientCount = it }
        )
    }
    DisposableEffect(Unit) {
        server.start()
        onDispose { server.stop() }
    }

    // Sirena
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { toneGenerator?.release() } catch (e: Exception) { } }
    }
    LaunchedEffect(level, sirenMuted) {
        while (level == AlertLevel.CRITICAL && !sirenMuted) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
            } catch (e: Exception) {
            }
            delay(700)
        }
    }

    // Cada cambio de nivel se guarda en SQLite y se anota en el registro
    LaunchedEffect(level) {
        if (lastLevel != level) {
            lastLevel = level
            sirenMuted = false
            reading?.let {
                database.saveEvent(
                    it.vibration, it.distanceCm, it.baselineCm, level, "NODO"
                )
            }
            addLog("Nivel → ${GeoEdgeEngine.label(level)}")
        }
    }

    // Envia el estado a superficie con cada lectura nueva y tambien cuando se
    // conecta un supervisor (cambia clientCount). El delay de 120 ms evita
    // mandar un mensaje por cada pixel que se arrastra el deslizador.
    LaunchedEffect(reading, clientCount) {
        delay(120)
        reading?.let { server.broadcast(LinkProtocol.encodeState(it)) }
    }

    val ipAddresses = remember { localIpAddresses() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("MODO NODO", style = MaterialTheme.typography.headlineSmall)
            Text("Sesión: $user (rol NODO)")
            Text(
                if (ipAddresses.isEmpty()) "Sin red activa."
                else "IP de este nodo: ${ipAddresses.joinToString(", ")}" +
                    "  ·  puerto ${LinkProtocol.PORT}"
            )
            Text("Supervisores conectados: $clientCount")

            TrafficLight(level)
            Text(
                GeoEdgeEngine.label(level),
                style = MaterialTheme.typography.titleLarge
            )
            Text(GeoEdgeEngine.description(level))

            Text("Vibración: ${vibration.toInt()} (umbral 512)")
            Slider(
                value = vibration,
                onValueChange = { value ->
                    vibration = value
                    repository.updateSensors(vibration.toInt(), distance.toInt())
                },
                valueRange = 0f..1023f
            )

            Text("Distancia: ${distance.toInt()} cm")
            Slider(
                value = distance,
                onValueChange = { value ->
                    distance = value
                    repository.updateSensors(vibration.toInt(), distance.toInt())
                },
                valueRange = 0f..100f
            )

            if (reading != null) {
                Text(
                    "Base: ${reading.baselineCm} cm · desviación: " +
                        "${abs(reading.distanceCm - reading.baselineCm)} cm"
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    repository.calibrateBaseline()
                    addLog("Base recalibrada localmente")
                }) { Text("Calibrar base") }

                Button(onClick = {
                    sirenMuted = !sirenMuted
                    addLog(if (sirenMuted) "Sirena silenciada" else "Sirena reactivada")
                }) { Text(if (sirenMuted) "Reactivar sirena" else "Silenciar sirena") }
            }

            Text("Registro de eventos", style = MaterialTheme.typography.titleMedium)
            Text(
                if (logLines.isEmpty()) "Sin eventos todavía."
                else logLines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = onLogout) { Text("Cerrar sesión") }
        }
    }
}
