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

/**
 * 配色策略：黑白灰的界面控件 + 唯一的饱和色来自球本身。
 *
 * 这是从两支高赞的 "code-based UI motion" 作品里抄来的纪律：浅暖灰画布、
 * 黑白组件、单一强调色，**不在 UI 控件上使用装饰性渐变**。彩票应用里
 * 红与蓝本身就是语义色（红球/蓝球），所以让它们独家承担全部色彩，
 * 界面其余部分保持中性——这样球是画面里唯一的彩色，注意力自然落到号码上。
 */
private object Palette {
    // 浅色（主）：纸墨配色，与 designsystem 的 SketchPalette 保持一致
    val Paper = Color(0xFFFBF8F2)
    val Card = Color(0xFFFBF8F2)
    val Canvas = Color(0xFFF1ECE2)
    val Ink = Color(0xFF23201C)
    val InkSoft = Color(0xFF6E675E)
    val Hairline = Color(0xFFB9B0A3)
    val Well = Color(0xFFEAE4D8)
    val Alert = Color(0xFFB3261E)

    // 深色：同一套骨相的夜间版本
    val Night = Color(0xFF111013)
    val NightCard = Color(0xFF1B1A1E)
    val NightInk = Color(0xFFF2EFEA)
    val NightInkSoft = Color(0xFF9A958E)
    val NightHairline = Color(0xFF2C2A30)
    val NightWell = Color(0xFF232127)
}

private val LightScheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Card,
    primaryContainer = Palette.Ink,
    onPrimaryContainer = Palette.Card,
    secondary = Palette.InkSoft,
    onSecondary = Palette.Card,
    secondaryContainer = Palette.Well,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Ink,
    onTertiary = Palette.Card,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Well,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = Palette.Card,
    surfaceContainerLow = Palette.Card,
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Palette.Well,
    surfaceContainerHighest = Palette.Well,
    outline = Palette.Hairline,
    outlineVariant = Palette.Hairline,
    error = Palette.Alert,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.NightInk,
    onPrimary = Palette.Night,
    primaryContainer = Palette.NightInk,
    onPrimaryContainer = Palette.Night,
    secondary = Palette.NightInkSoft,
    onSecondary = Palette.Night,
    secondaryContainer = Palette.NightWell,
    onSecondaryContainer = Palette.NightInk,
    tertiary = Palette.NightInk,
    onTertiary = Palette.Night,
    background = Palette.Night,
    onBackground = Palette.NightInk,
    surface = Palette.NightCard,
    onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightWell,
    onSurfaceVariant = Palette.NightInkSoft,
    surfaceContainerLowest = Palette.Night,
    surfaceContainerLow = Palette.NightCard,
    surfaceContainer = Palette.NightCard,
    surfaceContainerHigh = Palette.NightWell,
    surfaceContainerHighest = Palette.NightWell,
    outline = Palette.NightHairline,
    outlineVariant = Palette.NightHairline,
    error = Color(0xFFFF897D),
)

/**
 * 应用主题。只在 MainActivity 包一层，feature 屏幕不要再包。
 */
@Composable
fun SsqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = SsqTypography,
        content = content,
    )
}

/**
 * 单一字族、克制的字重变化。
 *
 * 参考作品的要求是 "one clean UI font"——混排两套字体会立刻显出拼凑感，
 * 所以这里只调字号与字重，不换字族。
 */
private val SsqTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

/** 尺寸 token。 */
object SsqDimens {
    val screenPadding = 16.dp
    val ballSize = 42.dp
    val ballSizeCompact = 36.dp
    val ballSizeSmall = 28.dp

    /** 舞台（双鼓）高度；结果行在其下方，单独占位。 */
    val drumStageHeight = 200.dp
    val resultRowHeight = 62.dp

    val cardGap = 12.dp
}

/**
 * 动效参数。
 *
 * 参考作品的纪律是 "springs everywhere, a tiny overshoot at most"——
 * 用弹簧而不是缓动曲线，且过冲要极小（阻尼 0.86 左右）；明确禁止 bounce。
 */
object SsqMotion {
    const val SPRING_DAMPING = 0.86f
    const val SPRING_STIFFNESS = 900f

    /** "content swaps with a quick blur"：内容切换的模糊时长。 */
    const val SWAP_BLUR_MS = 140

    /** 快速位移，用于出号等需要利落的动作。 */
    const val QUICK_MS = 170
}
