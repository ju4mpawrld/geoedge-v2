package com.example.monitoreoambiental.model

import kotlin.math.abs

/**
 * Modelo de dominio de GeoEdge.
 *
 * Mantiene la misma estructura del ejemplo base entregado por el docente
 * (ConnectionStatus / SensorReading / Device con propiedad calculada de alerta),
 * adaptada al dominio del proyecto: en lugar de temperatura y humedad, las dos
 * variables que cruza GeoEdge son vibracion y deformacion estructural.
 */

/** Estado del enlace con un nodo. Identico al del ejemplo base. */
enum class ConnectionStatus {
    CONNECTED, DISCONNECTED
}

/**
 * Nivel de alerta del frente de trabajo.
 * Equivale a los tres LEDs del circuito Arduino de la Evaluacion 1.
 */
enum class AlertLevel {
    NORMAL, WARNING, CRITICAL
}

/**
 * Lectura instantanea de los sensores del frente.
 *
 * @param vibration        0..1023, mismo rango del ADC de 10 bits del Arduino (potenciometro en A0)
 * @param distanceCm       lectura del sensor ultrasonico HC-SR04 en centimetros
 * @param baselineCm       distancia base calibrada del frente, referencia para medir convergencia
 * @param measuredAtMillis marca de tiempo de la medicion
 */
data class SensorReading(
    val vibration: Int,
    val distanceCm: Int,
    val baselineCm: Int,
    val measuredAtMillis: Long
) {
    companion object {
        /** Umbral de vibracion por tronadura o maquinaria pesada. */
        const val VIBRATION_THRESHOLD = 512

        /** Desviacion minima respecto a la base para considerar deformacion. */
        const val DEFORMATION_THRESHOLD_CM = 2
    }

    /** Vibracion por sobre el umbral: paso de maquinaria, tronadura o movimiento sismico. */
    val hasVibration: Boolean
        get() = vibration >= VIBRATION_THRESHOLD

    /** La roca se desplazo respecto a la base calibrada: convergencia del tunel. */
    val hasDeformation: Boolean
        get() = abs(distanceCm - baselineCm) >= DEFORMATION_THRESHOLD_CM

    /**
     * Condicion fuera de rango seguro.
     *
     * Equivalente a isOutsideDemoRange del ejemplo base, pero aqui exige que
     * AMBAS variables esten presentes. Esa conjuncion es la propuesta de valor
     * del proyecto: una sola variable produce falsas alarmas, y las falsas
     * alarmas son la razon por la que los trabajadores desconectan los sistemas
     * actuales.
     */
    val isOutsideSafeRange: Boolean
        get() = hasVibration && hasDeformation

    /** Desviacion absoluta respecto a la base, en centimetros. */
    val deviationCm: Int
        get() = abs(distanceCm - baselineCm)
}

/**
 * Un nodo GeoEdge instalado en un frente de trabajo.
 * Misma forma que el Device del ejemplo base.
 */
data class Device(
    val id: String,
    val name: String,
    val location: String,
    val connection: ConnectionStatus,
    val reading: SensorReading? = null
) {
    /** Alerta vigente. Identico criterio al hasCurrentAlert del ejemplo base. */
    val hasCurrentAlert: Boolean
        get() = connection == ConnectionStatus.CONNECTED &&
            reading?.isOutsideSafeRange == true
}
