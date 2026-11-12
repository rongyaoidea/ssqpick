package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 排除历史中奖组合：快路径必须与组合计数完全一致，否则会把“没中过”的
 * 号码误判为已中奖，过滤器就变成了随机禁号。
 */
class HistoryFilterTest {

    private fun draw(code: String, reds: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate(2026, 1, 1),
        red = reds,
        blue = blue,
    )

    private fun draftOf(reds: Collection<Int>, blues: Collection<Int>) = TicketDraft(
        redPicked = reds.toSet(),
        bluePicked = blues.toSet(),
    )

    @Test
    fun `快路径与组合计数逐项一致`() {
        val rng = Random(20261112)
        repeat(300) {
            val reds = (1..33).shuffled(rng).take(rng.nextInt(6, 15))
            val blues = (1..16).shuffled(rng).take(rng.nextInt(1, 5))
            val d = draw(
                code = "T$it",
                reds = (1..33).shuffled(rng).take(6),
                blue = rng.nextInt(1, 17),
            )
            val draft = draftOf(reds, blues)
            for (tier in listOf(PrizeTier.FIRST, PrizeTier.SECOND, PrizeTier.THIRD)) {
                val expected = TicketRules.prizeBreakdown(draft, d).containsKey(tier)
                assertEquals(
                    "红 $reds 蓝 $blues 对 $d 的 ${tier.label} 判定不一致",
                    expected,
                    HistoryFilter.hits(draft, d, tier),
                )
            }
        }
    }

    @Test
    fun `胆拖走通用路径也不误判`() {
        val d = draw("T1", listOf(1, 2, 3, 4, 5, 6), 7)
        val bold = TicketDraft(
            redBold = setOf(1, 2, 3),
            redPicked = setOf(4, 5, 6),
            bluePicked = setOf(7),
        )
        for (tier in PrizeTier.entries) {
            assertEquals(
                "胆拖 ${tier.label}",
                TicketRules.prizeBreakdown(bold, d).containsKey(tier),
                HistoryFilter.hits(bold, d, tier),
            )
        }
    }

    @Test
    fun `命中三等奖会重摇直到通过`() {
        val d = draw("T1", listOf(1, 2, 3, 4, 5, 6), 7)
        val candidates = ArrayDeque(
            listOf(
                // 5 红 + 开奖蓝 → 三等奖
                listOf(1, 2, 3, 4, 5, 8) to listOf(7),
                // 同样 5 红 + 开奖蓝 → 三等奖
                listOf(1, 2, 3, 4, 5, 9) to listOf(7),
                // 4 红 + 开奖蓝 → 四等奖，应通过
                listOf(1, 2, 3, 4, 8, 9) to listOf(7),
            ),
        )
        val sampled = HistoryFilter.sample(
            redCount = 6,
            blueCount = 1,
            draws = listOf(d),
            tiers = setOf(PrizeTier.THIRD),
            maxAttempts = 10,
        ) { candidates.removeFirst() }

        assertEquals(3, sampled?.attempts)
        assertEquals(listOf(1, 2, 3, 4, 8, 9), sampled?.reds)
        assertFalse(HistoryFilter.hitsAny(draftOf(sampled!!.reds, sampled.blues), listOf(d), setOf(PrizeTier.THIRD)))
    }

    @Test
    fun `一直命中时达到上限返回空`() {
        val d = draw("T1", listOf(1, 2, 3, 4, 5, 6), 7)
        val same = listOf(1, 2, 3, 4, 5, 6) to listOf(7)
        val sampled = HistoryFilter.sample(
            redCount = 6,
            blueCount = 1,
            draws = listOf(d),
            tiers = setOf(PrizeTier.FIRST),
            maxAttempts = 3,
        ) { same }
        assertNull(sampled)
    }

    @Test
    fun `没有历史数据或没有勾选奖级时返回空`() {
        val d = draw("T1", listOf(1, 2, 3, 4, 5, 6), 7)
        assertNull(HistoryFilter.sample(6, 1, emptyList(), setOf(PrizeTier.FIRST)))
        assertNull(HistoryFilter.sample(6, 1, listOf(d), emptySet()))
    }

    @Test
    fun `真实取样不会命中所选奖级`() {
        val draws = listOf(
            draw("T1", listOf(1, 2, 3, 4, 5, 6), 7),
            draw("T2", listOf(5, 9, 12, 18, 25, 33), 16),
            draw("T3", listOf(2, 8, 14, 20, 26, 31), 3),
        )
        val tiers = setOf(PrizeTier.FIRST, PrizeTier.SECOND, PrizeTier.THIRD)
        repeat(20) {
            val sampled = HistoryFilter.sample(
                redCount = 10,
                blueCount = 4,
                draws = draws,
                tiers = tiers,
            )
            assertTrue("应按期摇出号码", sampled != null)
            val draft = draftOf(sampled!!.reds, sampled.blues)
            assertEquals(10, draft.redPicked.size)
            assertEquals(4, draft.bluePicked.size)
            assertFalse(
                "取样结果不应命中历史 ${tiers.joinToString { it.label }}",
                HistoryFilter.hitsAny(draft, draws, tiers),
            )
        }
    }
}
