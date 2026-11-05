package com.example.monitoreoambiental.net

import com.example.monitoreoambiental.model.SensorReading
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Protocolo del enlace entre las dos aplicaciones moviles.
 *
 * TECNOLOGIA INALAMBRICA ELEGIDA: sockets TCP sobre Wi-Fi, con el telefono del
 * nodo levantando su propia red local (zona Wi-Fi).
 *
 * Justificacion frente a las tres opciones que plantea la guia:
 *
 *   Bluetooth clasico: alcance de unos 10 metros y un solo enlace punto a punto.
 *     Superficie puede estar a cientos de metros del frente de trabajo, y se
 *     necesita que mas de un supervisor pueda observar el mismo nodo.
 *
 *   MQTT: requiere un broker accesible. En mineria subterranea no hay internet,
 *     y montar un broker agrega un punto de falla fuera del control del nodo.
 *
 *   TCP sobre Wi-Fi: red aislada levantada por el propio nodo, varios
 *     supervisores simultaneos, y entrega confirmada. Esto ultimo es decisivo:
 *     una alerta critica de evacuacion no puede perderse en silencio, que es
 *     exactamente lo que pasaria con UDP o con un broadcast sin confirmacion.
 *
 * FORMATO: una linea de texto JSON por mensaje, terminada en salto de linea.
 *
 *   Supervisor -> Nodo, primer mensaje obligatorio:
 *     {"tipo":"AUTH","token":"<sha256 de la clave precompartida>"}
 *   Nodo -> Supervisor:
 *     {"tipo":"AUTH_OK"} | {"tipo":"AUTH_FAIL"}
 *   Nodo -> Supervisor, en cada cambio de estado:
 *     {"tipo":"ESTADO","vibracion":700,"distancia":26,"base":20,"ts":1730000000000}
 *   Supervisor -> Nodo:
 *     {"tipo":"CMD","accion":"SILENCIAR"} | {"tipo":"CMD","accion":"RECALIBRAR"}
 */
object LinkProtocol {

    /** Puerto de escucha del nodo, fuera del rango de puertos privilegiados. */
    const val PORT = 50505

    const val TYPE_AUTH = "AUTH"
    const val TYPE_AUTH_OK = "AUTH_OK"
    const val TYPE_AUTH_FAIL = "AUTH_FAIL"
    const val TYPE_STATE = "ESTADO"
    const val TYPE_COMMAND = "CMD"

    const val CMD_SILENCE = "SILENCIAR"
    const val CMD_RECALIBRATE = "RECALIBRAR"

    /** Serializa una lectura para enviarla a superficie. */
    fun encodeState(reading: SensorReading): String = JSONObject().apply {
        put("tipo", TYPE_STATE)
        put("vibracion", reading.vibration)
        put("distancia", reading.distanceCm)
        put("base", reading.baselineCm)
        put("ts", reading.measuredAtMillis)
    }.toString()

    /**
     * Reconstruye una lectura recibida. Devuelve null si el mensaje no es un
     * estado valido, de modo que un mensaje malformado se descarta en lugar de
     * derribar el enlace (ISO 27400: validacion de entradas).
     */
    fun decodeState(line: String): SensorReading? = try {
        val json = JSONObject(line)
        if (json.optString("tipo") != TYPE_STATE) {
            null
        } else {
            SensorReading(
                vibration = json.getInt("vibracion"),
                distanceCm = json.getInt("distancia"),
                baselineCm = json.getInt("base"),
                measuredAtMillis = json.optLong("ts", System.currentTimeMillis())
            )
        }
    } catch (e: Exception) {
        null
    }

    fun encodeAuth(token: String): String = JSONObject().apply {
        put("tipo", TYPE_AUTH)
        put("token", token)
    }.toString()

    fun encodeCommand(action: String): String = JSONObject().apply {
        put("tipo", TYPE_COMMAND)
        put("accion", action)
    }.toString()

    /** Extrae la accion de un comando recibido, o null si no es un comando. */
    fun decodeCommand(line: String): String? = try {
        val json = JSONObject(line)
        if (json.optString("tipo") == TYPE_COMMAND) json.optString("accion") else null
    } catch (e: Exception) {
        null
    }

    fun typeOf(line: String): String? = try {
        JSONObject(line).optString("tipo")
    } catch (e: Exception) {
        null
    }
}

/**
 * Direcciones IPv4 locales del equipo.
 *
 * El operador del nodo lee en pantalla la IP que debe escribir el supervisor.
 * Funciona tanto conectado a una red Wi-Fi como levantando zona Wi-Fi propia,
 * y por eso la IP no esta escrita a fuego en el codigo.
 */
fun localIpAddresses(): List<String> {
    val result = mutableListOf<String>()
    try {
        for (intf in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!intf.isUp || intf.isLoopback) continue
            for (addr in Collections.list(intf.inetAddresses)) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    addr.hostAddress?.let { result.add(it) }
                }
            }
        }
    } catch (e: Exception) {
        // Sin red disponible: la interfaz informa el caso al operador.
    }
    return result
}
