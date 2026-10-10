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
