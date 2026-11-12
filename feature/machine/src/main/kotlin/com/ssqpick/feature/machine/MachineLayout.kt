package com.ssqpick.feature.machine

/**
 * 一台鼓的展示配置。
 *
 * 2D 舞台只用到"出号槽"这几个参数：槽数、槽距、鼓半径、球半径。
 * 槽距按球径放大过：球会真的停在槽里，不再淡出，所以相邻球不能叠在一起。
 */
data class DrumRenderSpec(
    /** 鼓半径（局部单位，1.0 表示占满一个单位球）。 */
    val drumRadius: Float,
    /** 球半径（局部单位）。 */
    val ballScale: Float,
    /** 可见承接槽数量（红 6 / 蓝 4）；超出的球从收集口落下，不在舞台叠球。 */
    val slotCount: Int,
    /** 相邻槽间距（局部单位）。必须大于 2 × [ballScale]。 */
    val slotSpacing: Float,
)

/** 摇号机的舞台布局。控制器与渲染层共用这一份配置。 */
object MachineLayout {

    /** 红鼓：33 球，出 6 个。球径与 DrumWorld 的 ballRadius 保持一致。 */
    val RED = DrumRenderSpec(
        drumRadius = 1f,
        ballScale = 0.20f,
        slotCount = 6,
        slotSpacing = 0.44f,
    )

    /** 蓝鼓：16 球，最多出 4 个（复式模式下逐颗发射）。 */
    val BLUE = DrumRenderSpec(
        drumRadius = 1f,
        ballScale = 0.24f,
        slotCount = 4,
        slotSpacing = 0.52f,
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

    /**
     * 出号管出口，也是承接槽的高度：球从管口自由落体到这里，再沿槽滚向自己的位置。
     * 球心比槽面高一个球半径，所以球落到这里时正好"坐"在槽面上。
     */
    fun chuteExitTarget(spec: DrumRenderSpec): FloatArray = floatArrayOf(
        0f,
        slotY(spec),
        0f,
    )

    /**
     * 第 [index] 个出号球在鼓局部坐标系里的落点。
     *
     * [count] 是本次实际出号个数（复式可能 7~20 红、2~16 蓝），不能写死成
     * [DrumRenderSpec.slotCount]，否则大复式会从鼓底下单边铺出去。
     * 2D 舞台把落点设在**鼓底外侧**：球从鼓里落下来，停在槽里由舞台展示；
     * 下方结果条同步显示同一个号码。横向按槽距铺开，最终号码按升序找位置，
     * 所以结果条从左到右仍是升序。
     */
    fun slotTarget(spec: DrumRenderSpec, index: Int, count: Int = spec.slotCount): FloatArray {
        val half = (count - 1) / 2f
        return floatArrayOf(
            (index - half) * spec.slotSpacing,
            slotY(spec),
            0f,
        )
    }

    /** 承接槽半宽：足够托住最外侧的球。 */
    fun trayHalf(spec: DrumRenderSpec, count: Int): Float = (count - 1) / 2f * spec.slotSpacing + spec.ballScale * 1.15f

    /** 球心停在槽里时的高度（局部 y，越小越靠下）。 */
    fun slotY(spec: DrumRenderSpec): Float = -(spec.drumRadius + spec.ballScale * 1.15f)

    /** 超出可见槽位的大复式球：从管口落到槽面高度，随后进收集口，不再绘制。 */
    fun binTarget(spec: DrumRenderSpec): FloatArray = floatArrayOf(0f, slotY(spec), 0f)

    /** 承接槽的槽面高度：球心再往下一个球半径，就是球底。 */
    fun trayY(spec: DrumRenderSpec): Float = -(spec.drumRadius + spec.ballScale * 2.15f)
}
