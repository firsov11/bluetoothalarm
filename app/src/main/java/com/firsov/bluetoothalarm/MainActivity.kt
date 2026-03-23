package com.firsov.bluetoothalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.firsov.bluetoothalarm.ble.BleForegroundService
import com.firsov.bluetoothalarm.ble.BleManager
import com.firsov.bluetoothalarm.ui.LockScreen
import com.firsov.bluetoothalarm.viewmodel.LockViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var bleManager: BleManager

    // Hilt сам создаст ViewModel, если она помечена @HiltViewModel
    private val viewModel: LockViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Проверяем, дали ли основные разрешения
        val btScan = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: true
        val btConnect = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        val location = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: true

        if (btScan && btConnect && location) {
            startBleWork()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            MaterialTheme {
                Surface {
                    LockScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = getRequiredPermissions()
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startBleWork()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startBleWork() {
        // 1. Запускаем сервис (он сам пнет bleManager.startScan() в своем onCreate)
        val intent = Intent(this, BleForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)

        // 2. На всякий случай дублируем старт сканирования, если сервис уже был запущен
        bleManager.startScan()
    }

    private fun getRequiredPermissions(): List<String> {
        val list = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Для Android 11 и ниже локация ОБЯЗАТЕЛЬНА для поиска BLE
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list
    }
}
