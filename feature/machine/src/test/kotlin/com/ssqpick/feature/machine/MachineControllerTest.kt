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
}
