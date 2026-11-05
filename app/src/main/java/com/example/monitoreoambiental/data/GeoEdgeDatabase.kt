package com.example.monitoreoambiental.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.monitoreoambiental.model.AlertLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un evento persistido en la base local. */
data class EventRecord(
    val id: Long,
    val timestamp: Long,
    val vibration: Int,
    val distanceCm: Int,
    val baselineCm: Int,
    val level: String,
    val origin: String
) {
    fun formattedTime(): String =
        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * GeoEdgeDatabase
 *
 * Almacenamiento local con SQLite, opcion autorizada por la guia de la Unidad 2
 * tanto para autenticacion sin conexion como para gestion de datos de sensores.
 *
 * Por que SQLite y no Firebase: el contexto del proyecto es un tunel de mineria
 * subterranea SIN cobertura. El nodo debe poder autenticar al operador y
 * registrar el historial de eventos aunque no exista ninguna red disponible.
 * Si el login dependiera de internet, el sistema no serviria justo donde tiene
 * que servir. La interoperabilidad con superficie se resuelve en la capa de
 * transporte, no en la base de datos.
 *
 *   usuarios(id, usuario, salt, hash, rol)
 *   eventos (id, timestamp, vibracion, distancia, base, nivel, origen)
 */
class GeoEdgeDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "geoedge.db"
        const val DB_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE usuarios (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                usuario TEXT NOT NULL UNIQUE,
                salt    TEXT NOT NULL,
                hash    TEXT NOT NULL,
                rol     TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE eventos (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                vibracion INTEGER NOT NULL,
                distancia INTEGER NOT NULL,
                base      INTEGER NOT NULL,
                nivel     TEXT NOT NULL,
                origen    TEXT NOT NULL
            )
            """.trimIndent()
        )
        seedUsers(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS usuarios")
        db.execSQL("DROP TABLE IF EXISTS eventos")
        onCreate(db)
    }

    /** Un usuario por rol. Las contrasenas quedan hasheadas con salt propio. */
    private fun seedUsers(db: SQLiteDatabase) {
        insertUser(db, "nodo", "Nodo123", Security.ROLE_NODE)
        insertUser(db, "supervisor", "Super123", Security.ROLE_SUPERVISOR)
    }

    private fun insertUser(db: SQLiteDatabase, user: String, password: String, role: String) {
        val salt = Security.newSalt()
        val values = ContentValues().apply {
            put("usuario", user)
            put("salt", salt)
            put("hash", Security.hashPassword(password, salt))
            put("rol", role)
        }
        db.insert("usuarios", null, values)
    }

    /**
     * Valida credenciales y devuelve el rol, o null si no son correctas.
     *
     * Un usuario inexistente y una contrasena erronea producen el mismo
     * resultado: no se le confirma a un atacante que una cuenta existe.
     */
    fun authenticate(user: String, password: String): String? {
        val cursor = readableDatabase.query(
            "usuarios",
            arrayOf("salt", "hash", "rol"),
            "usuario = ?",
            arrayOf(user.trim().lowercase()),
            null, null, null
        )
        var role: String? = null
        if (cursor.moveToFirst()) {
            val salt = cursor.getString(0)
            val stored = cursor.getString(1)
            if (Security.constantTimeEquals(stored, Security.hashPassword(password, salt))) {
                role = cursor.getString(2)
            }
        }
        cursor.close()
        return role
    }

    /** Persiste un cambio de nivel de alerta. */
    fun saveEvent(
        vibration: Int,
        distanceCm: Int,
        baselineCm: Int,
        level: AlertLevel,
        origin: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("vibracion", vibration)
            put("distancia", distanceCm)
            put("base", baselineCm)
            put("nivel", level.name)
            put("origen", origin)
        }
        writableDatabase.insert("eventos", null, values)
    }

    /** Consulta de los ultimos eventos, mas recientes primero. */
    fun recentEvents(limit: Int = 25): List<EventRecord> {
        val list = mutableListOf<EventRecord>()
        val cursor = readableDatabase.query(
            "eventos",
            arrayOf("id", "timestamp", "vibracion", "distancia", "base", "nivel", "origen"),
            null, null, null, null,
            "timestamp DESC",
            limit.toString()
        )
        while (cursor.moveToNext()) {
            list.add(
                EventRecord(
                    id = cursor.getLong(0),
                    timestamp = cursor.getLong(1),
                    vibration = cursor.getInt(2),
                    distanceCm = cursor.getInt(3),
                    baselineCm = cursor.getInt(4),
                    level = cursor.getString(5),
                    origin = cursor.getString(6)
                )
            )
        }
        cursor.close()
        return list
    }

    /** Conteo acumulado de eventos criticos. */
    fun criticalCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM eventos WHERE nivel = ?",
            arrayOf(AlertLevel.CRITICAL.name)
        )
        var total = 0
        if (cursor.moveToFirst()) total = cursor.getInt(0)
        cursor.close()
        return total
    }
}
