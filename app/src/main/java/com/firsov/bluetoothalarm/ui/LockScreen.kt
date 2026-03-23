package com.firsov.bluetoothalarm.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.firsov.bluetoothalarm.data.model.LockState
import com.firsov.bluetoothalarm.viewmodel.LockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(viewModel: LockViewModel) {

    val state by viewModel.state.collectAsState()
    val isAuto by viewModel.isAutoMode.collectAsState()
    val isConnected by viewModel.connected.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState() // Добавили стейт сканирования
    val rssi by viewModel.rssi.collectAsState()

    // Анимация вращения иконки при поиске
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "iconRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BikeLock") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        // КНОПКА СКАНИРОВАНИЯ С АНИМАЦИЕЙ
                        IconButton(onClick = { viewModel.refreshConnection() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Поиск",
                                modifier = Modifier.rotate(if (isScanning && !isConnected) rotation else 0f),
                                tint = if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        Text("АВТО", style = MaterialTheme.typography.labelSmall)

                        Spacer(Modifier.width(4.dp))

                        Switch(
                            checked = isAuto,
                            onCheckedChange = { viewModel.toggleAuto(it) }
                        )
                    }
                }
            )
        }
    ) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Статус связи
            Text(
                text = when {
                    isConnected -> "Связь: $rssi dBm"
                    isScanning -> "Идет поиск устройства..."
                    else -> "Поиск остановлен"
                },
                color = if (isConnected) Color(0xFF2E7D32) else Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))

            // Основной статус
            Text(
                text = state.name,
                style = MaterialTheme.typography.displayMedium,
                color = if (state == LockState.ALARM) Color.Red else MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(32.dp))

            // Кнопки управления
            // Кнопки управления
            Button(
                onClick = { viewModel.lock() },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
                // Если уже закрыто, делаем кнопку менее яркой, но оставляем активной на всякий случай
                colors = if (state == LockState.LOCKED)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                else ButtonDefaults.buttonColors()
            ) {
                Text("ЗАБЛОКИРОВАТЬ (LOCK)")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.unlock() },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && state != LockState.UNLOCKED // Если открыто — кнопка гаснет
            ) {
                Text("РАЗБЛОКИРОВАТЬ (UNLOCK)")
            }

            if (state == LockState.ALARM) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.alarmOff() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("СНЯТЬ ТРЕВОГУ", color = Color.White)
                }
            }
        }
    }
}
