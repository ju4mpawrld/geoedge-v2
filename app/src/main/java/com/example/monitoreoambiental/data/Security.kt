package com.example.monitoreoambiental.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Security
 *
 * Controles del criterio 2.1.3 (ISO/IEC 27400, seguridad y privacidad en IoT),
 * aplicados al lineamiento de industria OT.
 *
 *  - Credenciales en reposo: SHA-256 con salt aleatorio unico por usuario.
 *    La contrasena nunca se guarda ni se transmite en texto claro.
 *  - Control de acceso al nodo: clave precompartida obligatoria antes de
 *    entregar cualquier dato de sensor.
 *  - Minimo privilegio: el rol define que pantalla y que comandos se habilitan.
 *  - Resistencia a fuerza bruta: bloqueo temporal tras intentos fallidos.
 *  - Canales laterales: comparacion de hashes en tiempo constante.
 */
object Security {

    const val ROLE_NODE = "NODO"
    const val ROLE_SUPERVISOR = "SUPERVISOR"

    /**
     * Clave precompartida del enlace Nodo <-> Supervisor.
     *
     * En una instalacion productiva se aprovisiona por equipo durante el
     * despliegue en faena y no vive en el codigo fuente. Queda fija aqui porque
     * el alcance de esta evaluacion es un prototipo de laboratorio.
     */
    private const val PRE_SHARED_KEY = "GeoEdge-INACAP-TI3042-2026"

    /** Lo que viaja en el handshake. La clave en si nunca sale del dispositivo. */
    val LINK_TOKEN: String by lazy { sha256(PRE_SHARED_KEY) }

    const val MAX_ATTEMPTS = 3
    const val LOCKOUT_MILLIS = 30_000L

    fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** Salt aleatorio criptograficamente seguro, 16 bytes en hexadecimal. */
    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun hashPassword(password: String, salt: String): String = sha256(salt + password)

    /**
     * Comparacion en tiempo constante: no revela informacion por el tiempo que
     * tarda en fallar.
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
