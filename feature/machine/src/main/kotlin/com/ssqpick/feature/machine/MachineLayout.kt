package com.ssqpick.feature.machine

/**
 * 一台鼓的显示配置。
 *
 * 从已删除的 GL 渲染器里搬过来的——2D 舞台同样需要"鼓心/半径/球半径"这组常量，
 * 而槽位与尺寸只应有一处定义。渲染层（Canvas）与逻辑层（Controller）共用它，
 * 避免两边各写一遍导致"球停在槽外"这类难查的 bug。
 */
data class DrumRenderSpec(
    /** 鼓心在**鼓局部坐标系**里的位置（物理模拟始终以鼓心为原点）。 */
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    /** 鼓半径（局部单位，1.0 表示占满一个单位球）。 */
    val drumRadius: Float,
    /** 球半径（局部单位）。 */
    val ballScale: Float,
    val ballCount: Int,
    /** 出号槽数量：红 6 个、蓝 1 个。 */
    val slotCount: Int,
    /** 相邻槽间距（局部单位）。 */
    val slotSpacing: Float,
    val kind: Kind,
) {
    enum class Kind { RED, BLUE }
}

/**
 * 摇号机的舞台布局。
 *
 * 渲染器和控制器共用这一份配置。
 */
object MachineLayout {

    /** 红鼓：33 球，出 6 个。 */
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

    /** 蓝鼓：16 球，出 1 个。 */
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

    /**
     * 第 [index] 个出号球在鼓局部坐标系里的落点。
     *
     * 2D 舞台把落点设在**鼓底外侧**：球从鼓里滑出、在下缘淡出，
     * 由舞台下方的结果条接手展示号码。横向按槽距铺开，视觉上像依次排开。
     */
    fun slotTarget(spec: DrumRenderSpec, index: Int): FloatArray {
        val half = (spec.slotCount - 1) / 2f
        return floatArrayOf(
            (index - half) * spec.slotSpacing * 0.55f,
            -spec.drumRadius - spec.ballScale * 1.2f,
            0f,
        )
    }
}
