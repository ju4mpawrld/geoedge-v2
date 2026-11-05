package com.example.monitoreoambiental.data

import com.example.monitoreoambiental.model.ConnectionStatus
import com.example.monitoreoambiental.model.Device
import com.example.monitoreoambiental.net.LinkProtocol
import com.example.monitoreoambiental.net.SupervisorClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

// CAPA C: modo SUPERVISOR. Cumple el mismo contrato DeviceRepository que
// LocalNodeRepository, pero los datos no nacen aqui: llegan por la red.
class RemoteNodeRepository : DeviceRepository {

    private val state = MutableStateFlow(
        Device(
            id = "NODO-01",
            name = "Frente de avance 01",
            location = "Nivel -320",
            connection = ConnectionStatus.DISCONNECTED,
            reading = null
        )
    )

    private val _status = MutableStateFlow("Sin enlace. Escriba la IP del nodo.")
    val status: StateFlow<String> = _status.asStateFlow()

    private var client: SupervisorClient? = null

    override fun observeDevices(): Flow<List<Device>> =
        state.map { listOf(it) }

    fun connect(ip: String) {
        client?.close()
        client = SupervisorClient(
            onReading = { reading ->
                state.value = state.value.copy(
                    connection = ConnectionStatus.CONNECTED,
                    reading = reading
                )
            },
            onLog = { _status.value = it },
            onConnectionChanged = { connected ->
                state.value = state.value.copy(
                    connection = if (connected) ConnectionStatus.CONNECTED
                    else ConnectionStatus.DISCONNECTED
                )
            }
        )
        client?.connect(ip)
    }

    fun silenceSiren() = client?.sendCommand(LinkProtocol.CMD_SILENCE)

    fun recalibrate() = client?.sendCommand(LinkProtocol.CMD_RECALIBRATE)

    fun disconnect() {
        client?.close()
        client = null
        state.value = state.value.copy(connection = ConnectionStatus.DISCONNECTED)
    }
}
