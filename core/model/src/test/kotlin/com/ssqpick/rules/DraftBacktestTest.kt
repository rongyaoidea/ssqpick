package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DraftBacktestTest {

    private fun draw(code: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate(2026, 1, code.takeLast(3).toInt().coerceIn(1, 28)),
        red = red,
        blue = blue,
    )

    @Test
    fun `非法草稿返回空回测`() {
        val r = backtest(TicketDraft(), listOf(draw("2026001", listOf(1, 2, 3, 4, 5, 6), 7)))
        assertEquals(1, r.draws)
        assertEquals(0L, r.totalBetDraws)
        assertEquals(0L, r.costYuan)
    }

    @Test
    fun `三等奖手算一致`() {
        // 草稿红 1-5+7、蓝 7；开奖红 1-6、蓝 7 → 中 5 红 + 蓝 = 三等奖（固定 3000）
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 7), bluePicked = setOf(7))
        val draws = listOf(
            draw("2026001", listOf(1, 2, 3, 4, 5, 6), 7),
            draw("2026002", listOf(10, 11, 12, 13, 14, 15), 8),
        )
        val r = backtest(draft, draws)
        assertEquals(2, r.draws)
        assertEquals(1L, r.betsPerDraw)
        assertEquals(2L, r.totalBetDraws)
        assertEquals(1L, r.redHitBets[5])
        assertEquals(1L, r.redHitBets[0])
        assertEquals(1L, r.blueHitBets)
        assertEquals(1L, r.winningBets)
        assertEquals(2L * 2L, r.costYuan)
        assertEquals(PrizeTier.THIRD.fixedAmountYuan, r.prizeYuan)
    }

    @Test
    fun `理论分布和为一且与已知值一致`() {
        // 无胆单式：中 3 红 = C(6,3)C(27,3)/C(33,6) ≈ 5.28%
        val single = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(1))
        val dist = theoreticalRedDistribution(single)
        assertEquals(1.0, dist.values.sum(), 1e-9)
        assertEquals(58500.0 / 1107568.0, dist[3] ?: -1.0, 1e-9)
        assertEquals(1.0 / 1107568.0, dist[6] ?: -1.0, 1e-12)

        // 红胆拖的分布同样归一
        val dantuo = TicketDraft(
            redBold = setOf(1, 2),
            redPicked = (3..10).toSet(),
            bluePicked = setOf(1),
        )
        assertEquals(1.0, theoreticalRedDistribution(dantuo).values.sum(), 1e-9)

        assertEquals(1.0 / 16, theoreticalBlueHitRate(single), 1e-12)
        val blueBold = TicketDraft(
            redPicked = setOf(1, 2, 3, 4, 5, 6),
            blueBold = setOf(5),
            bluePicked = setOf(7, 8),
        )
        assertEquals(2.0 / 16, theoreticalBlueHitRate(blueBold), 1e-12)
    }

    @Test
    fun `胆拖回测与暴力展开一致`() {
        // 胆 1、拖 2-8（7 个）→ C(7,5)=21 注；直接对比注·期口径
        val draft = TicketDraft(
            redBold = setOf(1),
            redPicked = (2..8).toSet(),
            bluePicked = setOf(9),
        )
        val d = draw("2026001", listOf(1, 2, 3, 4, 5, 6), 9)
        val r = backtest(draft, listOf(d))
        assertEquals(21L, r.totalBetDraws)
        // 暴力：21 注里中 5 红（胆 1 必中 + 拖中 4）的注数 = C(5,4)×C(2,1) = 10
        var five = 0
        for (bet in TicketRules.expand(draft, limit = Long.MAX_VALUE)) {
            if (TicketRules.countRedMatches(bet.red, d.red) == 5) five++
        }
        assertEquals(five.toLong(), r.redHitBets[5])
        assertTrue(abs(r.redHitBets.values.sum() - 21L) < 1e-9)
    }
}
