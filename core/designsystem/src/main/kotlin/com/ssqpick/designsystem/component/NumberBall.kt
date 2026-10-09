package com.ssqpick.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 红球/蓝球的颜色 token。 */
object BallColors {
    val Red = Color(0xFFE23A2E)
    val Blue = Color(0xFF2C6BD4)
    val RedLight = Color(0xFFFF7A6B)
    val BlueLight = Color(0xFF6FA0FF)
}

/**
 * 一个摇奖球。
 *
 * 用径向渐变做出球体的高光，号码充当无障碍朗读文本（而不是装饰）。
 */
@Composable
fun NumberBall(
    number: Int,
    isBlue: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    dimmed: Boolean = false,
) {
    val base = if (isBlue) BallColors.Blue else BallColors.Red
    val light = if (isBlue) BallColors.BlueLight else BallColors.RedLight
    val brush = Brush.radialGradient(
        colors = if (dimmed) listOf(base.copy(alpha = 0.35f), base.copy(alpha = 0.35f))
        else listOf(light, base),
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
            .clearAndSetSemantics { contentDescription = if (isBlue) "蓝球 $number" else "红球 $number" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "%02d".format(number),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * (size / 44.dp.coerceAtLeast(1.dp)),
            ),
        )
    }
}

/** 一排球，间距一致。 */
@Composable
fun BallRow(
    numbers: List<Int>,
    isBlue: Boolean,
    modifier: Modifier = Modifier,
    ballSize: Dp = 44.dp,
    dimmed: Boolean = false,
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
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                NumberBall(number = n, isBlue = isBlue, size = ballSize, dimmed = dimmed)
            }
        }
    }
}

/** 小标签，例如"复式 8+3"。 */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceVariant) {
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
