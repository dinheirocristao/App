package br.com.autotdah.regulacaoproduzmais.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Brand {
    val Navy = Color(0xFF001030)
    val NavySoft = Color(0xFF0C1F45)
    val NavyLine = Color(0xFF1E3260)
    val Orange = Color(0xFFF68A00)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF2F4F8)
    val Card = Color(0xFFFFFFFF)
    val Field = Color(0xFFE8EBF1)
    val TextDark = Color(0xFF14203A)
    val Muted = Color(0xFF66708A)
    val MutedOnDark = Color(0xFFB4BED3)
    val Red = Color(0xFFE53935)
    val Green = Color(0xFF22B455)
}

@Composable
fun AutoTdahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand.Orange,
            onPrimary = Brand.White,
            secondary = Brand.Navy,
            onSecondary = Brand.White,
            background = Brand.Surface,
            onBackground = Brand.TextDark,
            surface = Brand.Card,
            onSurface = Brand.TextDark,
            surfaceVariant = Brand.Field,
            error = Brand.Red,
        ),
        typography = Typography(),
        content = content,
    )
}
