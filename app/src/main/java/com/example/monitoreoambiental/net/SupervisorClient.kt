package com.example.monitoreoambiental.net

import com.example.monitoreoambiental.data.Security
import com.example.monitoreoambiental.model.SensorReading
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SupervisorClient
 *
 * Lado superficie del enlace. Se conecta al nodo del tunel, presenta la clave
 * precompartida y queda recibiendo estados en tiempo real. Tambien envia
 * comandos de vuelta al nodo.
 */
class SupervisorClient(
    private val onReading: (SensorReading) -> Unit,
    private val onLog: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) {
    private var socket: Socket? = null

    @Volatile
    private var running = false

    fun connect(ip: String, port: Int = LinkProtocol.PORT) {
        if (running) return
        running = true
        Thread {
            try {
                onLog("Conectando a $ip:$port ...")
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 8_000)
                socket = s

                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = s.getOutputStream()

                // Handshake: se presenta el token derivado de la clave precompartida.
                output.write((LinkProtocol.encodeAuth(Security.LINK_TOKEN) + "\n").toByteArray())
                output.flush()

                val reply = reader.readLine()
                if (LinkProtocol.typeOf(reply ?: "") != LinkProtocol.TYPE_AUTH_OK) {
                    onLog("El nodo rechazó la conexión: clave de enlace inválida")
                    close()
                    return@Thread
                }

                onLog("Enlace establecido con el nodo del túnel")
                onConnectionChanged(true)

                while (running && !s.isClosed) {
                    val line = reader.readLine() ?: break
                    val reading = LinkProtocol.decodeState(line)
                    if (reading != null) onReading(reading)
                    else onLog("Mensaje ilegible descartado")
                }
            } catch (e: Exception) {
                onLog("No se pudo conectar: ${e.message}")
            } finally {
                onConnectionChanged(false)
                close()
            }
        }.start()
    }

    /** Envia un comando al nodo. Enlace bidireccional (criterio 2.1.4). */
    fun sendCommand(action: String) {
        val s = socket
        if (s == null || s.isClosed) {
            onLog("Sin enlace: el comando no se envió")
            return
        }
        Thread {
            try {
                val output = s.getOutputStream()
                output.write((LinkProtocol.encodeCommand(action) + "\n").toByteArray())
                output.flush()
                onLog("Comando enviado al nodo: $action")
            } catch (e: Exception) {
                onLog("Error al enviar el comando: ${e.message}")
            }
        }.start()
    }

    fun close() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        socket = null
    }
}
