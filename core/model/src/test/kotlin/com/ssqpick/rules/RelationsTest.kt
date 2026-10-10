package com.ssqpick.rules

import com.ssqpick.model.Draw
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 同出/跟随/同期：手算小库逐项对齐，改口径必先改这里。 */
class RelationsTest {

    private fun draw(code: String, date: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = red.sorted(),
        blue = blue,
    )

    // ---------------------------------------------------------------- 共现

    @Test
    fun `共现计数手算一致`() {
        val draws = listOf(
            draw("2026001", "2026-01-01", listOf(1, 2, 3, 4, 5, 6), 1),
            draw("2026002", "2026-01-02", listOf(1, 2, 7, 8, 9, 10), 2),
        )
        val r = coOccurrence(draws, topN = 3)
        assertEquals(2, r.draws)
        // (1,2) 两期都同出
        assertEquals(PairStat(1, 2, 2), r.topPairs.first())
        // 理论期望 = 2 × (6/33)(5/32)
        assertEquals(2 * (6.0 / 33.0) * (5.0 / 32.0), r.expectedPerPair, 1e-9)
        // 相克榜首是最低计数（并列时按号码升序）
        assertTrue(r.bottomPairs.all { it.count <= 1 })
    }

    @Test
    fun `空库共现为空`() {
        val r = coOccurrence(emptyList())
        assertEquals(0, r.draws)
        assertTrue(r.topPairs.isEmpty())
    }

    // ---------------------------------------------------------------- 跟随

    @Test
    fun `红球跟随手算一致_最后一期不计`() {
        val draws = listOf(
            draw("2026001", "2026-01-01", listOf(7, 1, 2, 3, 4, 5), 1),
            draw("2026002", "2026-01-02", listOf(12, 13, 14, 15, 16, 17), 2),
            // 第三期也含 7，但它没有"下一期"，不应计入
            draw("2026003", "2026-01-03", listOf(7, 18, 19, 20, 21, 22), 3),
        )
        val r = followersOf(draws, target = 7, isBlue = false)
        assertEquals(1, r.appearances)
        assertEquals(listOf(12, 13, 14, 15, 16, 17), r.followers.map { it.number }.sorted())
        assertTrue(r.followers.all { it.count == 1 })
        assertEquals(1 * 6.0 / 33.0, r.expectedPerNumber, 1e-9)
    }

    @Test
    fun `蓝球跟随手算一致`() {
        val draws = listOf(
            draw("2026001", "2026-01-01", listOf(1, 2, 3, 4, 5, 6), 5),
            draw("2026002", "2026-01-02", listOf(1, 2, 3, 4, 5, 6), 9),
        )
        val r = followersOf(draws, target = 5, isBlue = true)
        assertEquals(1, r.appearances)
        assertEquals(FollowStat(9, 1), r.followers.single())
        assertEquals(1 * 1.0 / 16.0, r.expectedPerNumber, 1e-9)
    }

    @Test
    fun `跟随_输入乱序也按时间处理`() {
        val draws = listOf(
            draw("2026002", "2026-01-02", listOf(12, 13, 14, 15, 16, 17), 2),
            draw("2026001", "2026-01-01", listOf(7, 1, 2, 3, 4, 5), 1),
        )
        val r = followersOf(draws, target = 7, isBlue = false)
        assertEquals(1, r.appearances)
        assertEquals(12, r.followers.first().number)
    }

    // ---------------------------------------------------------------- 同期

    @Test
    fun `历年同期按后缀匹配且只取往年`() {
        val target = draw("2026116", "2026-10-10", listOf(1, 2, 3, 4, 5, 6), 7)
        val draws = listOf(
            target,
            draw("2025116", "2025-10-09", listOf(1, 2, 3, 10, 11, 12), 7), // 重合 3 红 + 蓝
            draw("2024116", "2024-10-08", listOf(1, 20, 21, 22, 23, 24), 9), // 重合 1 红
            draw("2026115", "2026-10-08", listOf(1, 2, 3, 4, 5, 6), 7), // 后缀不同，排除
        )
        val matches = samePeriodOf(draws, target)
        assertEquals(listOf("2025116", "2024116"), matches.map { it.draw.code })
        assertEquals(3, matches[0].redOverlap)
        assertTrue(matches[0].blueMatch)
        assertEquals(1, matches[1].redOverlap)
        assertTrue(!matches[1].blueMatch)
    }

    @Test
    fun `历年同期无匹配返回空`() {
        val target = draw("2026116", "2026-10-10", listOf(1, 2, 3, 4, 5, 6), 7)
        assertTrue(samePeriodOf(listOf(target), target).isEmpty())
    }

    @Test
    fun `往年的今天按月日匹配`() {
        val target = draw("2026116", "2026-10-10", listOf(1, 2, 3, 4, 5, 6), 7)
        val draws = listOf(
            target,
            draw("2019116", "2019-10-10", listOf(1, 2, 30, 31, 32, 33), 7), // 同月日
            draw("2022116", "2022-10-10", listOf(9, 10, 11, 12, 13, 14), 1), // 同月日
            draw("2025116", "2025-10-09", listOf(1, 2, 3, 4, 5, 6), 7), // 差一天，排除
        )
        val matches = onThisDayOf(draws, target)
        assertEquals(listOf("2022116", "2019116"), matches.map { it.draw.code })
        assertEquals(2, matches[1].redOverlap)
        assertTrue(matches[1].blueMatch)
    }
}
