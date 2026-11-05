package com.example.monitoreoambiental.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoreoambiental.data.DeviceRepository
import com.example.monitoreoambiental.model.ConnectionStatus
import com.example.monitoreoambiental.model.Device
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DevicesUiState(
    val isLoading: Boolean = true,
    val devices: List<Device> = emptyList(),
    val errorMessage: String? = null
) {
    val connectedCount: Int
        get() = devices.count {
            it.connection == ConnectionStatus.CONNECTED
        }
    val alertCount: Int
        get() = devices.count { it.hasCurrentAlert }
}

class DevicesViewModel(
    repository: DeviceRepository
) : ViewModel() {
    val uiState: StateFlow<DevicesUiState> = repository
        .observeDevices()
        .map { devices ->
            DevicesUiState(
                isLoading = false, devices = devices
            )
        }
        .catch {
            emit(
                DevicesUiState(
                    isLoading = false,
                    errorMessage = "No se pudieron cargar los datos."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DevicesUiState()
        )
}