package com.example.monitoreoambiental.data

import com.example.monitoreoambiental.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevices(): Flow<List<Device>>
}