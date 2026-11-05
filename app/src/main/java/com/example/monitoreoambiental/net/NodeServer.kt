package com.example.monitoreoambiental.net

import com.example.monitoreoambiental.data.Security
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * NodeServer
 *
 * Lado nodo del enlace. Corre en el telefono instalado en el frente del tunel.
 *
 * Responsabilidades:
 *   - Escuchar conexiones de supervisores.
 *   - Validar la clave precompartida ANTES de entregar un solo dato de sensor
 *     (ISO 27400, control de acceso al dispositivo IoT).
 *   - Difundir cada cambio de estado a todos los supervisores conectados.
 *   - Recibir comandos de vuelta, lo que hace el enlace bidireccional.
 *
 * Toda la E/S corre en hilos propios: Android lanza NetworkOnMainThreadException
 * si se hace red en el hilo principal.
 */
class NodeServer(
    private val onLog: (String) -> Unit,
    private val onCommand: (String) -> Unit,
    private val onClientCountChanged: (Int) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val clients = Collections.synchronizedList(mutableListOf<Socket>())

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        Thread {
            try {
                val server = ServerSocket(LinkProtocol.PORT)
                serverSocket = server
                onLog("Nodo escuchando en el puerto ${LinkProtocol.PORT}")
                while (running) {
                    val socket = server.accept()
                    Thread { handle(socket) }.start()
                }
            } catch (e: Exception) {
                if (running) onLog("Error del servidor: ${e.message}")
            }
        }.start()
    }

    private fun handle(socket: Socket) {
        val ip = socket.inetAddress?.hostAddress ?: "desconocida"
        try {
            // El handshake tiene plazo: un cliente que no se identifica no
            // mantiene un hilo tomado indefinidamente.
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = socket.getOutputStream()

            val first = reader.readLine()
            val authorized = try {
                val json = org.json.JSONObject(first ?: "{}")
                json.optString("tipo") == LinkProtocol.TYPE_AUTH &&
                    Security.constantTimeEquals(Security.LINK_TOKEN, json.optString("token"))
            } catch (e: Exception) {
                false
            }

            if (!authorized) {
                output.write("{\"tipo\":\"${LinkProtocol.TYPE_AUTH_FAIL}\"}\n".toByteArray())
                output.flush()
                socket.close()
                onLog("RECHAZADO $ip: clave de enlace inválida")
                return
            }

            output.write("{\"tipo\":\"${LinkProtocol.TYPE_AUTH_OK}\"}\n".toByteArray())
            output.flush()
            socket.soTimeout = 0

            clients.add(socket)
            onClientCountChanged(clients.size)
            onLog("Supervisor conectado desde $ip")

            while (running && !socket.isClosed) {
                val line = reader.readLine() ?: break
                LinkProtocol.decodeCommand(line)?.let { onCommand(it) }
                    ?: onLog("Mensaje no reconocido, descartado")
            }
        } catch (e: Exception) {
            // Desconexion o error de red: se limpia en el finally.
        } finally {
            clients.remove(socket)
            try { socket.close() } catch (e: Exception) { }
            onClientCountChanged(clients.size)
            onLog("Supervisor desconectado ($ip)")
        }
    }

    /** Envia un mensaje a todos los supervisores conectados. */
    fun broadcast(message: String) {
        Thread {
            val snapshot = synchronized(clients) { clients.toList() }
            for (socket in snapshot) {
                try {
                    val output = socket.getOutputStream()
                    output.write((message + "\n").toByteArray())
                    output.flush()
                } catch (e: Exception) {
                    clients.remove(socket)
                    try { socket.close() } catch (ex: Exception) { }
                    onClientCountChanged(clients.size)
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (e: Exception) { }
        synchronized(clients) {
            clients.forEach { try { it.close() } catch (e: Exception) { } }
            clients.clear()
        }
        onClientCountChanged(0)
    }
}
