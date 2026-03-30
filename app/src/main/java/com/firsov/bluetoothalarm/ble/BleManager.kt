package com.firsov.bluetoothalarm.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DEVICE_NAME = "BikeLock"
    private val SERVICE_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    private val CONTROL_UUID = UUID.fromString("0000ABCD-0000-1000-8000-00805f9b34fb")
    private val STATUS_UUID = UUID.fromString("0000DCBA-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null
    private var controlChar: BluetoothGattCharacteristic? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val _rssi = MutableStateFlow(-100)
    val rssi: StateFlow<Int> = _rssi

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _status = MutableStateFlow("Ожидание...")
    val status: StateFlow<String> = _status

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private fun addToLog(message: String) {
        val filteredKeywords = listOf("Соединение", "Связь потеряна", "RSSI", "Статус BLE")
        if (filteredKeywords.any { message.contains(it) }) return

        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentLines = _status.value.lines().filter { it.isNotBlank() && it != "Ожидание..." }
        _status.value = (listOf("$timestamp: $message") + currentLines).take(10).joinToString("\n")
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) {
                addToLog("Замок найден")
                stopScan()
                connect(result.device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning || _connected.value) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanning = true
        _isScanning.value = true
        addToLog("Поиск $DEVICE_NAME...")
        scanner.startScan(listOf(filter), settings, scanCallback)
        handler.postDelayed({ stopScan() }, 15000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        _isScanning.value = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun manualReset() {
        addToLog("Сброс связи...")
        stopScan()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connected.value = false
        handler.postDelayed({ startScan() }, 1000)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        handler.post { // Выполняем в основном потоке для стабильности
            bluetoothGatt?.close()
            bluetoothGatt = null

            addToLog("Подключение...")

            // Попробуем autoConnect = true (иногда на C3 работает стабильнее)
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connected.value = true
                handler.postDelayed({ gatt.discoverServices() }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connected.value = false
                _status.value = "Связь потеряна"
                gatt.close()
                if (bluetoothGatt == gatt) bluetoothGatt = null
                handler.postDelayed({ startScan() }, 3000)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID) ?: return
            controlChar = service.getCharacteristic(CONTROL_UUID)
            val statusChar = service.getCharacteristic(STATUS_UUID)

            statusChar?.let { char ->
                // ШАГ 1: Читаем текущее состояние принудительно
                handler.postDelayed({ gatt.readCharacteristic(char) }, 200)

                // ШАГ 2: Подписываемся на Notify
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(CCCD_UUID)
                descriptor?.let { desc ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(desc)
                    }
                }
            }
            startRssiLoop(gatt)
        }

        // ШАГ 3: Получаем результат чтения (убирает UNKNOWN)
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == STATUS_UUID) {
                val msg = String(value).trim()
                addToLog(msg) // Должно добавить "LOCKED" или "UNLOCKED"
            }
        }


        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == STATUS_UUID) {
                val msg = String(characteristic.value ?: byteArrayOf()).trim()
                addToLog("Статус: $msg")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == STATUS_UUID) {
                addToLog(String(value).trim())
            }
        }


        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == STATUS_UUID) addToLog(String(characteristic.value).trim())
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _rssi.value = rssi
        }
    }

    private fun startRssiLoop(gatt: BluetoothGatt) {
        handler.post(object : Runnable {
            @SuppressLint("MissingPermission")
            override fun run() {
                if (_connected.value) {
                    gatt.readRemoteRssi()
                    handler.postDelayed(this, 2000)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(cmd: String) {
        val gatt = bluetoothGatt ?: return
        val char = controlChar ?: return
        val bytes = cmd.toByteArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
}

