package com.example.monitoreoambiental.domain

import com.example.monitoreoambiental.model.AlertLevel
import com.example.monitoreoambiental.model.SensorReading

/**
 * GeoEdgeEngine
 *
 * Motor de decision del sistema. Replica EXACTAMENTE la tabla definida en la
 * Evaluacion 1 para el sketch del Arduino Uno R3:
 *
 *  Vibracion (>= 512) | Deformacion (|dist - base| >= 2cm) | Estado      | Salidas
 *  -------------------+------------------------------------+-------------+--------------------------
 *  No                 | No                                 | NORMAL      | LED Verde
 *  No                 | Si                                 | NORMAL      | LED Verde
 *  Si                 | No                                 | ADVERTENCIA | LED Amarillo, SIN sonido
 *  Si                 | Si                                 | CRITICO     | LED Rojo + sirena
 *
 * Esta clase no conoce la red, la base de datos ni la interfaz. Por eso la
 * decision se puede tomar en el frente de trabajo sin depender de nada externo,
 * tal como lo hacia el microcontrolador, y por eso el dia que se conecte el
 * hardware real no hay que modificarla.
 */
object GeoEdgeEngine {

    /** Evalua el nivel de alerta a partir de una lectura. */
    fun evaluate(reading: SensorReading): AlertLevel = when {
        reading.hasVibration && reading.hasDeformation -> AlertLevel.CRITICAL
        reading.hasVibration -> AlertLevel.WARNING
        else -> AlertLevel.NORMAL
    }

    /** Sobrecarga para evaluar valores sueltos sin construir una lectura. */
    fun evaluate(vibration: Int, distanceCm: Int, baselineCm: Int): AlertLevel =
        evaluate(
            SensorReading(
                vibration = vibration,
                distanceCm = distanceCm,
                baselineCm = baselineCm,
                measuredAtMillis = 0L
            )
        )

    /** Etiqueta corta en español para la interfaz. */
    fun label(level: AlertLevel): String = when (level) {
        AlertLevel.CRITICAL -> "CRÍTICO"
        AlertLevel.WARNING -> "ADVERTENCIA"
        AlertLevel.NORMAL -> "NORMAL"
    }

    /** Explicacion operativa del estado, para la pantalla y para la demostracion. */
    fun description(level: AlertLevel): String = when (level) {
        AlertLevel.CRITICAL ->
            "Vibración y deformación simultáneas. Evacuar el frente de inmediato."
        AlertLevel.WARNING ->
            "Vibración sin deformación: paso de maquinaria. Falsa alarma evitada, sin sirena."
        AlertLevel.NORMAL ->
            "Operación sin novedades en el frente de trabajo."
    }

    /** true si el estado debe accionar la sirena. */
    fun requiresSiren(level: AlertLevel): Boolean = level == AlertLevel.CRITICAL
}
