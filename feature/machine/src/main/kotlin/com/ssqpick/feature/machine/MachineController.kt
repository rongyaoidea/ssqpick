package com.ssqpick.feature.machine

import com.ssqpick.feature.machine.physics.Ball
import com.ssqpick.feature.machine.physics.DrumWorld
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.NumberGenerator
import com.ssqpick.rules.TicketRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt
import kotlin.random.Random

/** 预设的投注方式（摇号机界面的一排快捷选项）。自定义数量走 [DrawLimits]。 */
enum class DrawMode(
    val label: String,
    val redCount: Int,
    val blueCount: Int,
) {
    SIMPLE("单式 6+1", 6, 1),
    COMPOUND_SMALL("小复式 7 红 2 蓝", 7, 2),
    COMPOUND_MID("复式 8 红 3 蓝", 8, 3),
    COMPOUND_LARGE("大复式 10 红 4 蓝", 10, 4),
    ;

    val betCount: Long
        get() = com.ssqpick.rules.TicketRules.binom(redCount.toLong(), 6) * blueCount

    val priceYuan: Long get() = betCount * 2L
}

/** 摇号机的展示状态。数量与已出的号码分开存：摇之前就能显示"要摇几个、几注"。 */
data class MachineUiState(
    val phase: Phase = Phase.IDLE,
    /** 本次要摇的红球/蓝球个数（合法范围见 [DrawLimits]）。 */
    val redCount: Int = DrawLimits.RED_MIN,
    val blueCount: Int = DrawLimits.BLUE_MIN,
    val redNumbers: List<Int> = emptyList(),
    val blueNumbers: List<Int> = emptyList(),
    val redEmitted: Int = 0,
    val blueEmitted: Int = 0,
) {
    enum class Phase { IDLE, RED_MIXING, RED_DRAWING, BLUE_MIXING, BLUE_DRAWING, COMPLETE }

    val isRunning: Boolean get() = phase != Phase.IDLE && phase != Phase.COMPLETE
    val isComplete: Boolean get() = phase == Phase.COMPLETE

    val phaseLabel: String
        get() = when (phase) {
            Phase.IDLE -> "待机"
            Phase.RED_MIXING -> "红球机摇号中…"
            Phase.RED_DRAWING -> "红球出号中…"
            Phase.BLUE_MIXING -> "蓝球机摇号中…"
            Phase.BLUE_DRAWING -> "蓝球出号中…"
            Phase.COMPLETE -> "摇号完成"
        }

    /** 这组数量的理论注数与金额：不依赖已出的球，摇之前和摇完都对。 */
    val plannedBets: Long get() = DrawLimits.bets(redCount, blueCount)
    val plannedPriceYuan: Long get() = plannedBets * 2L

    fun toDraft(): TicketDraft = TicketDraft(
        redPicked = redNumbers.toSet(),
        bluePicked = blueNumbers.toSet(),
    )

    val betCount: Long get() = TicketRules.betCount(toDraft())
    val priceYuan: Long get() = betCount * 2L
}

/**
 * 摇号机控制器。
 *
 * **设计原则：号码由 CSPRNG 决定，3D 机器只负责把结果演出来。**
 * 物理引擎的碰撞动力学不是均匀随机源——如果直接拿"哪个球先落到出口"当结果，
 * 号码分布会带系统性偏差，反而破坏了彩票应有的公平性。所以流程是：
 * 先用 [NumberGenerator] 抽好号码，再由物理层把这些球"引导"到出口槽，
 * 看起来像自然摇出，统计上保证每个组合等概率。
 *
 * 推进方式：界面帧回调每帧调用 [advance]，物理与状态机都在该回调里同步执行，
 * 不开协程，避免两个线程抢同一份球状态。
 */
class MachineController {

    // 待机时电机停、风机关（omega=0），球堆在鼓底；开摇才加速转起来。
    // 球径按真机比例取小（填充率 ~25%）：33 个球在 r=0.25 时填充率 52%，
    // 球堆互相卡死，风机吹不动——"真实动效"的前提是球装得下、混得开。
    val redWorld = DrumWorld(drumRadius = 1f, ballRadius = 0.20f, omega = 0f).apply {
        addBalls(33)
        scatter(Random(7))
    }
    val blueWorld = DrumWorld(drumRadius = 1f, ballRadius = 0.24f, omega = 0f).apply {
        addBalls(16)
        scatter(Random(11))
    }

    private val _state = MutableStateFlow(MachineUiState())
    val state: StateFlow<MachineUiState> = _state.asStateFlow()

    private val redBalls = redWorld.balls
    private val blueBalls = blueWorld.balls

    private var phaseTimer = 0f
    private var emitIndex = 0
    private var emitCooldown = 0f
    private var pendingRed: List<Int> = emptyList()
    private var pendingBlue: List<Int> = emptyList()

    companion object {
        const val MIX_SECONDS = 1.9f
        const val EMIT_INTERVAL = 0.40f

        /** 混合阶段的目标转速（rad/s）。真机是慢转 + 风机吹：
         *  转速再高离心力就把球全按在壳上，风机吹不动（实测 2.4 时内区占用率只剩 3%）。 */
        private const val MIX_OMEGA_RED = 1.5f
        private const val MIX_OMEGA_BLUE = 1.7f

        /** 出号阶段转速降到混合的 ~60%：真机出号时仍慢转、风仍吹。 */
        private const val DRAW_OMEGA_RED = 0.9f
        private const val DRAW_OMEGA_BLUE = 1.0f

        /** 出号阶段风扇功率（略低于混合，球更乖一点好进管口）。 */
        private const val DRAW_FAN = 0.85f

        /** 到达当前段目标的判定半径：进入就切下一段（最后一段则落槽）。 */
        private const val ARRIVE_RADIUS = 0.09f
    }

    fun start(redCount: Int, blueCount: Int) {
        if (_state.value.isRunning) return
        // 防御：任何入口都先夹一次合法范围（界面滑条已经夹过，这里兜底）
        val (red, blue) = DrawLimits.clampRed(redCount, blueCount)
        // 出号顺序用随机序（逐颗摇出），结果行展示时再排序
        pendingRed = NumberGenerator.sampleOrdered(TicketRules.RED_TOTAL, red)
        pendingBlue = NumberGenerator.sampleOrdered(TicketRules.BLUE_TOTAL, blue)
        emitIndex = 0
        emitCooldown = 0f
        phaseTimer = 0f
        resetWorlds()
        _state.value = MachineUiState(
            phase = MachineUiState.Phase.RED_MIXING,
            redCount = red,
            blueCount = blue,
        )
    }

    /** 只改数量：回到待机、清空结果，不开始摇。运行中忽略。 */
    fun configure(redCount: Int, blueCount: Int) {
        if (_state.value.isRunning) return
        val (red, blue) = DrawLimits.clampRed(redCount, blueCount)
        emitIndex = 0
        emitCooldown = 0f
        phaseTimer = 0f
        resetWorlds()
        _state.value = MachineUiState(
            phase = MachineUiState.Phase.IDLE,
            redCount = red,
            blueCount = blue,
        )
    }

    /**
     * 出号进度 (已出, 总数)。混合阶段返回 null——此时"在转"的刻度本身就是反馈，
     * 再加一条进度线只是多余的视觉噪音。
     */
    fun progress(): Pair<Int, Int>? = when (_state.value.phase) {
        MachineUiState.Phase.RED_DRAWING -> _state.value.redEmitted to _state.value.redCount
        MachineUiState.Phase.BLUE_DRAWING -> _state.value.blueEmitted to _state.value.blueCount
        else -> null
    }

    /** 摇完后回到待机，清空结果。 */
    fun reset() {
        if (_state.value.isRunning) return
        resetWorlds()
        _state.value = MachineUiState(
            phase = MachineUiState.Phase.IDLE,
            redCount = _state.value.redCount,
            blueCount = _state.value.blueCount,
        )
    }

    private fun resetWorlds() {
        val seed = System.nanoTime()
        redWorld.scatter(Random(seed))
        blueWorld.scatter(Random(seed xor 0x5DEECE66DL))
        redBalls.forEach(::clearBall)
        blueBalls.forEach(::clearBall)
    }

    private fun clearBall(b: Ball) {
        b.escaping = false
        b.settled = false
        b.escapeStage = 0
        b.attractX = 0f
        b.attractY = 0f
        b.attractZ = 0f
        b.slotX = 0f
        b.slotY = 0f
        b.slotZ = 0f
        b.vx = 0f
        b.vy = 0f
        b.vz = 0f
    }

    /** 每帧推进（界面帧回调里调用，物理与状态机同步执行，不开协程）。 */
    fun advance(dt: Float) {
        val s = _state.value
        updateMotor(s.phase)
        redWorld.step(dt)
        blueWorld.step(dt)
        updateEscapes()

        when (s.phase) {
            MachineUiState.Phase.IDLE, MachineUiState.Phase.COMPLETE -> Unit

            MachineUiState.Phase.RED_MIXING -> {
                phaseTimer += dt
                if (phaseTimer >= MIX_SECONDS) {
                    phaseTimer = 0f
                    _state.value = s.copy(phase = MachineUiState.Phase.RED_DRAWING)
                }
            }

            MachineUiState.Phase.RED_DRAWING -> {
                emitCooldown += dt
                if (emitCooldown >= EMIT_INTERVAL && emitIndex < pendingRed.size) {
                    emitCooldown = 0f
                    emitIndex++
                    emitNextRed(emitIndex - 1)
                }
                if (emitIndex >= pendingRed.size) {
                    _state.value = _state.value.copy(
                        phase = MachineUiState.Phase.BLUE_MIXING,
                        // 展示用结果排序（出号顺序是随机的，见 pendingRed）
                        redNumbers = pendingRed.sorted(),
                        redEmitted = pendingRed.size,
                    )
                    phaseTimer = 0f
                    emitCooldown = 0f
                    // 计数器红蓝共用：不清零的话，蓝阶段 `emitIndex == 0` 守卫永远等不到，
                    // 蓝球一次都不发射，状态机卡死在 BLUE_DRAWING。
                    emitIndex = 0
                }
            }

            MachineUiState.Phase.BLUE_MIXING -> {
                phaseTimer += dt
                if (phaseTimer >= MIX_SECONDS) {
                    phaseTimer = 0f
                    emitCooldown = 0f
                    _state.value = _state.value.copy(phase = MachineUiState.Phase.BLUE_DRAWING)
                }
            }

            MachineUiState.Phase.BLUE_DRAWING -> {
                emitCooldown += dt
                // 蓝球与红球同样逐颗发射：复式可能出 2~4 颗，
                // 之前只发第一颗、进度从 0 跳到 N，表现层不一致。
                if (emitCooldown >= EMIT_INTERVAL && emitIndex < pendingBlue.size) {
                    emitCooldown = 0f
                    emitBlue(pendingBlue[emitIndex], emitIndex)
                    emitIndex++
                }
                if (emitIndex >= pendingBlue.size &&
                    pendingBlue.all { blueBalls[it - 1].settled }
                ) {
                    _state.value = _state.value.copy(
                        phase = MachineUiState.Phase.COMPLETE,
                        blueNumbers = pendingBlue.sorted(),
                        blueEmitted = pendingBlue.size,
                    )
                }
            }
        }
    }

    /**
     * 电机与风扇按相位驱动：红蓝两台机各自独立——
     * 红球出号时红机仍在慢转慢吹，蓝机还在待机；蓝球出号时红机才停。
     */
    private fun updateMotor(phase: MachineUiState.Phase) {
        // 用局部变量而不是数组：这个方法每帧都跑，不给热路径添分配
        var redOmega = 0f
        var redFan = 0f
        var blueOmega = 0f
        var blueFan = 0f
        when (phase) {
            MachineUiState.Phase.IDLE, MachineUiState.Phase.COMPLETE -> Unit

            MachineUiState.Phase.RED_MIXING -> {
                redOmega = MIX_OMEGA_RED
                redFan = 1f
            }

            MachineUiState.Phase.RED_DRAWING -> {
                redOmega = DRAW_OMEGA_RED
                redFan = DRAW_FAN
            }

            MachineUiState.Phase.BLUE_MIXING -> {
                blueOmega = MIX_OMEGA_BLUE
                blueFan = 1f
            }

            MachineUiState.Phase.BLUE_DRAWING -> {
                blueOmega = DRAW_OMEGA_BLUE
                blueFan = DRAW_FAN
            }
        }
        redWorld.omegaTarget = redOmega
        redWorld.blowerTarget = redFan
        blueWorld.omegaTarget = blueOmega
        blueWorld.blowerTarget = blueFan
    }

    /**
     * 出号三段路：管口 → 管出口 → 展示槽。
     *
     * 之前是一根弹簧直拽到槽位，球会从鼓壁上任意一点"穿玻璃"飞出去；
     * 现在球先滑到鼓底管口、竖直穿过画出来的出号管，再横着滚向自己的槽，
     * 路径与舞台上的管线对得上。
     */
    private fun updateEscapes() {
        for (b in redBalls) updateEscape(b, MachineLayout.RED)
        for (b in blueBalls) updateEscape(b, MachineLayout.BLUE)
    }

    private fun updateEscape(b: Ball, spec: DrumRenderSpec) {
        if (!b.escaping || b.settled) return
        val dx = b.x - b.attractX
        val dy = b.y - b.attractY
        val dz = b.z - b.attractZ
        if (sqrt(dx * dx + dy * dy + dz * dz) > ARRIVE_RADIUS) return
        when (b.escapeStage) {
            0 -> {
                val t = MachineLayout.chuteExitTarget(spec)
                b.attractX = t[0]
                b.attractY = t[1]
                b.attractZ = t[2]
                b.escapeStage = 1
            }

            1 -> {
                b.attractX = b.slotX
                b.attractY = b.slotY
                b.attractZ = b.slotZ
                b.escapeStage = 2
            }

            else -> settle(b)
        }
    }

    private fun settle(b: Ball) {
        b.settled = true
        b.x = b.attractX
        b.y = b.attractY
        b.z = b.attractZ
        b.vx = 0f
        b.vy = 0f
        b.vz = 0f
        b.spinZ = 0f
    }

    /** 第 index 个预选红球出发：先滑向鼓底管口，最终落点由 [MachineLayout] 给出。 */
    private fun emitNextRed(index: Int) {
        val number = pendingRed[index]
        val ball = redBalls[number - 1]
        val outlet = MachineLayout.outletTarget(MachineLayout.RED)
        val slot = MachineLayout.slotTarget(MachineLayout.RED, index)
        ball.attractX = outlet[0]
        ball.attractY = outlet[1]
        ball.attractZ = outlet[2]
        ball.slotX = slot[0]
        ball.slotY = slot[1]
        ball.slotZ = slot[2]
        ball.escapeStage = 0
        ball.escaping = true
        _state.value = _state.value.copy(redEmitted = index + 1)
    }

    private fun emitBlue(number: Int, index: Int) {
        val ball = blueBalls[number - 1]
        val outlet = MachineLayout.outletTarget(MachineLayout.BLUE)
        val slot = MachineLayout.slotTarget(MachineLayout.BLUE, index)
        ball.attractX = outlet[0]
        ball.attractY = outlet[1]
        ball.attractZ = outlet[2]
        ball.slotX = slot[0]
        ball.slotY = slot[1]
        ball.slotZ = slot[2]
        ball.escapeStage = 0
        ball.escaping = true
        _state.value = _state.value.copy(blueEmitted = index + 1)
    }
}
