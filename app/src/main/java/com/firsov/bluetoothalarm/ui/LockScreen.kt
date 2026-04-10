package com.firsov.bluetoothalarm.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firsov.bluetoothalarm.data.model.LockState
import com.firsov.bluetoothalarm.viewmodel.LockViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LockScreen(viewModel: LockViewModel) {
    val state by viewModel.state.collectAsState()
    val isConnected by viewModel.connected.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val rssi by viewModel.rssi.collectAsState()
    val logText by viewModel.status.collectAsState()

    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    // ЕДИНЫЙ СТИЛЬ ГЕОМЕТРИИ
    val commonShape = RoundedCornerShape(28.dp)
    val buttonBorder = if (isDark) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)),
        label = "iconRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BikeLock", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    TextButton(
                        onClick = { viewModel.startScan() },
                        enabled = !isScanning && !isConnected
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).rotate(if (isScanning) rotation else 0f)
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
            modifier = Modifier.padding(paddingValues).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- ВЕРХНЯЯ КАРТОЧКА ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = commonShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (state == LockState.ALARM) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.surface
                ),
                border = buttonBorder,
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(targetState = state, label = "stateIcon") { targetState ->
                            Icon(
                                imageVector = when (targetState) {
                                    LockState.LOCKED -> Icons.Default.Lock
                                    LockState.UNLOCKED -> Icons.Default.LockOpen
                                    LockState.ALARM -> Icons.Default.Warning
                                    else -> Icons.Default.BluetoothSearching
                                },
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = if (targetState == LockState.ALARM) Color.White
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "СТАТУС",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state == LockState.ALARM) Color.White.copy(0.7f) else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = when(state) {
                                    LockState.LOCKED -> "ЗАКРЫТО"
                                    LockState.UNLOCKED -> "ОТКРЫТО"
                                    LockState.ALARM -> "ТРЕВОГА!"
                                    else -> "ПОИСК..."
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (state == LockState.ALARM) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isConnected) "RSSI: $rssi" else "OFFLINE",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state == LockState.ALARM) Color.White else if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (state == LockState.ALARM) Color.White else if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(8.dp).padding(top = 6.dp)
                        ) {}
                    }
                }
            }

            Spacer(Modifier.weight(1.2f))

            // --- КВАДРАТНЫЕ КНОПКИ ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                val iconSize = 72.dp

                // Кнопка БЛОК (Активна при UNLOCKED)
                val canLock = isConnected && state == LockState.UNLOCKED
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.lock()
                    },
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    enabled = canLock,
                    shape = commonShape,
                    border = buttonBorder,
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(0.4f)
                    )
                ) {
                    Icon(
                        Icons.Default.Lock, null, Modifier.size(iconSize),
                        tint = if (canLock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.15f)
                    )
                }

                // Кнопка РАЗБЛОК (Активна при LOCKED)
                val canUnlock = isConnected && state == LockState.LOCKED
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.unlock()
                    },
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    enabled = canUnlock,
                    shape = commonShape,
                    border = buttonBorder,
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(0.4f)
                    )
                ) {
                    Icon(
                        Icons.Default.LockOpen, null, Modifier.size(iconSize),
                        tint = if (canUnlock) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(0.15f)
                    )
                }
            }

            // --- КНОПКА ТРЕВОГИ ---
            AnimatedVisibility(
                visible = state == LockState.ALARM,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(30.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.alarmOff()
                        },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        shape = commonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.GppBad, null, Modifier.size(32.dp), tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("СНЯТЬ ТРЕВОГУ", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // --- ИСТОРИЯ ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("       ИСТОРИЯ СОБЫТИЙ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    shape = commonShape,
                    border = buttonBorder
                ) {
                    Text(
                        text = logText,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }
        }
    }
}
