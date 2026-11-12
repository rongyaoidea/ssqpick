package com.ssqpick.feature.machine

import com.ssqpick.feature.machine.physics.V3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 控制器集成测试：从开摇到落槽的整条链路。
 *
 * 号码由 CSPRNG 预抽、物理只负责演出——这里验证的也是"演出"：
 * 流程能走完、出号球按管道路径走、最终都停在展示槽。
 */
class MachineControllerTest {

    @Test
    fun `完整摇号流程能走完且全部落槽`() {
        val c = MachineController()
        c.start(redCount = 6, blueCount = 1)
        repeat(60 * 20) { c.advance(1f / 60f) }
        val s = c.state.value
        assertEquals(MachineUiState.Phase.COMPLETE, s.phase)
        assertEquals(6, s.redNumbers.size)
        assertEquals(1, s.blueNumbers.size)
        assertTrue(
            "出号的红球都应停在展示槽",
            c.redWorld.balls.filter { it.number in s.redNumbers }.all { it.settled },
        )
        assertTrue(
            "出号的蓝球都应停在展示槽",
            c.blueWorld.balls.filter { it.number in s.blueNumbers }.all { it.settled },
        )
    }

    @Test
    fun `出号球先过鼓底管口再去展示槽`() {
        val c = MachineController()
        c.start(redCount = 6, blueCount = 1)
        val outlet = MachineLayout.outletTarget(MachineLayout.RED)
        var minDist = Float.MAX_VALUE
        var sawEscaping = false
        repeat(60 * 20) {
            c.advance(1f / 60f)
            for (b in c.redWorld.balls) {
                if (!b.escaping) continue
                sawEscaping = true
                val d = V3.len(b.x - outlet[0], b.y - outlet[1], b.z - outlet[2])
                if (d < minDist) minDist = d
            }
        }
        assertTrue("应看到球出号", sawEscaping)
        assertTrue("出号球应经过鼓底管口，最近 $minDist", minDist < 0.25f)
    }

    @Test
    fun `出号球是掉下来的不是瞬移`() {
        val c = MachineController()
        c.start(redCount = 6, blueCount = 1)
        var escapeStartedAt = -1
        var firstSettleAt = -1
        var fallingFrames = 0
        var sawBallInTray = false
        var frame = 0
        while (frame < 60 * 20 && c.state.value.phase != MachineUiState.Phase.COMPLETE) {
            c.advance(1f / 60f)
            val b = c.redWorld.balls.firstOrNull { it.escaping }
            if (b != null) {
                if (escapeStartedAt < 0) escapeStartedAt = frame
                if (!b.settled && b.escapeStage == 1) fallingFrames++
                if (b.y < -1f && !b.settled) sawBallInTray = true
                if (b.settled && firstSettleAt < 0) firstSettleAt = frame
            }
            frame++
        }
        assertTrue("应先有球出号", escapeStartedAt >= 0)
        assertTrue("第一颗球应有落管过程（>= 0.15s），实际 $fallingFrames 帧", fallingFrames >= 9)
        assertTrue("球应落到鼓底外侧", sawBallInTray)
        assertTrue("第一颗球不应在 0.4s 内就落稳", firstSettleAt - escapeStartedAt >= 24)
    }

    @Test
    fun `号码在球落槽后才逐个出现`() {
        val c = MachineController()
        c.start(redCount = 6, blueCount = 1)
        val steps = mutableListOf<Int>()
        var sawPartly = false
        var frame = 0
        while (frame < 60 * 20 && c.state.value.phase != MachineUiState.Phase.COMPLETE) {
            c.advance(1f / 60f)
            val s = c.state.value
            // 已显示号码数 = 已落槽球数，不能提前亮
            assertEquals(
                "落槽数与结果条亮起的号码数不一致",
                c.redWorld.balls.count { it.settled },
                s.redSlots.count { it != null },
            )
            val revealed = s.redSlots.count { it != null }
            if (revealed in 1 until 6) sawPartly = true
            if (steps.lastOrNull() != revealed) steps += revealed
            frame++
        }
        assertTrue("应看到号码逐个亮起，实际 $steps", sawPartly)
        assertEquals("最后 6 个号码应全部亮起", 6, c.state.value.redSlots.count { it != null })
        // 升序槽位：亮的号码按位置单调递增
        val filled = c.state.value.redSlots.filterNotNull()
        assertEquals(filled.sorted(), filled)
    }

    @Test
    fun `单蓝球停在鼓底正中`() {
        val c = MachineController()
        c.start(redCount = 6, blueCount = 1)
        var frame = 0
        while (frame < 60 * 20 && c.state.value.phase != MachineUiState.Phase.COMPLETE) {
            c.advance(1f / 60f)
            frame++
        }
        val blue = c.blueWorld.balls.first { it.settled }
        assertEquals("单蓝球应居中落在承接槽", 0f, blue.x, 1e-3f)
        assertEquals(MachineLayout.slotY(MachineLayout.BLUE), blue.y, 1e-3f)
        assertEquals(0, blue.trayIndex)
    }

    @Test
    fun `大复式超出承接槽的球落进收集口`() {
        val c = MachineController()
        c.start(redCount = 10, blueCount = 4)
        var frame = 0
        while (frame < 60 * 30 && c.state.value.phase != MachineUiState.Phase.COMPLETE) {
            c.advance(1f / 60f)
            frame++
        }
        assertEquals(MachineUiState.Phase.COMPLETE, c.state.value.phase)
        val reds = c.redWorld.balls.filter { it.settled }
        assertEquals("10 个红球都应落稳", 10, reds.size)
        assertEquals("承接槽只停 6 个", 6, reds.count { it.trayIndex >= 0 })
        assertEquals("其余 4 个进收集口", 4, reds.count { it.trayIndex < 0 })
        assertEquals("结果条仍显示 10 个号码", 10, c.state.value.redSlots.count { it != null })
        assertEquals(4, c.blueWorld.balls.count { it.settled && it.trayIndex >= 0 })
    }
}
