package com.ssqpick.feature.machine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ssqpick.feature.machine.physics.Ball
import com.ssqpick.feature.machine.physics.DrumWorld
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D 摇号舞台。
 *
 * 为什么是 2D：先前用 `GLSurfaceView` 的 3D 方案在真机上一片空白——
 * `SurfaceView` 的 surface 由系统单独合成，既不能放进可滚动容器，又需要在正确的
 * 时机手动 `onResume()`，还额外依赖设备 GLES 3.0 与着色器编译。换成 Compose
 * `Canvas` 后这些失败面全部消失：没有独立 surface、没有 EGL、没有着色器，
 * 绘制与界面在同一套合成里——Compose 能画出界面，它就能画出机器。
 *
 * 物理仍复用 [DrumWorld]（纯 Kotlin，有独立单元测试），只是把三维位置正交投影
 * 到屏幕；z 轴用作深度，决定绘制顺序与视觉大小。这是"像 3D"而非"真 3D"：
 * 成本低、永远不黑屏、观感足够。
 */
@Composable
fun DrumStage(
    controller: MachineController,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var frame by remember { mutableLongStateOf(0L) }

    // withFrameNanos 与屏幕刷新同步；dt 夹上限，避免从后台切回时一个巨大的 dt
    // 把球甩出鼓外（物理层也有兜底，这里再防一层）。
    LaunchedEffect(controller) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    controller.advance(dt)
                }
                last = now
                frame++
            }
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val rim = MaterialTheme.colorScheme.outline
    val well = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.fillMaxSize()) {
        // 读一次 frame，建立绘制依赖，保证每帧重绘
        frame.hashCode()

        val w = size.width
        val h = size.height
        // 两台鼓并排
        val drumR = minOf(h * 0.34f, w * 0.19f)
        val redCenter = Offset(w * 0.29f, h * 0.44f)
        val blueCenter = Offset(w * 0.71f, h * 0.44f)

        drawDrum(controller.redWorld, redCenter, drumR, RED_BALL, ink, rim, well, measurer)
        drawDrum(controller.blueWorld, blueCenter, drumR, BLUE_BALL, ink, rim, well, measurer)

        // 摇号进度：一条极细的线。舞台里不引入第二种视觉语言（不用进度条控件）。
        controller.progress()?.let { (done, total) ->
            val y = h - 8f
            val half = w * 0.22f
            val left = w / 2f - half
            drawLine(rim, Offset(left, y), Offset(left + 2 * half, y), strokeWidth = 2f)
            drawLine(
                ink,
                Offset(left, y),
                Offset(left + 2 * half * (done.toFloat() / total.coerceAtLeast(1)), y),
                strokeWidth = 2f,
            )
        }
    }
}

/**
 * 画一台鼓。
 *
 * 顺序：鼓内底色 → 按 z 排序的球 → 玻璃鼓壳 → 随鼓旋转的刻度。
 * 球按 z 升序绘制（远的先画），近处的球更大并盖住远处的，从而产生深度感。
 */
private fun DrawScope.drawDrum(
    world: DrumWorld,
    center: Offset,
    radius: Float,
    ballColor: Color,
    ink: Color,
    rim: Color,
    well: Color,
    measurer: TextMeasurer,
) {
    drawCircle(color = well.copy(alpha = 0.6f), radius = radius, center = center)

    val balls = world.balls
    val ballR = radius * (world.ballRadius / world.drumRadius)

    // 深度排序。插入排序：49 个元素、帧间几乎有序，开销可忽略，
    // 关键是不产生每帧的装箱与列表分配。
    val order = orderBuffer(balls.size)
    for (i in balls.indices) order[i] = i
    for (i in 1 until balls.size) {
        val v = order[i]
        val zv = balls[v].z
        var j = i - 1
        while (j >= 0 && balls[order[j]].z > zv) {
            order[j + 1] = order[j]
            j--
        }
        order[j + 1] = v
    }

    for (k in 0 until balls.size) {
        val b = balls[order[k]]
        // 已出号落槽的球交给下方结果行展示，这里不再画
        if (b.settled) continue
        drawBall(b, center, radius, ballR, ballColor, measurer)
    }

    // 玻璃鼓壳：发丝线 + 左上一道高光弧。
    // 参考作品明确禁止 glow，所以这里是"线"而不是"光"。
    drawCircle(color = rim, radius = radius, center = center, style = Stroke(width = 1.6f))
    drawArc(
        color = Color.White.copy(alpha = 0.5f),
        startAngle = 200f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.84f, center.y - radius * 0.84f),
        size = Size(radius * 1.68f, radius * 1.68f),
        style = Stroke(width = radius * 0.06f),
    )

    // 三道随鼓旋转的刻度，让"在转"看得见
    val angle = world.angleDegrees()
    for (i in 0 until 3) {
        val a = Math.toRadians((angle + i * 120f).toDouble())
        val px = center.x + (radius * 0.93f * cos(a)).toFloat()
        val py = center.y + (radius * 0.93f * sin(a)).toFloat()
        drawCircle(color = rim.copy(alpha = 0.5f), radius = radius * 0.032f, center = Offset(px, py))
    }
}

/** 球的投影与绘制。z 越大表示越靠近观察者：更大、后画。 */
private fun DrawScope.drawBall(
    b: Ball,
    center: Offset,
    drumRadius: Float,
    ballRadius: Float,
    color: Color,
    measurer: TextMeasurer,
) {
    val sx = center.x + b.x * drumRadius
    val sy = center.y - b.y * drumRadius
    // 深度缩放 ±12%：足够读出前后关系又不夸张
    val depth = 1f + b.z * 0.12f
    val r = ballRadius * depth

    // 出号中的球向目标滑动并淡出，读起来像"落进出球槽"
    var alpha = 1f
    if (b.escaping) {
        val dx = b.x - b.attractX
        val dy = b.y - b.attractY
        val dz = b.z - b.attractZ
        alpha = (sqrt(dx * dx + dy * dy + dz * dz) * 2.4f).coerceIn(0f, 1f)
    }

    drawCircle(color = color.copy(alpha = alpha), radius = r, center = Offset(sx, sy))
    // 内侧一发丝环，让球不是一块纯色片
    drawCircle(
        color = Color.White.copy(alpha = 0.26f * alpha),
        radius = r * 0.8f,
        center = Offset(sx, sy),
        style = Stroke(width = r * 0.1f),
    )

    // 号码只在球足够大时画。鼓里 33 个球本就小，真实摇奖机上同样看不清滚动中的
    // 号码——所以这不是妥协而是正确取舍：把可读性留给下方结果行。
    if (r >= MIN_BALL_RADIUS_FOR_TEXT) {
        val layout = ballNumberLayout(measurer, b.number)
        val refRadius = layout.size.width / 2f
        val scale = (r * 0.8f) / refRadius
        withTransform({
            translate(sx, sy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f),
            )
        }
    }
}

private const val MIN_BALL_RADIUS_FOR_TEXT = 11f
private val RED_BALL = Color(0xFFE23A2E)
private val BLUE_BALL = Color(0xFF2C6BD4)

/**
 * 号码文本测量缓存。
 *
 * 号码只有 1..33 与 1..16 两组，字号样式固定，因此各测一次即可复用——
 * 每帧对 49 个球重新测量文本会明显拖慢绘制。
 */
private val numberLayoutCache = HashMap<Int, TextLayoutResult>()

private fun ballNumberLayout(measurer: TextMeasurer, number: Int): TextLayoutResult = numberLayoutCache.getOrPut(number) {
    measurer.measure(
        text = number.toString().padStart(2, '0'),
        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White),
    )
}

/** 复用的排序缓冲，避免每帧分配。 */
private var orderScratch = IntArray(64)

private fun orderBuffer(n: Int): IntArray {
    if (orderScratch.size < n) orderScratch = IntArray(n)
    return orderScratch
}
