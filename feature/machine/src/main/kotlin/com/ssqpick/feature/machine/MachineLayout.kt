package com.ssqpick.feature.machine

import com.ssqpick.feature.machine.gl.DrumRenderSpec

/**
 * 摇号机的舞台布局：两台鼓的位置、半径、球大小、出口槽位。
 *
 * 渲染器和控制器共用这一份配置——槽位坐标如果两边各写一遍，
 * 迟早会出现"球停在槽外"这类难查的 bug。
 */
object MachineLayout {

    /** 红鼓（33 球，选 6~10 个）。 */
    val RED = DrumRenderSpec(
        centerX = -2.5f,
        centerY = 0.95f,
        centerZ = 0f,
        drumRadius = 1f,
        ballScale = 0.25f,
        ballCount = 33,
        slotCount = 6,
        slotSpacing = 0.60f,
        kind = DrumRenderSpec.Kind.RED,
    )

    /** 蓝鼓（16 球，选 1~4 个）。 */
    val BLUE = DrumRenderSpec(
        centerX = 2.5f,
        centerY = 0.95f,
        centerZ = 0f,
        drumRadius = 1f,
        ballScale = 0.30f,
        ballCount = 16,
        slotCount = 1,
        slotSpacing = 0.60f,
        kind = DrumRenderSpec.Kind.BLUE,
    )

    /** 第 [index] 个出口槽在鼓局部坐标系里的目标点。 */
    fun slotTarget(spec: DrumRenderSpec, index: Int): FloatArray {
        val half = (spec.slotCount - 1) / 2f
        return floatArrayOf(
            (index - half) * spec.slotSpacing,
            -spec.drumRadius - spec.ballScale * 2.1f,
            -spec.ballScale * 1.4f,
        )
    }
}
