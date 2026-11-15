package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * 位掩码是模拟器热循环的地基：它必须与原有 Draw 判定逐项一致，
 * 否则实验室里的“历史重放”会和真实对奖打出两套结果。
 */
class TicketMasksTest {

    private fun draw(reds: List<Int>, blue: Int) = Draw(
        code = "T",
        date = LocalDate(2026, 1, 1),
        red = reds,
        blue = blue,
    )

    @Test
    fun `普通复式与胆拖的掩码判定与 Draw 判定一致`() {
        val rng = Random(20261116)
        repeat(500) {
            val redPool = (1..33).shuffled(rng).take(rng.nextInt(6, 21))
            val redBold = redPool.take(rng.nextInt(0, minOf(5, redPool.size - 1) + 1)).toSet()
            val redPicked = redPool.drop(redBold.size).toSet()
            val bluePool = (1..16).shuffled(rng).take(rng.nextInt(1, 5))
            // 蓝胆拖必须留至少一个拖码，否则是非法草稿。
            val blueBold = if (bluePool.size >= 2 && rng.nextBoolean()) setOf(bluePool.first()) else emptySet()
            val bluePicked = bluePool.drop(blueBold.size).toSet()
            val draft = TicketDraft(
                redBold = redBold,
                redPicked = redPicked,
                blueBold = blueBold,
                bluePicked = bluePicked,
            )
            val d = draw(
                reds = (1..33).shuffled(rng).take(6),
                blue = rng.nextInt(1, 17),
            )
            val masks = TicketMasks.of(draft)

            assertEquals(
                "红球分布不一致：$draft vs $d",
                TicketRules.redMatchCounts(draft, d),
                TicketRules.redMatchCounts(masks, maskOf(d.red)),
            )
            assertEquals(
                "蓝球分布不一致：$draft vs $d",
                TicketRules.blueMatchCounts(draft, d),
                TicketRules.blueMatchCounts(masks, d.blue),
            )
            assertEquals(
                "奖级分布不一致：$draft vs $d",
                TicketRules.prizeBreakdown(draft, d),
                TicketRules.prizeBreakdown(masks, maskOf(d.red), d.blue),
            )
        }
    }

    @Test
    fun `无胆码快路径与普通构造一致`() {
        val draft = TicketDraft(
            redPicked = setOf(1, 2, 3, 4, 5, 6, 7, 8),
            bluePicked = setOf(3, 9),
        )
        val d = draw(listOf(2, 4, 6, 8, 10, 12), 9)
        assertEquals(
            TicketRules.prizeBreakdown(draft, d),
            TicketRules.prizeBreakdown(TicketMasks.of(draft), maskOf(d.red), d.blue),
        )
    }

    @Test
    fun `累加奖级计数与组合计数一致`() {
        val draft = TicketDraft(
            redBold = setOf(1, 2),
            redPicked = setOf(3, 4, 5, 6, 7, 8, 9),
            bluePicked = setOf(2, 9, 14),
        )
        val d = draw(listOf(1, 3, 5, 8, 12, 20), 9)
        val expected = TicketRules.prizeBreakdown(draft, d)
        val out = LongArray(PrizeTier.entries.size)
        TicketRules.accumulateTierCounts(TicketMasks.of(draft), maskOf(d.red), d.blue, out)
        for (tier in PrizeTier.entries) {
            assertEquals("${tier.label} 计数", expected[tier] ?: 0L, out[tier.ordinal])
        }
    }

    @Test
    fun `奖金口径的掩码版本与 Draw 版本一致`() {
        val d = Draw(
            code = "T",
            date = LocalDate(2026, 1, 1),
            red = listOf(1, 2, 3, 4, 5, 6),
            blue = 7,
            prizeGrades = listOf(
                com.ssqpick.model.DrawPrizeGrade(PrizeTier.FIRST, 2, 8_888_888),
                com.ssqpick.model.DrawPrizeGrade(PrizeTier.SECOND, 5, 222_222),
            ),
        )
        for (tier in PrizeTier.entries) {
            assertEquals(
                "奖金 ${tier.label}",
                TicketRules.amountFor(tier, d),
                TicketRules.amountFor(tier, 8_888_888, 222_222),
            )
        }
    }
}
