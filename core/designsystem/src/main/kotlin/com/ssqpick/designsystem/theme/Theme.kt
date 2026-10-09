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
private val Ink = Color(0xFF0E1116)
private val Surface1 = Color(0xFF161B22)
private val Surface2 = Color(0xFF1F2630)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1B00),
    primaryContainer = Color(0xFF4A3300),
    onPrimaryContainer = Amber,
    secondary = BlueBall,
    onSecondary = Color.White,
    background = Ink,
    onBackground = Color(0xFFECEFF4),
    surface = Surface1,
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFB6BFCC),
    error = Color(0xFFFF6B6B),
)

// 摇号机是深色舞台，浅色主题只做兜底，不精心调
private val LightScheme = lightColorScheme(
    primary = Amber,
    secondary = BlueBall,
)

@Composable
fun SsqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = SsqTypography, content = content)
}

private val SsqTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

/** 球体尺寸的公共 token，界面里复用。 */
object SsqDimens {
    val ballSize = 44.dp
    val ballSizeSmall = 34.dp
    val screenPadding = 16.dp
}
