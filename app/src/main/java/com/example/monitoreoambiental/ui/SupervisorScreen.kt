package com.example.monitoreoambiental.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.monitoreoambiental.data.EventRecord
import com.example.monitoreoambiental.data.GeoEdgeDatabase
import com.example.monitoreoambiental.data.RemoteNodeRepository
import com.example.monitoreoambiental.domain.GeoEdgeEngine
import com.example.monitoreoambiental.model.AlertLevel
import com.example.monitoreoambiental.model.ConnectionStatus
import kotlin.math.abs

// CAPA C: MODO SUPERVISOR (equipo en superficie).
// Se conecta al nodo por TCP, replica el semaforo, guarda su propio
// historial en SQLite y envia comandos de vuelta al nodo.
@Composable
fun SupervisorScreen(
    user: String,
    state: DevicesUiState,
    repository: RemoteNodeRepository,
    database: GeoEdgeDatabase,
    onLogout: () -> Unit
) {
    val status by repository.status.collectAsState()
    val device = state.devices.firstOrNull()
    val reading = device?.reading
    val connected = device?.connection == ConnectionStatus.CONNECTED
    val level = reading?.let { GeoEdgeEngine.evaluate(it) }

    var ip by remember { mutableStateOf("") }
    var events by remember { mutableStateOf(listOf<EventRecord>()) }
    var lastLevel by remember { mutableStateOf<AlertLevel?>(null) }

    val refresh: () -> Unit = { events = database.recentEvents(25) }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(Unit) { onDispose { repository.disconnect() } }

    // Se guarda solo el cambio de nivel, no cada lectura
    LaunchedEffect(level) {
        if (level != null && level != lastLevel) {
            lastLevel = level
            reading?.let {
                database.saveEvent(
                    it.vibration, it.distanceCm, it.baselineCm,
                    level, "SUPERVISOR", it.measuredAtMillis
                )
            }
            refresh()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("MODO SUPERVISOR", style = MaterialTheme.typography.headlineSmall)
            Text("Sesión: $user (rol SUPERVISOR)")

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP del nodo") },
                singleLine = true,
                enabled = !connected,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (ip.isNotBlank()) repository.connect(ip.trim()) },
                    enabled = !connected
                ) { Text("Conectar") }

                Button(
                    onClick = { repository.disconnect() },
                    enabled = connected
                ) { Text("Desconectar") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)

            TrafficLight(level)
            Text(
                if (level != null) GeoEdgeEngine.label(level) else "SIN ENLACE",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (level != null) GeoEdgeEngine.description(level)
                else "Esperando datos del frente."
            )

            if (reading != null) {
                Text(
                    "Vibración: ${reading.vibration}\n" +
                        "Distancia: ${reading.distanceCm} cm\n" +
                        "Base: ${reading.baselineCm} cm\n" +
                        "Desviación: ${abs(reading.distanceCm - reading.baselineCm)} cm"
                )
            }

            Text("Comandos remotos", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { repository.silenceSiren() },
                    enabled = connected
                ) { Text("Silenciar sirena") }

                Button(
                    onClick = { repository.recalibrate() },
                    enabled = connected
                ) { Text("Recalibrar base") }
            }

            Text("Historial (SQLite)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (events.isEmpty()) "Sin eventos registrados."
                else events.joinToString("\n") { e ->
                    "${e.formattedTime()}  ${e.level}  vib ${e.vibration}  " +
                        "dist ${e.distanceCm} cm  (${e.origin})"
                },
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = {
                repository.disconnect()
                onLogout()
            }) { Text("Cerrar sesión") }
        }
    }
}
