package com.ssqpick.feature.machine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 自定义数量的边界：注数上限 5 万、红 6..20、蓝 1..16，
 * 两个滑条互相牵制时到底夹到哪。
 */
class DrawLimitsTest {

    @Test
    fun `注数按组合数乘蓝球`() {
        assertEquals(1L, DrawLimits.bets(6, 1))
        assertEquals(14L, DrawLimits.bets(7, 2)) // C(7,6)=7 × 2
        assertEquals(840L, DrawLimits.bets(10, 4)) // C(10,6)=210 × 4
        assertEquals(38760L, DrawLimits.bets(20, 1))
    }

    @Test
    fun `蓝球拉满时红球上限被压到14`() {
        assertEquals(14, DrawLimits.maxRedFor(16)) // C(14,6)×16=48048 ≤ 50000
        assertEquals(20, DrawLimits.maxRedFor(1))
        assertEquals(16, DrawLimits.maxBlueFor(10)) // C(10,6)×16=3360
        assertEquals(1, DrawLimits.maxBlueFor(20)) // C(20,6)×2 就超了
    }

    @Test
    fun `调红球会顺手把蓝球夹住`() {
        // 红拉满 20，蓝 16 必须降到 1
        assertEquals(20 to 1, DrawLimits.clampRed(20, 16))
        // 红 10 时蓝 16 是合法的
        assertEquals(10 to 16, DrawLimits.clampRed(10, 16))
        // 越界输入被夹回
        assertEquals(6 to 1, DrawLimits.clampRed(3, 0))
    }

    @Test
    fun `调蓝球会顺手把红球夹住`() {
        assertEquals(14 to 16, DrawLimits.clampBlue(20, 16))
        assertEquals(20 to 1, DrawLimits.clampBlue(20, 1))
        // 蓝先被夹进 1..16（99 → 16），红 6 在 16 蓝下的上限 14 之内，不动
        assertEquals(6 to 16, DrawLimits.clampBlue(6, 99))
    }

    @Test
    fun `夹取后的组合一定不超过注数上限`() {
        for (r in 6..20) {
            for (b in 1..16) {
                val (red, blue) = DrawLimits.clampRed(r, b)
                org.junit.Assert.assertTrue(
                    "clampRed($r,$b) -> ($red,$blue) 超限",
                    DrawLimits.bets(red, blue) <= DrawLimits.MAX_BETS,
                )
                val (red2, blue2) = DrawLimits.clampBlue(r, b)
                org.junit.Assert.assertTrue(
                    "clampBlue($r,$b) -> ($red2,$blue2) 超限",
                    DrawLimits.bets(red2, blue2) <= DrawLimits.MAX_BETS,
                )
            }
        }
    }
}
