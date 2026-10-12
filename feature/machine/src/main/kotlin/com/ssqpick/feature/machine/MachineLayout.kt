package com.ssqpick.feature.machine

/**
 * 一台鼓的展示配置。
 *
 * 2D 舞台只用到"出号槽"这几个参数：槽数、槽距、鼓半径、球半径。
 * （GL 时代的鼓心坐标/球数/种类字段已随渲染器删除，别再长回来。）
 */
data class DrumRenderSpec(
    /** 鼓半径（局部单位，1.0 表示占满一个单位球）。 */
    val drumRadius: Float,
    /** 球半径（局部单位）。 */
    val ballScale: Float,
    /** 出号槽数量：红 6 个、蓝最多 4 个（大复式 10+4）。 */
    val slotCount: Int,
    /** 相邻槽间距（局部单位）。 */
    val slotSpacing: Float,
)

/** 摇号机的舞台布局。控制器与渲染层共用这一份配置。 */
object MachineLayout {

    /** 红鼓：33 球，出 6 个。球径与 DrumWorld 的 ballRadius 保持一致。 */
    val RED = DrumRenderSpec(
        drumRadius = 1f,
        ballScale = 0.20f,
        slotCount = 6,
        slotSpacing = 0.60f,
    )

    /** 蓝鼓：16 球，最多出 4 个（复式模式下逐颗发射）。 */
    val BLUE = DrumRenderSpec(
        drumRadius = 1f,
        ballScale = 0.24f,
        slotCount = 4,
        slotSpacing = 0.60f,
    )

    /**
     * 鼓底管口（球心可达的内壁点）：出号第一段先滑到这里。
     *
     * 球心在内壁上的极限位置就是 `drumRadius - ballScale`，所以管口
     * 天然与鼓底相切，球滑到这儿正好"对准"画出来的出号管。
     */
    fun outletTarget(spec: DrumRenderSpec): FloatArray = floatArrayOf(
        0f,
        -(spec.drumRadius - spec.ballScale),
        0f,
    )

    /** 出号管出口：球竖直穿过管子后先到这里，再横向滑向展示槽。 */
    fun chuteExitTarget(spec: DrumRenderSpec): FloatArray = floatArrayOf(
        0f,
        -(spec.drumRadius + spec.ballScale * 0.9f),
        0f,
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
