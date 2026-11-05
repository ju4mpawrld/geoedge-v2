package com.example.monitoreoambiental.data

import com.example.monitoreoambiental.model.ConnectionStatus
import com.example.monitoreoambiental.model.Device
import com.example.monitoreoambiental.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * LocalNodeRepository
 *
 * Implementacion para el MODO NODO: el equipo instalado en el frente del tunel.
 * Cumple el mismo rol que el FakeDeviceRepository del ejemplo base, es decir
 * alimentar el flujo con lecturas simuladas, pero aqui las lecturas no siguen
 * una secuencia automatica por fases: las controla el operador con los dos
 * deslizadores de la pantalla, para poder demostrar cada fila de la tabla de
 * decision a voluntad.
 *
 * SOBRE LA SIMULACION DE SENSORES
 * El hardware fisico no esta conectado en esta entrega. Las dos variables se
 * ingresan por pantalla en el mismo rango del hardware original: 0 a 1023 para
 * la vibracion, que es el rango del ADC de 10 bits del Arduino, y centimetros
 * para la distancia del HC-SR04.
 *
 * Esto continua una decision ya tomada y documentada en la Evaluacion 1, donde
 * el sensor de vibracion SW-200D fue reemplazado por un potenciometro
 * precisamente para "permitir una simulacion gradual y controlada del nivel de
 * vibracion". El deslizador es ese mismo potenciometro, movido al telefono.
 *
 * El dia que se conecte el hardware real mediante un modulo HC-05, lo unico que
 * cambia es quien llama a updateSensors(): en lugar de la pantalla, el lector
 * de la trama serial por Bluetooth. El motor de decision, el almacenamiento, la
 * seguridad y el enlace con superficie no se tocan.
 */
class LocalNodeRepository(
    private val deviceId: String = "NODO-01",
    private val deviceName: String = "Frente de avance 01",
    private val deviceLocation: String = "Nivel -320, galería principal"
) : DeviceRepository {

    private val state = MutableStateFlow(
        Device(
            id = deviceId,
            name = deviceName,
            location = deviceLocation,
            connection = ConnectionStatus.CONNECTED,
            reading = SensorReading(
                vibration = 0,
                distanceCm = 20,
                baselineCm = 20,
                measuredAtMillis = System.currentTimeMillis()
            )
        )
    )

    /** El nodo siempre reporta un unico dispositivo: su propio frente. */
    override fun observeDevices(): Flow<List<Device>> = state.map { listOf(it) }

    /** Acceso directo al dispositivo, para la pantalla del nodo. */
    fun observeDevice(): Flow<Device> = state.asStateFlow()

    fun current(): Device = state.value

    /** Nueva lectura de sensores. Conserva la base calibrada vigente. */
    fun updateSensors(vibration: Int, distanceCm: Int) {
        val previous = state.value.reading
        state.value = state.value.copy(
            reading = SensorReading(
                vibration = vibration,
                distanceCm = distanceCm,
                baselineCm = previous?.baselineCm ?: distanceCm,
                measuredAtMillis = System.currentTimeMillis()
            )
        )
    }

    /**
     * Fija la distancia actual como nueva referencia del frente.
     * Equivale a la toma de distancia base del sketch Arduino, y es necesaria
     * despues de una tronadura planificada que cambio la geometria del tunel.
     */
    fun calibrateBaseline() {
        val previous = state.value.reading ?: return
        state.value = state.value.copy(
            reading = previous.copy(
                baselineCm = previous.distanceCm,
                measuredAtMillis = System.currentTimeMillis()
            )
        )
    }
}
