package com.firsov.bluetoothalarm.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firsov.bluetoothalarm.data.model.LockState
import com.firsov.bluetoothalarm.viewmodel.LockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockScreen(viewModel: LockViewModel) {

    val state by viewModel.state.collectAsState()
    val isConnected by viewModel.connected.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val rssi by viewModel.rssi.collectAsState()
    val logText by viewModel.status.collectAsState()

    // Анимация вращения иконки при поиске
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ), label = "iconRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BikeLock", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // Кнопка сканирования в верхнем углу
                    TextButton(
                        onClick = { viewModel.startScan() },
                        enabled = !isScanning && !isConnected
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (isScanning) rotation else 0f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isScanning) "ПОИСК..." else "СКАН")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- ВЕРХНЯЯ КАРТОЧКА: СТАТУС И СИГНАЛ ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state == LockState.ALARM)
                        Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "СОСТОЯНИЕ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = state.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state == LockState.ALARM) Color.Red else Color.Unspecified
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        // Индикатор сигнала
                        Text(
                            text = if (isConnected) "СИГНАЛ: $rssi dBm" else "НЕТ СВЯЗИ",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) Color(0xFF2E7D32) else Color.Gray
                        )
                        // Индикатор работы BLE
                        Text(
                            text = if (isScanning) "СКАНИРОВАНИЕ..." else "ГОТОВ",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isScanning) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // --- ЦЕНТРАЛЬНАЯ ЧАСТЬ: КНОПКИ УПРАВЛЕНИЯ ---
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Кнопка ЗАБЛОКИРОВАТЬ
                val isLockEnabled = isConnected && state != LockState.LOCKED && state != LockState.ALARM
                Button(
                    onClick = { viewModel.lock() },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    enabled = isLockEnabled, // Отключаем, если уже заперто или тревога
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color.LightGray.copy(alpha = 0.4f) // Цвет "неактивной" кнопки
                    )
                ) {
                    Text(
                        text = if (state == LockState.LOCKED) "ЗАБЛОКИРОВАНО" else "ЗАБЛОКИРОВАТЬ",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Кнопка РАЗБЛОКИРОВАТЬ
                val isUnlockEnabled = isConnected && state != LockState.UNLOCKED && state != LockState.ALARM
                Button(
                    onClick = { viewModel.unlock() },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    enabled = isUnlockEnabled, // Отключаем, если уже открыто или тревога
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        disabledContainerColor = Color.LightGray.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (state == LockState.UNLOCKED) "РАЗБЛОКИРОВАНО" else "РАЗБЛОКИРОВАТЬ",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (state == LockState.ALARM) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.alarmOff() },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            text = "СНЯТЬ ТРЕВОГУ",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                }
            }


            // --- НИЖНЯЯ ЧАСТЬ: ЛОГ СОБЫТИЙ ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ИСТОРИЯ СОБЫТИЙ (10)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Color(0xFFF8F9FA),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text(
                        text = logText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            fontSize = 12.sp
                        ),
                        color = Color(0xFF37474F)
                    )
                }
            }
        }
    }
}
