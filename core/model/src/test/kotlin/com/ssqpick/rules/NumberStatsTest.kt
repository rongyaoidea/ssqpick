package com.ssqpick.rules

import com.ssqpick.model.Draw
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberStatsTest {

    private fun draw(code: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate.parse("2026-0${code.last()}-01"),
        red = red,
        blue = blue,
    )

    @Test
    fun `空库返回全零统计`() {
        val s = computeStats(emptyList())
        assertEquals(0, s.totalDraws)
        assertEquals(listOf(1, 2, 3), s.hotReds(3))
        assertEquals(0, s.redFrequency[7])
        assertEquals(0, s.redOmission[7])
    }

    @Test
    fun `频次与遗漏计算正确`() {
        // 3 期：7 号出 2 次（第 1、3 期），8 号出 1 次（第 2 期），蓝 1 出 2 次
        val draws = listOf(
            draw("2026001", listOf(1, 2, 3, 4, 5, 7), 1),
            draw("2026002", listOf(1, 2, 3, 4, 5, 8), 2),
            draw("2026003", listOf(1, 2, 3, 4, 5, 7), 1),
        )
        val s = computeStats(draws)
        assertEquals(3, s.totalDraws)
        assertEquals(2, s.redFrequency[7])
        assertEquals(1, s.redFrequency[8])
        assertEquals(0, s.redFrequency[9])
        assertEquals(2, s.blueFrequency[1])

        // 遗漏从最新一期往回数：7 号上期刚出=0，8 号隔 1 期=1，9 号从未出=3
        assertEquals(0, s.redOmission[7])
        assertEquals(1, s.redOmission[8])
        assertEquals(3, s.redOmission[9])
        assertEquals(0, s.blueOmission[1])
        assertEquals(1, s.blueOmission[2])

        // 排序：1~5 号各出 3 次排最前，7 号（2 次）紧随其后；
        // 最冷是遗漏 3 期里号码最小的 6 号（6、9~33 都从未出过）
        assertEquals(listOf(1, 2, 3, 4, 5), s.hotReds(5))
        assertEquals(7, s.hotReds(6)[5])
        assertEquals(6, s.coldReds(6).first())
    }

    @Test
    fun `输入乱序不影响遗漏`() {
        val draws = listOf(
            draw("2026003", listOf(1, 2, 3, 4, 5, 7), 1),
            draw("2026001", listOf(1, 2, 3, 4, 5, 7), 1),
            draw("2026002", listOf(1, 2, 3, 4, 5, 8), 2),
        )
        val s = computeStats(draws)
        assertEquals(0, s.redOmission[7])
        assertEquals(1, s.redOmission[8])
    }

    @Test
    fun `形态手算一致`() {
        val draws = listOf(
            draw("2026001", listOf(1, 2, 3, 4, 5, 6), 1),
            draw("2026002", listOf(11, 12, 22, 23, 32, 33), 2),
        )
        val s = computeShape(draws)
        assertEquals(2, s.draws)
        // 和值 21 与 133
        assertEquals(77.0, s.sumMean, 1e-9)
        assertEquals(21, s.sumMin)
        assertEquals(133, s.sumMax)
        assertEquals(21, s.sumLow)
        assertEquals(133, s.sumHigh)
        // 奇数都是 3 个
        assertEquals(3.0, s.oddMean, 1e-9)
        // 三区球次：第一期 6/0/0，第二期 1/2/3
        assertEquals(7.0 / 12, s.zoneShare[0], 1e-9)
        assertEquals(2.0 / 12, s.zoneShare[1], 1e-9)
        assertEquals(3.0 / 12, s.zoneShare[2], 1e-9)
        // 两期都有连号
        assertEquals(1.0, s.consecutiveShare, 1e-9)
    }

    @Test
    fun `空库形态全零`() {
        val s = computeShape(emptyList())
        assertEquals(0, s.draws)
        assertEquals(0.0, s.sumMean, 1e-9)
    }
}
