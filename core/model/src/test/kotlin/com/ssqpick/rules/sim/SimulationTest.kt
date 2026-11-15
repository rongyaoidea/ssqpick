package com.ssqpick.rules.sim

import com.ssqpick.model.Draw
import com.ssqpick.model.DrawPrizeGrade
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 模拟引擎的硬约束：可复现、口径与 TicketRules 一致、预算/取消/历史重放可手算。
 */
class SimulationTest {

    private fun draw(
        reds: List<Int> = listOf(1, 2, 3, 4, 5, 6),
        blue: Int = 7,
        first: Long = 8_000_000,
        second: Long = 200_000,
    ) = Draw(
        code = "T",
        date = LocalDate(2026, 1, 1),
        red = reds,
        blue = blue,
        prizeGrades = listOf(
            DrawPrizeGrade(PrizeTier.FIRST, 1, first),
            DrawPrizeGrade(PrizeTier.SECOND, 2, second),
        ),
    )

    @Test
    fun `同一种子结果完全可复现`() {
        val spec = SimSpec(
            source = SimSource.UNIFORM,
            strategy = SimStrategy.RandomSingle,
            paths = 20,
            drawsPerPath = 50,
            seed = 42,
        )
        val a = simulate(spec)!!
        val b = simulate(spec)!!
        assertEquals(a.totalCostYuan, b.totalCostYuan)
        assertEquals(a.totalPrizeYuan, b.totalPrizeYuan)
        assertEquals(a.roi, b.roi)
        assertEquals(a.fan.p50.toList(), b.fan.p50.toList())
        assertEquals(a.tierStats, b.tierStats)
    }

    @Test
    fun `历史重放加守号可以手算出一等奖`() {
        val d = draw(first = 9_999_999)
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        val spec = SimSpec(
            source = SimSource.HISTORICAL,
            strategy = SimStrategy.FixedDraft(draft),
            paths = 1,
            drawsPerPath = 1,
            seed = 1,
        )
        val r = simulate(spec, listOf(d))!!
        assertEquals(2L, r.totalCostYuan)
        assertEquals(9_999_999L, r.totalPrizeYuan)
        assertEquals(1L, r.tierStats.getValue(PrizeTier.FIRST).hits)
        assertEquals(0L, r.tierStats.getValue(PrizeTier.SECOND).hits)
        assertEquals(1.0, r.profitableShare, 0.0)
        assertEquals(0.0, r.ruinShare, 0.0)
        assertEquals(listOf(0, 1), r.fan.xs.toList())
        assertEquals(9_999_997f, r.fan.p50.last(), 1f)
    }

    @Test
    fun `预算不足时路径提前停止并计入破产`() {
        val spec = SimSpec(
            source = SimSource.UNIFORM,
            strategy = SimStrategy.RandomSingle,
            paths = 3,
            drawsPerPath = 10,
            budgetYuan = 2,
            seed = 7,
        )
        val r = simulate(spec)!!
        assertEquals(6L, r.totalCostYuan)
        assertEquals(1.0, r.ruinShare, 0.0)
        assertEquals(1, r.medianSurvivalDraws)
        assertEquals(11, r.fan.xs.size)
    }

    @Test
    fun `取消时返回空而不是半份结果`() {
        val spec = SimSpec(
            source = SimSource.UNIFORM,
            strategy = SimStrategy.RandomSingle,
            paths = 10,
            drawsPerPath = 10,
            seed = 1,
        )
        assertNull(simulate(spec, onProgress = { }, shouldCancel = { true }))
    }

    @Test
    fun `随机复式按注数计成本`() {
        val spec = SimSpec(
            source = SimSource.UNIFORM,
            strategy = SimStrategy.RandomCompound(redCount = 7, blueCount = 2),
            paths = 1,
            drawsPerPath = 1,
            seed = 2,
        )
        val r = simulate(spec)!!
        // C(7,6) × 2 蓝 = 14 注，每注 2 元
        assertEquals(28L, r.totalCostYuan)
        assertEquals(14L, r.totalBetDraws)
    }

    @Test
    fun `理论概率与官方头奖空间一致`() {
        assertEquals(17_721_088L, SimOdds.firstPrizeSpace)
        assertEquals(1.0 / 17_721_088.0, SimOdds.probability(PrizeTier.FIRST), 1e-15)
        val total = PrizeTier.entries.sumOf { SimOdds.probability(it) }
        // 双色球任意奖级总概率约 6.7%，这里只要求落在合理区间
        assertTrue("总中奖概率 $total", total in 0.05..0.08)
    }

    @Test
    fun `理论期望命中数随注期线性增长`() {
        val spec = SimSpec(
            source = SimSource.UNIFORM,
            strategy = SimStrategy.RandomSingle,
            paths = 50,
            drawsPerPath = 100,
            seed = 9,
        )
        val r = simulate(spec)!!
        assertEquals(5_000L, r.totalBetDraws)
        val expectedFirst = r.tierStats.getValue(PrizeTier.FIRST).expected
        assertTrue(abs(expectedFirst - 5_000.0 / 17_721_088.0) < 1e-12)
        assertNotNull(r.tierStats[PrizeTier.SIXTH])
    }

    @Test
    fun `未同步历史数据时拒绝历史重放`() {
        val spec = SimSpec(
            source = SimSource.HISTORICAL,
            strategy = SimStrategy.RandomSingle,
            paths = 1,
            drawsPerPath = 1,
            seed = 1,
        )
        var failed = false
        try {
            simulate(spec, emptyList())
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
