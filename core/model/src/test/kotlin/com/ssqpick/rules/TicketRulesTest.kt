package com.ssqpick.rules

import com.ssqpick.model.Bet
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 核心：用暴力枚举交叉验证 [TicketRules.prizeBreakdown] 的组合计数公式。
 * 枚举是"显然正确"的参照实现，任何公式错误都会在这里暴露。
 */
class PrizeBreakdownTest {

    private fun draw(vararg red: Int, blue: Int) = Draw(
        code = "2026001",
        date = kotlinx.datetime.LocalDate(2026, 1, 1),
        red = red.sorted(),
        blue = blue,
    )

    /** 参照实现：老老实实展开成注再逐注判定。 */
    private fun bruteForce(draft: TicketDraft, d: Draw): Map<PrizeTier, Long> {
        val acc = HashMap<PrizeTier, Long>()
        for (bet in TicketRules.expand(draft, limit = Long.MAX_VALUE)) {
            val tier = TicketRules.tierFor(
                TicketRules.countRedMatches(bet.red, d.red),
                d.blue in bet.blues,
            )
            if (tier != null) acc[tier] = (acc[tier] ?: 0L) + 1L
        }
        return acc
    }

    @Test
    fun `binom 基本值`() {
        assertEquals(1L, TicketRules.binom(33, 0))
        assertEquals(33L, TicketRules.binom(33, 1))
        assertEquals(1_107_568L, TicketRules.binom(33, 6))
        assertEquals(1L, TicketRules.binom(33, 33))
        assertEquals(0L, TicketRules.binom(4, 6))
        assertEquals(0L, TicketRules.binom(-1, 0))
    }

    @Test
    fun `tier 与官方奖级表一致`() {
        assertNull(TicketRules.tierFor(0, true).let { if (it == PrizeTier.SIXTH) null else it })
        assertEquals(PrizeTier.FIRST, TicketRules.tierFor(6, true))
        assertEquals(PrizeTier.SECOND, TicketRules.tierFor(6, false))
        assertEquals(PrizeTier.THIRD, TicketRules.tierFor(5, true))
        assertEquals(PrizeTier.FOURTH, TicketRules.tierFor(5, false))
        assertEquals(PrizeTier.FOURTH, TicketRules.tierFor(4, true))
        assertEquals(PrizeTier.FIFTH, TicketRules.tierFor(4, false))
        assertEquals(PrizeTier.FIFTH, TicketRules.tierFor(3, true))
        assertEquals(PrizeTier.SIXTH, TicketRules.tierFor(2, true))
        assertEquals(PrizeTier.SIXTH, TicketRules.tierFor(1, true))
        assertEquals(PrizeTier.SIXTH, TicketRules.tierFor(0, true))
        assertNull(TicketRules.tierFor(3, false))
        assertNull(TicketRules.tierFor(0, false))
    }

    @Test
    fun `单式全中一等奖`() {
        val d = draw(1, 2, 3, 4, 5, 6, blue = 7)
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        val bd = TicketRules.prizeBreakdown(draft, d)
        assertEquals(mapOf(PrizeTier.FIRST to 1L), bd)
        assertEquals(1L, TicketRules.betCount(draft))
    }

    @Test
    fun `单式中六等奖仅蓝球命中`() {
        val d = draw(10, 11, 12, 13, 14, 15, blue = 7)
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        assertEquals(mapOf(PrizeTier.SIXTH to 1L), TicketRules.prizeBreakdown(draft, d))
    }

    @Test
    fun `注数计算 单式与复式`() {
        assertEquals(1L, TicketRules.betCount(TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(1))))
        assertEquals(
            7L,
            TicketRules.betCount(TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6, 7), bluePicked = setOf(1))),
        )
        assertEquals(
            6L,
            TicketRules.betCount(TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(1, 2, 3, 4, 5, 6))),
        )
        assertEquals(
            42L,
            TicketRules.betCount(TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6, 7), bluePicked = setOf(1, 2, 3, 4, 5, 6))),
        )
        assertEquals(
            TicketRules.binom(20, 6).toLong() * 16,
            TicketRules.betCount(
                TicketDraft(redPicked = (1..20).toSet(), bluePicked = (1..16).toSet()),
            ),
        )
    }

    @Test
    fun `注数计算 胆拖`() {
        // 胆1 + 拖7 = C(7,5) = 21
        assertEquals(
            21L,
            TicketRules.betCount(
                TicketDraft(redBold = setOf(1), redPicked = setOf(2, 3, 4, 5, 6, 7, 8), bluePicked = setOf(9)),
            ),
        )
        // 胆2 + 拖8 = C(8,4) = 70
        assertEquals(
            70L,
            TicketRules.betCount(
                TicketDraft(redBold = setOf(1, 2), redPicked = (3..10).toSet(), bluePicked = setOf(9)),
            ),
        )
        // 红胆2+拖8 = C(8,4)=70，蓝胆1+蓝拖4 = 4 注 -> 70*4 = 280 注
        assertEquals(
            280L,
            TicketRules.betCount(
                TicketDraft(redBold = setOf(1, 2), redPicked = (3..10).toSet(), blueBold = setOf(5), bluePicked = setOf(1, 2, 3, 4)),
            ),
        )
    }

    @Test
    fun `校验规则`() {
        assertTrue(TicketRules.validate(TicketDraft()).let { !it.isValid && it.error == TicketRules.ValidationError.EMPTY })
        assertTrue(
            TicketRules.validate(TicketDraft(redPicked = setOf(1, 2, 3), bluePicked = setOf(1))).let {
                it.error == TicketRules.ValidationError.RED_NOT_ENOUGH
            },
        )
        assertTrue(
            TicketRules.validate(TicketDraft(redBold = setOf(1, 2, 3, 4, 5, 6), redPicked = setOf(7), bluePicked = setOf(1)))
                .let { it.error == TicketRules.ValidationError.RED_BOLD_TOO_MANY },
        )
        assertTrue(
            TicketRules.validate(TicketDraft(redBold = setOf(1), redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(1)))
                .let { it.error == TicketRules.ValidationError.RED_BOLD_OVERLAP },
        )
        assertTrue(
            TicketRules.validate(TicketDraft(redPicked = (1..21).toSet(), bluePicked = setOf(1)))
                .let { it.error == TicketRules.ValidationError.RED_TOO_MANY },
        )
        // 20 红 + 2 蓝 = C(20,6)*2 = 77,520 > 50,000 上限
        assertTrue(
            TicketRules.validate(TicketDraft(redPicked = (1..20).toSet(), bluePicked = setOf(1, 2)))
                .let { it.error == TicketRules.ValidationError.TOO_MANY_BETS },
        )
        val ok = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(1))
        assertTrue(TicketRules.validate(ok).isValid)
    }

    /** 决定性测试：组合计数公式必须与暴力展开完全一致。 */
    @Test
    fun `组合计数与暴力枚举一致`() {
        val rng = Random(20261009)
        var checked = 0
        repeat(400) {
            // 生成一张随机合法票：红 6~9 个（可带 0~2 个胆码），蓝 1~4 个（可带 0~1 个胆码）
            val nRed = rng.nextInt(6, 10)
            val redPool = (1..33).shuffled(rng).take(nRed)
            val redBold = if (nRed > 6) {
                redPool.shuffled(rng).take(rng.nextInt(0, 3))
            } else {
                emptyList()
            }
            val redPicked = redPool - redBold
            val bluePool = (1..16).shuffled(rng).take(rng.nextInt(1, 5))
            val blueBold = if (bluePool.size > 1 && rng.nextBoolean()) {
                listOf(bluePool.shuffled(rng).first())
            } else {
                emptyList()
            }
            val bluePicked = bluePool - blueBold

            val draft = TicketDraft(
                redBold = redBold.toSet(),
                redPicked = redPicked.toSet(),
                blueBold = blueBold.toSet(),
                bluePicked = bluePicked.toSet(),
            )
            check(TicketRules.validate(draft).isValid) { "生成的票应合法: $draft" }

            val d = draw(
                *(1..33).shuffled(rng).take(6).toIntArray(),
                blue = rng.nextInt(1, 17),
            )
            val fast = TicketRules.prizeBreakdown(draft, d)
            val slow = bruteForce(draft, d).filterValues { it > 0 }
            assertEquals("draft=$draft draw=$d", slow, fast)
            checked++
        }
        assertTrue("应该真的跑了用例", checked == 400)
    }

    /** 复式在"全不中"时注数应恰好等于总注数。 */
    @Test
    fun `没有命中时红球分布覆盖全部注`() {
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6, 7), bluePicked = setOf(1, 2))
        val d = draw(20, 21, 22, 23, 24, 25, blue = 16)
        val counts = TicketRules.redMatchCounts(draft, d)
        val total = counts.values.sum()
        assertEquals(TicketRules.redCombos(draft), total)
    }

    /** 蓝球胆拖：胆码命中时全部注都算中蓝。 */
    @Test
    fun `蓝球胆拖命中`() {
        val d = draw(1, 2, 3, 4, 5, 6, blue = 5)
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), blueBold = setOf(5), bluePicked = setOf(7, 8, 9))
        val bd = TicketRules.prizeBreakdown(draft, d)
        assertEquals(3L, bd[PrizeTier.FIRST] ?: 0L)
    }
}
