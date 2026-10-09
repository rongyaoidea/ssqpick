package com.ssqpick.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 红球/蓝球的颜色 token。 */
object BallColors {
    val Red = Color(0xFFE23A2E)
    val Blue = Color(0xFF2C6BD4)
    val RedLight = Color(0xFFFF7A6B)
    val BlueLight = Color(0xFF6FA0FF)
    val UnselectedRed = Color(0xFF3A1F1E)
    val UnselectedBlue = Color(0xFF1E2B42)
}

/**
 * 一个摇奖球。
 *
 * - [selected] 为 false 时渲染暗色轮廓态，用于"未选中"的号码；
 * - 字号直接由球直径推导，不再用 `44.dp` 基准做比例缩放（小号球会被压得不可读）；
 * - 无障碍朗读的是"红球 07"而不是装饰本身。
 */
@Composable
fun NumberBall(
    number: Int,
    isBlue: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    selected: Boolean = true,
) {
    val base = if (isBlue) BallColors.Blue else BallColors.Red
    val light = if (isBlue) BallColors.BlueLight else BallColors.RedLight
    val dim = if (isBlue) BallColors.UnselectedBlue else BallColors.UnselectedRed

    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = tween(140),
        label = "ballScale",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(140),
        label = "ballRing",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 0.dp else 1.dp,
        animationSpec = tween(140),
        label = "ballRingWidth",
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.radialGradient(listOf(light, base))
                } else {
                    Brush.radialGradient(listOf(dim, dim))
                },
            )
            .border(ringWidth, ring, CircleShape)
            .clearAndSetSemantics {
                contentDescription = if (isBlue) "蓝球 $number" else "红球 $number"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "%02d".format(number),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // 字号随球直径线性缩放：44dp -> 约 17sp，34dp -> 约 13sp
            fontSize = (size.value * 0.385f).sp,
            maxLines = 1,
        )
    }
}

/**
 * 一排等距的球，带出场动画。
 *
 * [placeholderCount] 多于 [numbers.size] 时，用空槽位表示"还没摇出来"。
 */
@Composable
fun BallRow(
    numbers: List<Int>,
    isBlue: Boolean,
    modifier: Modifier = Modifier,
    ballSize: Dp = 44.dp,
    placeholderCount: Int = numbers.size,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(placeholderCount) { index ->
            val n = numbers.getOrNull(index)
            if (n == null) {
                Box(
                    Modifier
                        .size(ballSize)
                        .alpha(0.35f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                NumberBall(number = n, isBlue = isBlue, size = ballSize)
            }
        }
    }
}

/** 小标签，例如"复式 8+3"。 */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
