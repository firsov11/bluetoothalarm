package com.firsov.bluetoothalarm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firsov.bluetoothalarm.ble.BleManager
import com.firsov.bluetoothalarm.data.model.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

    val connected = bleManager.connected
    val rssi = bleManager.rssi
    val isScanning = bleManager.isScanning

    // Пробрасываем весь текст лога для окна в нижней части экрана
    val status = bleManager.status

    // Исправленная логика определения состояния замка
    val state: StateFlow<LockState> = bleManager.status.map { logText ->
        // Разбиваем лог на строки и берем первую непустую
        val lastLogLine = logText.lines().firstOrNull { it.isNotBlank() } ?: ""

        when {
            // Ищем вхождение слова в последнюю добавленную строку лога
            lastLogLine.contains("ALARM", ignoreCase = true) -> LockState.ALARM
            lastLogLine.contains("UNLOCKED", ignoreCase = true) -> LockState.UNLOCKED
            lastLogLine.contains("LOCKED", ignoreCase = true) -> LockState.LOCKED
            else -> LockState.UNKNOWN
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockState.UNKNOWN)


    fun lock() {
        bleManager.sendCommand("LOCK")
    }

    fun unlock() {
        bleManager.sendCommand("UNLOCK")
    }

    fun alarmOff() {
        bleManager.sendCommand("ALARM_OFF")
    }

    fun startScan() {
        bleManager.startScan()
    }

    // Добавим функцию сброса, раз мы ее обсуждали для UI
    fun refreshConnection() {
        bleManager.manualReset()
    }
}


