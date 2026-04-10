package com.firsov.bluetoothalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = MutedBlue,
    onPrimary = Color.White,
    primaryContainer = SandStone.copy(alpha = 0.4f),

    secondary = MutedSage,
    onSecondary = Color.White,
    secondaryContainer = SandStone.copy(alpha = 0.4f),

    background = PowderWhite,
    surface = WarmBeige,
    onSurface = SoftCocoa,
    outline = SandStone,
    error = AlarmCrimson
)

private val DarkColorScheme = darkColorScheme(
    primary = ColdSteel,
    onPrimary = CarbonBlack,
    primaryContainer = GraphiteGrey,

    secondary = MetalSilver,
    onSecondary = CarbonBlack,
    secondaryContainer = GraphiteGrey,

    background = CarbonBlack,
    surface = GraphiteGrey,
    onSurface = ColdSteel,
    outline = MetalSilver.copy(alpha = 0.3f),
    error = AlarmCrimson
)

@Composable
fun BluetoothAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
