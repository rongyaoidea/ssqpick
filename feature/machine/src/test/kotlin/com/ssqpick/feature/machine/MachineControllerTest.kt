package com.ssqpick.feature.machine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 状态机回归：单式必须能走完 红混合→红出号→蓝混合→蓝出号→完成。
 *
 * 写这个测试的原因：`emitIndex` 是红蓝共用的计数器，红出完后它停在 6，
 * 而蓝出号的守卫是 `emitIndex == 0`——蓝球永远等不到发射，直接卡死。
 * advance() 是纯同步函数，不碰主线程，JVM 单测随便跑。
 */
class MachineControllerTest {

    @Test
    fun `单式走完全程_红6蓝1`() {
        val c = MachineController()
        c.start(DrawMode.SIMPLE)

        var frames = 0
        while (c.state.value.phase != MachineUiState.Phase.COMPLETE && frames < 7_200) {
            c.advance(1 / 60f)
            frames++
        }

        assertEquals(MachineUiState.Phase.COMPLETE, c.state.value.phase)
        assertEquals(6, c.state.value.redNumbers.size)
        assertEquals(1, c.state.value.blueNumbers.size)
        // 出来的必须是合法号码，不是下标越界之类的垃圾
        assertEquals(6, c.state.value.redNumbers.toSet().size)
        assertEquals(1, c.state.value.blueNumbers.toSet().size)
    }

    @Test
    fun `出号顺序是随机序_结果行才是升序`() {
        var sawNonAscendingEmission = false
        repeat(5) { run ->
            val c = MachineController()
            c.start(DrawMode.SIMPLE)

            val emission = mutableListOf<Int>()
            var frames = 0
            while (c.state.value.phase != MachineUiState.Phase.COMPLETE && frames < 14_400) {
                val before = c.state.value.redEmitted
                c.advance(1 / 60f)
                frames++
                if (c.state.value.redEmitted > before) {
                    // 刚发射的球：在鼓里找 escaping 且还没记录过的那个
                    val ball = c.redWorld.balls.firstOrNull { it.escaping && it.number !in emission }
                    if (ball != null) emission += ball.number
                }
            }

            assertEquals("第 $run 次应出满 6 颗", 6, emission.size)
            // 结果行（展示）必须升序
            assertEquals(emission.sorted(), c.state.value.redNumbers)
            if (emission != emission.sorted()) sawNonAscendingEmission = true
        }
        org.junit.Assert.assertTrue(
            "5 次摇号中至少一次出号顺序不是升序——否则说明又按排序发射了",
            sawNonAscendingEmission,
        )
    }

    @Test
    fun `复式蓝球逐颗出完才完成_红7蓝2`() {
        val c = MachineController()
        c.start(DrawMode.COMPOUND_SMALL)

        var frames = 0
        var sawSecondBlueWhileDrawing = false
        while (c.state.value.phase != MachineUiState.Phase.COMPLETE && frames < 14_400) {
            c.advance(1 / 60f)
            frames++
            // 旧行为：第二颗蓝球只在 COMPLETE 那一拍被跳过式地记为已出（0→N 跳变）。
            // 新行为：出蓝阶段中就应看到 blueEmitted=2 的中间态。
            if (c.state.value.phase == MachineUiState.Phase.BLUE_DRAWING &&
                c.state.value.blueEmitted == 2
            ) {
                sawSecondBlueWhileDrawing = true
            }
        }

        assertEquals(MachineUiState.Phase.COMPLETE, c.state.value.phase)
        assertEquals(7, c.state.value.redNumbers.size)
        assertEquals(2, c.state.value.blueNumbers.size)
        org.junit.Assert.assertTrue(sawSecondBlueWhileDrawing)
    }
}
