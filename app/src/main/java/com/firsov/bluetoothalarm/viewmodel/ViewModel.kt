package com.firsov.bluetoothalarm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firsov.bluetoothalarm.ble.BleManager
import com.firsov.bluetoothalarm.data.model.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

    val connected = bleManager.connected
    val rssi = bleManager.rssi
    val isScanning = bleManager.isScanning

    private val _isAutoMode = MutableStateFlow(false)
    val isAutoMode: StateFlow<Boolean> = _isAutoMode

    // Время последнего ручного закрытия
    private var lastManualLockTime: Long = 0

    val state: StateFlow<LockState> = bleManager.status.map {
        when(it) {
            "ALARM" -> LockState.ALARM
            "LOCKED" -> LockState.LOCKED
            "UNLOCKED" -> LockState.UNLOCKED
            else -> LockState.UNKNOWN
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockState.UNKNOWN)

    init {
        observeAutoLogic()
    }

    private fun observeAutoLogic() {
        viewModelScope.launch {
            combine(connected, rssi, _isAutoMode) { conn, r, auto -> Triple(conn, r, auto) }
                .collect { (conn, r, auto) ->
                    val currentTime = System.currentTimeMillis()

                    // ПРОВЕРКА ДЛЯ АВТО-ОТКРЫТИЯ:
                    // 1. Связь есть, режим "Авто" включен, сигнал > -60, байк закрыт
                    // 2. С момента ручного LOCK прошло БОЛЕЕ 10 секунд (10000 мс)
                    if (conn && auto && r > -60 && state.value == LockState.LOCKED) {
                        if (currentTime - lastManualLockTime > 10000) {
                            unlock()
                        }
                    }
                }
        }
    }

    fun toggleAuto(enabled: Boolean) {
        _isAutoMode.value = enabled
    }

    fun lock() {
        // Запоминаем время нажатия, чтобы дать себе 10 секунд уйти
        lastManualLockTime = System.currentTimeMillis()
        bleManager.sendCommand("LOCK")
    }

    fun unlock() {
        bleManager.sendCommand("UNLOCK")
    }

    fun alarmOff() {
        bleManager.sendCommand("ALARM_OFF")
    }

    fun refreshConnection() {
        bleManager.manualReset()
    }
}

