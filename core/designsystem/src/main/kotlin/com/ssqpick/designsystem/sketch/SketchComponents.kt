package com.ssqpick.designsystem.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 手绘风格的组件集。
 *
 * 风格约定（整套界面共用）：
 * - 纸面用暖白，边框是深墨色手绘线，线条两端略微出头；
 * - 元素带 ±1° 左右的轻微倾斜，像随手贴在纸上；
 * - **文字始终保持清晰**。手绘只作用于边框与装饰，不作用于字形——
 *   号码的可读性是这个应用的底线，不能为了风格牺牲。
 */
object SketchPalette {
    val Paper = Color(0xFFFBF8F2)
    val Canvas = Color(0xFFF1ECE2)
    val Ink = Color(0xFF23201C)
    val InkSoft = Color(0xFF6E675E)
    val InkFaint = Color(0xFFB9B0A3)
    val Highlighter = Color(0xFFFFE9A8)
    val HighlighterMint = Color(0xFFBFF0DC)
    val RedInk = Color(0xFFD8453A)
    val BlueInk = Color(0xFF3A6FD8)
    val RedBall = Color(0xFFE23A2E)
    val BlueBall = Color(0xFF2C6BD4)
}

/**
 * 纸面背景。
 *
 * 除了底色还撒了一小撮极淡的纤维斑点——纯色在手机上容易显得像塑料，
 * 有一点颗粒才像纸。斑点位置由 seed 决定、数量固定，不会闪。
 */
@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    seed: Int = 20261010,
    speckles: Int = 80,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas)
            .drawBehind {
                val rnd = Random(seed)
                repeat(speckles) {
                    drawCircle(
                        color = SketchPalette.InkFaint.copy(alpha = 0.10f),
                        radius = 0.6f + rnd.nextFloat() * 1.4f,
                        center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                    )
                }
            },
    )
}

/**
 * 手绘卡片：纸面 + 墨线描边，可带轻微倾斜。
 *
 * @param seed 决定抖动形态。**同一内容请传相同的 seed**，否则每次重组线条都在变，
 *   看着像在动而不是画出来的。
 * @param tilt 倾斜角度。卡片之间给一点差异，比全部端正更像手摆的。
 */
@Composable
fun SketchCard(
    modifier: Modifier = Modifier,
    seed: Any? = null,
    container: Color = SketchPalette.Paper,
    ink: Color = SketchPalette.Ink,
    corner: Dp = 14.dp,
    strokeWidth: Dp = 1.6.dp,
    jitter: Dp = 1.7.dp,
    tilt: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cache = remember { SketchPathCache() }
    val s = SketchGeometry.seedOf(seed)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (tilt != 0f) Modifier.rotate(tilt) else Modifier)
            .drawBehind {
                val path = cache.roundRect(size, corner.toPx(), jitter.toPx(), s)
                drawPath(path, container)
                drawPath(
                    path = path,
                    color = ink.copy(alpha = 0.85f),
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
                )
            }
            .padding(16.dp),
        content = content,
    )
}

/** 手绘分隔线：两端略微出头，中间有轻微起伏。 */
@Composable
fun SketchDivider(
    modifier: Modifier = Modifier,
    ink: Color = SketchPalette.InkFaint,
    strokeWidth: Dp = 1.4.dp,
    seed: Any? = null,
) {
    val s = SketchGeometry.seedOf(seed)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .drawBehind {
                val path = SketchGeometry.line(
                    from = Offset(0f, size.height / 2f),
                    to = Offset(size.width, size.height / 2f),
                    jitter = 0.9f,
                    seed = s,
                )
                drawPath(
                    path = path,
                    color = ink,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
                )
            },
    )
}

/** 手绘主按钮。 */
@Composable
fun SketchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    seed: Any? = null,
    filled: Boolean = true,
    tilt: Float = -0.4f,
) {
    val cache = remember { SketchPathCache() }
    val s = SketchGeometry.seedOf(seed, label)
    val container = when {
        !enabled -> SketchPalette.Canvas
        filled -> SketchPalette.Ink
        else -> SketchPalette.Paper
    }
    val contentColor = when {
        !enabled -> SketchPalette.InkFaint
        filled -> SketchPalette.Paper
        else -> SketchPalette.Ink
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .rotate(tilt)
            .drawBehind {
                val path = cache.roundRect(size, 12.dp.toPx(), 1.6.dp.toPx(), s)
                drawPath(path, container)
                drawPath(
                    path = path,
                    color = if (enabled) SketchPalette.Ink else SketchPalette.InkFaint,
                    style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // 读屏需要明确的按钮角色与文案：Box + clickable 默认只报"可点击"。
            // mergeDescendants 避免文案与内部 Text 重复朗读。
            .semantics(mergeDescendants = true) {
                contentDescription = if (enabled) label else "$label，不可用"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/**
 * 手绘小标签（投注方式、注数说明）。
 *
 * @param fill 非空时是荧光笔涂在文字后面的感觉；null 则是空心墨线框。
 */
@Composable
fun SketchChip(
    label: String,
    modifier: Modifier = Modifier,
    seed: Any? = null,
    fill: Color? = SketchPalette.Highlighter,
    tilt: Float = -1.2f,
) {
    val cache = remember { SketchPathCache() }
    val s = SketchGeometry.seedOf(seed, label)
    Box(
        modifier = modifier
            .rotate(tilt)
            .drawBehind {
                val path = cache.roundRect(size, 7.dp.toPx(), 1.3.dp.toPx(), s)
                if (fill != null) {
                    drawPath(path, fill.copy(alpha = 0.9f))
                } else {
                    drawPath(
                        path = path,
                        color = SketchPalette.Ink.copy(alpha = 0.7f),
                        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SketchPalette.Ink)
    }
}

/** 小节标题：下方带一条手绘下划线。 */
@Composable
fun SketchSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = SketchPalette.Ink)
        Spacer(Modifier.height(3.dp))
        SketchDivider(ink = SketchPalette.InkFaint, strokeWidth = 1.2.dp, seed = text)
    }
}

/**
 * 给任意布局加一个手绘外框。
 *
 * 用于那些不该用 [SketchCard] 的小方块（投注方式、号码格等）——
 * 它们需要有自己的点击态与尺寸，但外框要保持同一套手绘语言。
 *
 * @param filled true 填墨色（选中态），false 纸面 + 墨线
 */
@Composable
fun Modifier.sketchBox(
    seed: Any?,
    filled: Boolean,
    enabled: Boolean = true,
    corner: Dp = 12.dp,
    strokeWidth: Dp = 1.6.dp,
    jitter: Dp = 1.5.dp,
    onClick: (() -> Unit)? = null,
): Modifier {
    val cache = remember { SketchPathCache() }
    val s = SketchGeometry.seedOf(seed)
    val container = if (filled) SketchPalette.Ink else SketchPalette.Paper
    val ink = if (enabled) SketchPalette.Ink else SketchPalette.InkFaint
    return this
        .drawBehind {
            val path = cache.roundRect(size, corner.toPx(), jitter.toPx(), s)
            drawPath(path, container)
            drawPath(
                path = path,
                color = ink.copy(alpha = if (filled) 0f else 0.85f),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )
}

/** 手绘分布条：标签 + 抖动轨道 + 实心填充 + 右侧数值，用于命中分布这类小统计。 */
@Composable
fun SketchBarRow(
    label: String,
    /** 实测占比 0..1，决定填充长度（各行共用一把尺子，调用方先归一好）。 */
    fraction: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    subText: String? = null,
    seed: Any? = null,
) {
    val cache = remember { SketchPathCache() }
    val s = SketchGeometry.seedOf(seed, label)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SketchPalette.Ink)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(13.dp)
                .drawBehind {
                    val track = cache.roundRect(size, 6.dp.toPx(), 1.2.dp.toPx(), s)
                    drawPath(track, SketchPalette.Paper)
                    drawPath(
                        path = track,
                        color = SketchPalette.Ink.copy(alpha = 0.5f),
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    // 填充是实心块：它是"量"不是"线"，不用抖
                    val fw = (size.width - 5f) * fraction.coerceIn(0f, 1f)
                    if (fw > 2f) {
                        drawRect(
                            color = SketchPalette.Ink.copy(alpha = 0.78f),
                            topLeft = Offset(2.5f, 2.5f),
                            size = Size(fw, size.height - 5f),
                        )
                    }
                },
        )
        if (subText != null) {
            Spacer(Modifier.height(2.dp))
            Text(subText, style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkFaint)
        }
    }
}

/** 一行「标签 + 数值」，用于摘要信息。 */
@Composable
fun SketchMetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = SketchPalette.Ink,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SketchPalette.InkSoft)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** 行内小按钮：多操作并排（记录/备注/删除、全选/清空）用。 */
@Composable
fun SketchMiniButton(
    label: String,
    seed: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .sketchBox(seed = seed, filled = filled, enabled = enabled, onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> SketchPalette.InkFaint
                filled -> SketchPalette.Paper
                else -> SketchPalette.InkSoft
            },
            maxLines = 1,
        )
    }
}

/**
 * 手绘折线图：把一串值映射成抖动折线。
 *
 * @param baseline 参考线（如 0 或均值）；传了才会画。
 * @param includeZero 是否把 0 纳入值域——账本折线要看得出"0 以上/以下"。
 * 每帧的抖动由 seed 决定，重组不会闪。
 */
@Composable
fun SketchLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    baseline: Float? = null,
    includeZero: Boolean = false,
    seed: Any? = null,
    lineColor: Color = SketchPalette.Ink,
    baselineColor: Color = SketchPalette.InkFaint,
) {
    val s = SketchGeometry.seedOf(seed, "chart")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .drawBehind {
                if (values.size < 2) return@drawBehind
                var lo = values.min()
                var hi = values.max()
                baseline?.let {
                    lo = minOf(lo, it)
                    hi = maxOf(hi, it)
                }
                if (includeZero) {
                    lo = minOf(lo, 0f)
                    hi = maxOf(hi, 0f)
                }
                val span = (hi - lo).let { if (it < 1e-6f) 1f else it }
                val pad = 6f
                val w = size.width - 2 * pad
                val h = size.height - 2 * pad
                fun y(v: Float) = pad + h * (1f - (v - lo) / span)

                val rnd = Random(s)
                val path = Path()
                values.forEachIndexed { i, v ->
                    val px = pad + w * i / (values.size - 1) + (rnd.nextFloat() - 0.5f) * 1.6f
                    val py = y(v) + (rnd.nextFloat() - 0.5f) * 1.6f
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                baseline?.let {
                    val by = y(it)
                    val bp = SketchGeometry.line(
                        from = Offset(pad - 2f, by),
                        to = Offset(size.width - pad + 2f, by),
                        jitter = 0.8f,
                        seed = s * 31 + 7,
                    )
                    drawPath(bp, baselineColor, style = Stroke(width = 1.2f))
                }
                drawPath(path, lineColor, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
            },
    )
}
