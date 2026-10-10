package com.ssqpick.designsystem.sketch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 手绘风格的抖动几何。
 *
 * 手绘感的关键是"每条线都不是直的"：真实的笔画在两点之间会有轻微起伏，
 * 收笔处还会略微出头。这里按边缘等距采样、沿法线方向施加小扰动来近似，
 * 比给整个矩形做随机位移更可控——形状仍然是矩形，只是边不那么直。
 *
 * **扰动必须是确定性的**：同一个 seed 永远得到同一条路径。否则每次重组
 * （尤其是摇号时每帧重组）线条都在抖，看着像坏掉的屏幕而不是手绘。
 */
object SketchGeometry {

    /** 抖动幅度相对边长的比例。手绘要"看得出但不晃"，0.012 左右最自然。 */
    private const val JITTER_RATIO = 0.012f

    /** 采样密度：每条边至少这么多段，越多越顺滑但越费。 */
    private const val SEGMENTS_PER_EDGE = 4

    /**
     * 生成一个手绘风格的圆角矩形路径。
     *
     * @param corner 圆角半径
     * @param jitter 抖动幅度（像素）。传 0 得到规整矩形
     * @param seed 决定抖动形态；同一 seed 结果稳定
     */
    fun roundRect(
        size: Size,
        corner: Float,
        jitter: Float,
        seed: Int,
    ): Path {
        val w = size.width
        val h = size.height
        val path = Path()
        if (w <= 0f || h <= 0f) return path

        val r = corner.coerceIn(0f, minOf(w, h) / 2f)
        val j = jitter.coerceAtLeast(0f)
        if (j <= 0.001f) {
            path.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = 0f,
                    top = 0f,
                    right = w,
                    bottom = h,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                ),
            )
            return path
        }

        val rnd = Random(seed)
        // 沿周长采样：对每条边与每个圆角各取若干点，整体走一圈
        val pts = ArrayList<Offset>(32)

        fun edge(x0: Float, y0: Float, x1: Float, y1: Float) {
            // 法线方向 = 边方向逆时针转 90°
            val dx = x1 - x0
            val dy = y1 - y0
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len <= 0.0001f) return
            val nx = -dy / len
            val ny = dx / len
            val segs = (len / 26f).toInt().coerceIn(SEGMENTS_PER_EDGE, 10)
            for (i in 0 until segs) {
                val t = i.toFloat() / segs
                // 中间段抖动大一些，两端收笔小一些 —— 更像真的运笔
                val envelope = kotlin.math.sin(t * Math.PI.toFloat()).coerceAtLeast(0.15f)
                val off = (rnd.nextFloat() - 0.5f) * 2f * j * envelope
                pts += Offset(x0 + dx * t + nx * off, y0 + dy * t + ny * off)
            }
        }

        fun arc(cx: Float, cy: Float, a0: Float, a1: Float) {
            val segs = 3
            for (i in 0 until segs) {
                val a = Math.toRadians((a0 + (a1 - a0) * i.toFloat() / segs).toDouble())
                val off = (rnd.nextFloat() - 0.5f) * 2f * j
                pts += Offset(
                    cx + (r + off) * cos(a).toFloat(),
                    cy + (r + off) * sin(a).toFloat(),
                )
            }
        }

        if (r <= 0.5f) {
            edge(0f, 0f, w, 0f)
            edge(w, 0f, w, h)
            edge(w, h, 0f, h)
            edge(0f, h, 0f, 0f)
        } else {
            edge(r, 0f, w - r, 0f)
            arc(w - r, r, -90f, 0f)
            edge(w, r, w, h - r)
            arc(w - r, h - r, 0f, 90f)
            edge(w - r, h, r, h)
            arc(r, h - r, 90f, 180f)
            edge(0f, h - r, 0f, r)
            arc(r, r, 180f, 270f)
        }

        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        path.close()
        return path
    }

    /**
     * 手绘直线：两端略微出头，中间有轻微起伏。
     *
     * 用作分隔线特别合适——规整的 1dp 分割线看起来像表格，手绘的像笔记本上划的。
     */
    fun line(from: Offset, to: Offset, jitter: Float, seed: Int): Path {
        val path = Path()
        val rnd = Random(seed)
        // 出头量：看起来像笔没停稳就抬起来了
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len <= 0.0001f) return path
        val ux = dx / len
        val uy = dy / len
        val nx = -uy
        val ny = ux
        val over = jitter * 1.6f

        val start = Offset(from.x - ux * over, from.y - uy * over)
        val end = Offset(to.x + ux * over, to.y + uy * over)

        val segs = (len / 30f).toInt().coerceIn(3, 8)
        path.moveTo(start.x, start.y)
        for (i in 1..segs) {
            val t = i.toFloat() / segs
            val envelope = kotlin.math.sin(t * Math.PI.toFloat())
            val off = (rnd.nextFloat() - 0.5f) * 2f * jitter * envelope
            path.lineTo(
                start.x + (end.x - start.x) * t + nx * off,
                start.y + (end.y - start.y) * t + ny * off,
            )
        }
        return path
    }

    /**
     * 手绘圆形（用于球的描边）。
     *
     * [wobble] 是半径的相对抖动量；0.03 时看起来像手画的圆，0.08 就明显歪了。
     */
    fun circle(center: Offset, radius: Float, wobble: Float, seed: Int): Path {
        val path = Path()
        val rnd = Random(seed)
        val segs = 26
        for (i in 0..segs) {
            val a = 2.0 * Math.PI * i / segs
            // 低频起伏：整体不圆，而不是逐点锯齿
            val wave = 1.0 + wobble * sin(a * 2.0 + rnd.nextDouble() * 0.4)
            val r = (radius * wave.coerceIn(0.86, 1.14)).toFloat()
            val x = center.x + r * cos(a).toFloat()
            val y = center.y + r * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /**
     * 由内容派生稳定 seed。
     *
     * 同一个标签永远画出同一条抖动线：界面不会在重组时"蠕动"，
     * 看起来才像真的画上去的，而不是在动。
     */
    fun seedOf(vararg parts: Any?): Int {
        var h = 17
        for (p in parts) {
            h = h * 31 + (p?.hashCode() ?: 0)
        }
        return h and 0x7FFFFFFF
    }
}
