package com.ssqpick.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val RedBall = Color(0xFFE23A2E)
private val BlueBall = Color(0xFF2C6BD4)
private val Amber = Color(0xFFFFC24B)

private val Ink = Color(0xFF0B0E13)
private val Surface1 = Color(0xFF141920)
private val Surface2 = Color(0xFF1B222C)
private val Surface3 = Color(0xFF232B37)
private val Outline = Color(0xFF37414F)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = Color(0xFF4A3300),
    onPrimaryContainer = Amber,
    secondary = BlueBall,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF12283F),
    onSecondaryContainer = Color(0xFFBBD6FF),
    tertiary = RedBall,
    onTertiary = Color.White,
    background = Ink,
    onBackground = Color(0xFFECEFF4),
    surface = Surface1,
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFAEB8C6),
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    surfaceContainerHighest = Color(0xFF2B3441),
    outline = Outline,
    outlineVariant = Color(0xFF2A333F),
    error = Color(0xFFFF6B6B),
)

// 摇号机主视觉是深色舞台；浅色主题只做兜底，不做精细调校
private val LightScheme = lightColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B00),
    secondary = BlueBall,
    tertiary = RedBall,
)

/**
 * 应用主题。
 *
 * 只在最外层（MainActivity）调用一次——各 feature 屏幕**不要**再包一层，
 * 否则 MaterialTheme 会被重复解析，`LocalView` 上做状态栏配色的副作用也会跑两遍。
 */
@Composable
fun SsqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = SsqTypography, content = content)
}

private val SsqTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

/** 通用尺寸 token。 */
object SsqDimens {
    val screenPadding = 16.dp
    val ballSize = 44.dp
    val ballSizeCompact = 34.dp
    val ballSizeSmall = 30.dp
    val stageHeight = 340.dp
    val cardGap = 12.dp
}
