package com.ssqpick.feature.machine.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils

/**
 * 号码球贴图图集。
 *
 * 33 个红球 + 16 个蓝球的号码牌打进一张 8×8 网格的 1024×1024 纹理。
 * 这样 49 个球可以一次实例化绘制完成，同时每个球都有自己清晰的号码。
 *
 * 图集单元（cell）索引：
 * - 红球 1..33 → 0..32
 * - 蓝球 1..16 → 33..48
 */
object BallTextures {

    const val COLS = 8
    const val ROWS = 8
    const val CELL = 128
    const val RED_BALLS = 33
    const val BLUE_BALLS = 16

    const val RED_COLOR = 0xFFE23A2E.toInt()
    const val BLUE_COLOR = 0xFF2C6BD4.toInt()

    fun atlasTextureId(): Int {
        val w = COLS * CELL
        val h = ROWS * CELL
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (n in 1..RED_BALLS) drawCell(canvas, n - 1, RED_COLOR, n)
        for (n in 1..BLUE_BALLS) drawCell(canvas, RED_BALLS + n - 1, BLUE_COLOR, n)

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()
        return ids[0]
    }

    /** 一个球面网格上号码牌实际占用的 UV 矩形。缩小一点以避开相邻 cell 的边缘渗色。 */
    fun cellUv(index: Int): FloatArray {
        val cx = index % COLS
        val cy = index / COLS
        val du = 1f / COLS
        // 图集行自下而上还是自上而下取决于贴图坐标系，这里统一按"行号从上往下"处理
        val v0 = 1f - (cy + 1) * du
        val inset = 0.5f / CELL
        return floatArrayOf(cx * du + inset, v0 + inset, du - 2 * inset, du - 2 * inset)
    }

    private fun drawCell(canvas: Canvas, index: Int, baseColor: Int, number: Int) {
        val x = (index % COLS) * CELL
        val y = (index / COLS) * CELL

        // 底色整格铺满，相邻格之间不留缝
        canvas.drawRect(
            x.toFloat(), y.toFloat(),
            (x + CELL).toFloat(), (y + CELL).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor },
        )

        // 中央白色圆牌，稍微小于格子，两格交界处仍是纯底色
        val pad = CELL * 0.19f
        canvas.drawCircle(
            x + CELL / 2f, y + CELL / 2f, CELL / 2f - pad,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
        )

        val tp = textPaint(number)
        canvas.drawText(
            number.toString(),
            x + CELL / 2f,
            y + CELL / 2f - (tp.fontMetrics.ascent + tp.fontMetrics.descent) / 2f,
            tp,
        )
    }

    private fun textPaint(number: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1A1A.toInt()
        textSize = if (number < 10) CELL * 0.50f else CELL * 0.40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun delete(id: Int) = GLES30.glDeleteTextures(1, intArrayOf(id), 0)
}
