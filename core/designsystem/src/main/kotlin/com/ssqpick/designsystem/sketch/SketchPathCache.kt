package com.ssqpick.designsystem.sketch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path

/**
 * 抖动路径缓存。
 *
 * `drawBehind` 每帧都会执行，若每次都重新采样 30 个点、构造 Path 与点列表，
 * 在摇号这种持续重绘的界面上是纯浪费。这里按尺寸记忆：只有布局变了才重算。
 */
class SketchPathCache {

    private var lastKey: Long = Long.MIN_VALUE
    private var lastPath: Path = Path()

    private var lastCircleKey: Long = Long.MIN_VALUE
    private var lastCircle: Path = Path()

    fun roundRect(size: Size, corner: Float, jitter: Float, seed: Int): Path {
        val key = hash(size, corner, jitter, seed)
        if (key != lastKey) {
            lastPath = SketchGeometry.roundRect(size, corner, jitter, seed)
            lastKey = key
        }
        return lastPath
    }

    fun circle(center: Offset, radius: Float, wobble: Float, seed: Int): Path {
        val key = hash(center, radius, wobble, seed) * 31 + 7
        if (key != lastCircleKey) {
            lastCircle = SketchGeometry.circle(center, radius, wobble, seed)
            lastCircleKey = key
        }
        return lastCircle
    }

    private fun hash(vararg parts: Any?): Long {
        var h = 1125899906842597L
        for (p in parts) {
            h = h * 31 + (p?.hashCode() ?: 0)
        }
        return h
    }
}
