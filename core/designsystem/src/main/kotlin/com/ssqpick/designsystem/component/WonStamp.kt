package com.ssqpick.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssqpick.designsystem.sketch.SketchGeometry
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchPathCache

/**
 * 红色中奖章：半透明纸底 + 双圈红章，盖在号码上。
 *
 * 金额为 0（浮动奖公告缺失）时只显示奖级，不编造金额。
 * 章是水印层，不改变球的号码字形；读屏通过 contentDescription 给出同样信息。
 */
@Composable
fun WonStamp(
    tierLabel: String?,
    amountYuan: Long,
    modifier: Modifier = Modifier,
) {
    val red = SketchPalette.RedInk
    val cache = remember { SketchPathCache() }
    val seed = SketchGeometry.seedOf("won-stamp", tierLabel, amountYuan)
    val amountText = if (amountYuan > 0) {
        "¥${"%,d".format(amountYuan)}"
    } else {
        "金额待公告"
    }
    val spoken = buildString {
        append("中奖印章")
        tierLabel?.let { append("：$it") }
        if (amountYuan > 0) append("，$amountYuan 元") else append("，金额待公告")
    }

    Box(
        modifier = modifier
            .rotate(-7f)
            .drawBehind {
                val border = 2.3.dp.toPx()
                val path = cache.roundRect(size, 11.dp.toPx(), 2.4.dp.toPx(), seed)
                // 只留一层极淡纸底：章是水印，号码必须还能读
                drawPath(path, SketchPalette.Paper.copy(alpha = 0.10f))
                drawPath(
                    path = path,
                    color = red.copy(alpha = 0.66f),
                    style = Stroke(width = border, cap = StrokeCap.Round),
                )
                val inset = 4.dp.toPx()
                drawRoundRect(
                    color = red.copy(alpha = 0.30f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(7.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "中奖",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = red.copy(alpha = 0.34f),
            )
            Text(
                if (tierLabel.isNullOrBlank()) amountText else "$tierLabel · $amountText",
                style = MaterialTheme.typography.labelMedium,
                color = red.copy(alpha = 0.72f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
