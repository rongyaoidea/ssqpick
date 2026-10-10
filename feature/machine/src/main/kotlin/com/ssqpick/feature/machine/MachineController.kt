package com.ssqpick.feature.machine

import com.ssqpick.feature.machine.physics.Ball
import com.ssqpick.feature.machine.physics.DrumWorld
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.NumberGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt
import kotlin.random.Random

/** 一次摇号的模式。 */
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

/** 摇号机的展示状态。 */
data class MachineUiState(
    val phase: Phase = Phase.IDLE,
    val mode: DrawMode = DrawMode.SIMPLE,
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

    fun toDraft(): TicketDraft = TicketDraft(
        redPicked = redNumbers.toSet(),
        bluePicked = blueNumbers.toSet(),
    )

    val betCount: Long get() = com.ssqpick.rules.TicketRules.betCount(toDraft())
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

    val redWorld = DrumWorld(drumRadius = 1f, ballRadius = 0.25f, omega = 5.2f).apply {
        addBalls(33)
        scatter(Random(7))
    }
    val blueWorld = DrumWorld(drumRadius = 1f, ballRadius = 0.30f, omega = 5.6f).apply {
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
    }

    fun start(mode: DrawMode) {
        if (_state.value.isRunning) return
        pendingRed = NumberGenerator.sample(33, mode.redCount).sorted()
        pendingBlue = NumberGenerator.sample(16, mode.blueCount).sorted()
        emitIndex = 0
        emitCooldown = 0f
        phaseTimer = 0f
        resetWorlds()
        _state.value = MachineUiState(phase = MachineUiState.Phase.RED_MIXING, mode = mode)
    }

    /**
     * 出号进度 (已出, 总数)。混合阶段返回 null——此时"在转"的刻度本身就是反馈，
     * 再加一条进度线只是多余的视觉噪音。
     */
    fun progress(): Pair<Int, Int>? = when (_state.value.phase) {
        MachineUiState.Phase.RED_DRAWING -> _state.value.redEmitted to _state.value.mode.redCount
        MachineUiState.Phase.BLUE_DRAWING -> _state.value.blueEmitted to _state.value.mode.blueCount
        else -> null
    }

    /** 摇完后回到待机，清空结果。 */
    fun reset() {
        if (_state.value.isRunning) return
        resetWorlds()
        _state.value = MachineUiState(phase = MachineUiState.Phase.IDLE, mode = _state.value.mode)
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
        b.attractX = 0f
        b.attractY = 0f
        b.attractZ = 0f
        b.vx = 0f
        b.vy = 0f
        b.vz = 0f
    }

    /** 每帧推进（界面帧回调里调用，物理与状态机同步执行，不开协程）。 */
    fun advance(dt: Float) {
        val s = _state.value
        redWorld.step(dt)
        blueWorld.step(dt)
        markArrived()

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
                        redNumbers = pendingRed,
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
                        blueNumbers = pendingBlue,
                        blueEmitted = pendingBlue.size,
                    )
                }
            }
        }
    }

    /** 逃逸球接近目标点后标记静止，物理上真正停在展示槽里。 */
    private fun markArrived() {
        for (b in redBalls) if (b.escaping && !b.settled && nearTarget(b)) settle(b)
        for (b in blueBalls) if (b.escaping && !b.settled && nearTarget(b)) settle(b)
    }

    private fun nearTarget(b: Ball): Boolean {
        val dx = b.x - b.attractX
        val dy = b.y - b.attractY
        val dz = b.z - b.attractZ
        return sqrt(dx * dx + dy * dy + dz * dz) < 0.06f
    }

    private fun settle(b: Ball) {
        b.settled = true
        b.x = b.attractX
        b.y = b.attractY
        b.z = b.attractZ
        b.vx = 0f
        b.vy = 0f
        b.vz = 0f
        b.spinX = 0f
        b.spinY = 0f
    }

    /** 第 index 个预选红球逃向该槽位（槽位坐标由 [MachineLayout] 统一给出）。 */
    private fun emitNextRed(index: Int) {
        val number = pendingRed[index]
        val ball = redBalls[number - 1]
        val t = MachineLayout.slotTarget(MachineLayout.RED, index)
        ball.attractX = t[0]
        ball.attractY = t[1]
        ball.attractZ = t[2]
        ball.escaping = true
        _state.value = _state.value.copy(redEmitted = index + 1)
    }

    private fun emitBlue(number: Int, index: Int) {
        val ball = blueBalls[number - 1]
        val t = MachineLayout.slotTarget(MachineLayout.BLUE, index)
        ball.attractX = t[0]
        ball.attractY = t[1]
        ball.attractZ = t[2]
        ball.escaping = true
        _state.value = _state.value.copy(blueEmitted = index + 1)
    }
}
