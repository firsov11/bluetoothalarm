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

    private val _status = MutableStateFlow("UNKNOWN")
    val status: StateFlow<String> = _status

    // НОВОЕ: состояние сканирования для UI
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Ищем по имени или по Service UUID из фильтра
            if (result.device.name == DEVICE_NAME) {
                stopScan()
                connect(result.device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning || _connected.value) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        // Фильтр критически важен для работы в фоне
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            // Используем BALANCED для стабильности в фоне
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        scanning = true
        _isScanning.value = true
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        _isScanning.value = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    // НОВОЕ: Метод для ручного сброса (для кнопки)
    @SuppressLint("MissingPermission")
    fun manualReset() {
        stopScan()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connected.value = false
        _status.value = "RESETTING..."
        handler.postDelayed({ startScan() }, 500)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        bluetoothGatt?.close()
        // autoconnect = false для более быстрого подключения при обнаружении
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connected.value = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connected.value = false
                _status.value = "DISCONNECTED"
                controlChar = null
                // Обязательно закрываем старый GATT объект
                gatt.close()
                if (bluetoothGatt == gatt) bluetoothGatt = null

                // Перезапуск сканирования через задержку
                handler.postDelayed({ startScan() }, 2000)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID) ?: return
            controlChar = service.getCharacteristic(CONTROL_UUID)
            val statusChar = service.getCharacteristic(STATUS_UUID)

            statusChar?.let { char ->
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

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == STATUS_UUID) _status.value = String(value).trim()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == STATUS_UUID) {
                _status.value = String(characteristic.value ?: byteArrayOf()).trim()
            }
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
                    handler.postDelayed(this, 1500)
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
