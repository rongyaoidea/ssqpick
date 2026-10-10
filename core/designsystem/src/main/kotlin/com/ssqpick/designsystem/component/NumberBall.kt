package com.ssqpick.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssqpick.designsystem.sketch.SketchGeometry
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchPathCache
import com.ssqpick.designsystem.theme.SsqMotion

/**
 * 红球 / 蓝球的语义色。
 *
 * 这是整个界面**唯一**的饱和色来源。参考作品的纪律是"黑白灰组件 + 单一强调色"；
 * 在彩票应用里红蓝本身就是语义（红球/蓝球），所以让它们独家承担全部色彩，
 * 界面其余部分保持中性——球因此成为画面里唯一醒目的东西。
 */
object BallColors {
    val Red = Color(0xFFE23A2E)
    val Blue = Color(0xFF2C6BD4)
    val RedLift = Color(0xFFFF6A5A)
    val BlueLift = Color(0xFF6BA0F5)
}

/**
 * 一个号码球。
 *
 * 动效遵循参考作品的纪律："springs everywhere, a tiny overshoot at most"——
 * 弹簧而非缓动曲线，过冲极小（阻尼 0.86），明确禁止 bounce；
 * 号码变化时配一次极短模糊（"content swaps with a quick blur"），
 * 避免两个数字在同一位置重叠。
 */
@Composable
fun NumberBall(
    number: Int,
    isBlue: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    selected: Boolean = true,
    /** 胆码标记：选中态外再套一圈球色手绘环。字形不动，只加装饰。 */
    bold: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val base = if (isBlue) BallColors.Blue else BallColors.Red
    val lift = if (isBlue) BallColors.BlueLift else BallColors.RedLift

    // 球径是固定 dp，字必须跟着球走而不是跟着系统字体走：
    // 直接写 .sp 会在系统字体放大（无障碍 1.3x+）时让数字溢出球体。
    val density = LocalDensity.current

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 选中态：弹簧推到目标，带一点过冲；未选中压到 0.88 倍
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            selected -> 1f
            else -> 0.88f
        },
        animationSpec = spring(
            dampingRatio = SsqMotion.SPRING_DAMPING,
            stiffness = SsqMotion.SPRING_STIFFNESS,
        ),
        label = "ballScale",
    )

    // 号码切换时的短模糊：从 7dp 收敛到 0，器一瞬即逝，不拖节奏
    val blurRadius = remember { Animatable(0f) }
    var lastNumber by remember { mutableIntStateOf(number) }
    LaunchedEffect(number) {
        if (number != lastNumber) {
            lastNumber = number
            blurRadius.snapTo(7f)
            blurRadius.animateTo(0f, tween(durationMillis = SsqMotion.SWAP_BLUR_MS))
        }
    }

    val fill = if (selected) {
        // 极轻微的材质渐变：球是实体、不是装饰，不违反"禁止 UI 控件渐变"
        Brush.radialGradient(
            colors = listOf(lift.copy(alpha = 0.5f), base),
            radius = size.value * 0.85f,
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }

    val ringCache = remember { SketchPathCache() }
    // 胆码外环另起一份缓存：单槽缓存一帧画两条不同路径会次次失配
    // （鼓面双鼓共用一份缓存是同一个教训，见 DrumStage）。
    val boldCache = remember { SketchPathCache() }
    // 描边宽度在 drawBehind 内部换算——只有 DrawScope 才是 Density 作用域
    val strokeDp: Dp = if (selected) 1.1.dp else 1.8.dp
    val ringInk = if (selected) SketchPalette.Ink else SketchPalette.InkSoft
    val ringSeed = remember(number, isBlue) { SketchGeometry.seedOf(number, isBlue) }
    val boldSeed = remember(number, isBlue) { SketchGeometry.seedOf(number, isBlue, "bold") }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            // 手绘描边：实心圆保持规整（号码要读得清），外面套一圈抖动的墨线。
            // 圆心相对半径的抖动 0.035 看起来像手画的，再大就明显歪了。
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawPath(
                    path = ringCache.circle(center, r, wobble = 0.035f, seed = ringSeed),
                    color = if (selected) ringInk.copy(alpha = 0.55f) else ringInk,
                    style = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round),
                )
                if (bold && selected) {
                    drawPath(
                        path = boldCache.circle(center, r + 3.2.dp.toPx(), wobble = 0.05f, seed = boldSeed),
                        color = base,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(if (isBlue) "蓝球 " else "红球 ")
                    append(number)
                    if (bold && selected) append("，胆码")
                    if (!selected) append("，未选中")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "%02d".format(number),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // 字号随球直径线性推导：42dp -> 约 16sp，36dp -> 约 14sp。
            // 除以 fontScale：物理尺寸恒定，系统放大字体时数字不会溢出球。
            fontSize = (size.value * 0.385f / density.fontScale).sp,
            // API 31+ 生效；更低版本自动忽略，不影响可读性
            modifier = Modifier.blur(blurRadius.value.dp, BlurredEdgeTreatment.Unbounded),
        )
    }
}

/**
 * 一排等距球，空槽位表示"还没摇出来"。
 *
 * 用空槽位而不是隐藏：用户看到"6 个位置、出了 3 个"，比只看到 3 个球清楚得多。
 */
@Composable
fun BallRow(
    numbers: List<Int>,
    isBlue: Boolean,
    modifier: Modifier = Modifier,
    ballSize: Dp = 42.dp,
    placeholderCount: Int = numbers.size,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(placeholderCount) { index ->
            val n = numbers.getOrNull(index)
            if (n == null) {
                Box(
                    Modifier
                        .size(ballSize)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            } else {
                NumberBall(number = n, isBlue = isBlue, size = ballSize)
            }
        }
    }
}
