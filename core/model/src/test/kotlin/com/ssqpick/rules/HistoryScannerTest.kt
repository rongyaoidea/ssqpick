package com.ssqpick.rules

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

class HistoryScannerTest {

    private fun draw(code: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate(2026, 1, 1),
        red = red.sorted(),
        blue = blue,
        prizeGrades = listOf(
            DrawPrizeGrade(PrizeTier.FIRST, 3, 5_000_000),
            DrawPrizeGrade(PrizeTier.SECOND, 30, 150_000),
        ),
    )

    @Test
    fun `从未中过任何奖`() {
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        val draws = (0 until 50).map {
            draw("2026${it.toString().padStart(3, '0')}", (20..25).toList(), 16)
        }
        val r = HistoryScanner.scan(draft, draws)
        assertNull(r.highestTier)
        assertTrue(!r.hasEverWon)
        assertEquals(50, r.checkedDraws)
        assertEquals(0, r.bestRedMatches)
    }

    @Test
    fun `命中过三等奖`() {
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        val draws = listOf(
            draw("2026001", listOf(1, 2, 3, 4, 5, 6), blue = 9), // 6红0蓝 -> 二等奖(浮动)
            draw("2026002", listOf(1, 2, 3, 4, 5, 20), blue = 7), // 5红1蓝 -> 三等奖
        )
        val r = HistoryScanner.scan(draft, draws)
        assertEquals(PrizeTier.SECOND, r.highestTier)
        assertEquals("2026001", r.highestTierDrawCode)
        assertEquals(150_000L, r.highestTierAmountYuan)
        assertEquals(6, r.bestRedMatches)
    }

    @Test
    fun `二等奖取开奖公告实付额`() {
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(16))
        val draws = listOf(draw("2026001", listOf(1, 2, 3, 4, 5, 6), blue = 9))
        val (total, breakdown) = TicketRules.evaluate(draft, draws[0])
        assertEquals(150_000L, total)
        assertEquals(mapOf(PrizeTier.SECOND to 1L), breakdown)
    }

    @Test
    fun `复式在历史中累计中奖`() {
        // 7 红 + 1 蓝的复式，共 7 注
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6, 7), bluePicked = setOf(16))
        val draws = listOf(
            draw("2026001", listOf(1, 2, 3, 4, 5, 6), blue = 16), // 命中 6 红+蓝
            draw("2026002", listOf(1, 2, 3, 4, 5, 20), blue = 16), // 5 红+蓝
        )
        val r = HistoryScanner.scan(draft, draws)
        assertEquals(PrizeTier.FIRST, r.highestTier)
        assertEquals(6, r.bestRedMatches)
    }

    @Test
    fun `OddsInfo 概率单调趋近于1`() {
        val p1 = OddsInfo.appearProbability(1)
        val p100 = OddsInfo.appearProbability(100)
        val p10000 = OddsInfo.appearProbability(10_000)
        assertTrue(p1 < p100 && p100 < p10000)
        assertTrue(p10000 < 0.01)
        assertEquals(17_721_088L, OddsInfo.TOTAL_SPACE)
    }

    @Test
    fun `OddsInfo 文本可读`() {
        val t = OddsInfo.logProbabilityText()
        assertTrue(t.contains("17,721,088"))
    }
}

class NumberGeneratorTest {

    @Test
    fun `生成一注合法号码`() {
        repeat(200) {
            val bet = NumberGenerator.generateBet()
            assertEquals(6, bet.red.size)
            assertEquals(6, bet.red.distinct().size)
            assertTrue(bet.red.all { it in 1..33 })
            assertTrue(bet.red == bet.red.sorted())
            assertTrue(bet.blues.single() in 1..16)
        }
    }

    @Test
    fun `sample 均匀性不塌陷`() {
        // 大量抽样下每个红号出现次数应接近 1/33
        val counts = IntArray(34)
        repeat(20_000) {
            for (v in NumberGenerator.sample(33, 6)) counts[v]++
        }
        val expected = 20_000 * 6 / 33.0
        for (i in 1..33) {
            assertTrue(
                "号 $i 出现 ${counts[i]} 次，偏离期望 $expected 过大",
                Math.abs(counts[i] - expected) < 5 * Math.sqrt(expected),
            )
        }
    }

    @Test
    fun `sample 边界`() {
        assertEquals(emptyList<Int>(), NumberGenerator.sample(33, 0))
        assertEquals((1..33).toList(), NumberGenerator.sample(33, 33))
    }

    @Test
    fun `sampleFrom无放回且不出池`() {
        val pool = (1..33).filter { it !in setOf(1, 2, 3) }
        repeat(50) {
            val s = NumberGenerator.sampleFrom(pool, 6)
            assertEquals(6, s.toSet().size)
            assertTrue(s.all { it in pool })
        }
    }

    @Test
    fun `sampleOrdered 是随机序不是恒升序`() {
        var sawNonSorted = false
        repeat(30) {
            val s = NumberGenerator.sampleOrdered(33, 6)
            assertEquals(6, s.size)
            assertEquals(6, s.toSet().size)
            assertTrue(s.all { it in 1..33 })
            if (s != s.sorted()) sawNonSorted = true
        }
        // 6 个号恰好升序的概率是 1/720，30 次全升序的概率可忽略
        assertTrue("30 次抽取中至少一次不是升序", sawNonSorted)
    }

    @Test
    fun `sampleFrom池不够截断为空`() {
        assertTrue(NumberGenerator.sampleFrom(emptyList(), 3).isEmpty())
        assertEquals(2, NumberGenerator.sampleFrom(listOf(7, 8), 5).size)
        assertTrue(NumberGenerator.sampleFrom(listOf(7, 8), 0).isEmpty())
    }
}
