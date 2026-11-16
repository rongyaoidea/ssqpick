package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.DrawPrizeGrade
import com.ssqpick.model.PrizeTier
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataQualityTest {

    private fun draw(
        code: String,
        date: String,
        red: List<Int> = listOf(1, 2, 3, 4, 5, 6),
        blue: Int = 7,
    ) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = red,
        blue = blue,
        prizeGrades = listOf(
            DrawPrizeGrade(PrizeTier.FIRST, 1, 5_000_000),
            DrawPrizeGrade(PrizeTier.SECOND, 10, 150_000),
        ),
    )

    @Test
    fun 连续完整的数据是健康的() {
        val draws = listOf(
            draw("2026001", "2026-01-01"),
            draw("2026002", "2026-01-04"),
            draw("2026003", "2026-01-06"),
        )
        val q = dataQualityOf(draws, LocalDate.parse("2026-01-07"))
        assertTrue(q.healthy)
        assertEquals(3, q.totalDraws)
        assertEquals(0, q.missingCodeCount)
        assertEquals(0, q.invalidCodeCount)
        assertEquals(1L, q.daysSinceLatest)
    }

    @Test
    fun 缺期会报出具体期号() {
        val draws = listOf(
            draw("2026001", "2026-01-01"),
            draw("2026003", "2026-01-06"),
        )
        val q = dataQualityOf(draws, LocalDate.parse("2026-01-07"))
        assertEquals(1, q.missingCodeCount)
        assertEquals(listOf("2026002"), q.missingCodeSamples)
        assertFalse(q.healthy)
    }

    @Test
    fun 号码不合法会被计数() {
        val draws = listOf(
            draw("2026001", "2026-01-01", red = listOf(1, 2, 3, 4, 5)),
            draw("2026002", "2026-01-04", red = listOf(1, 1, 2, 3, 4, 5)),
            draw("2026003", "2026-01-06", blue = 17),
        )
        val q = dataQualityOf(draws, LocalDate.parse("2026-01-07"))
        assertEquals(3, q.invalidCodeCount)
        assertEquals(listOf("2026001", "2026002", "2026003"), q.invalidCodeSamples)
        assertFalse(q.healthy)
    }

    @Test
    fun 最新一期过旧会判不健康() {
        val draws = listOf(draw("2026001", "2026-01-01"))
        val q = dataQualityOf(draws, LocalDate.parse("2026-01-10"))
        assertEquals(9L, q.daysSinceLatest)
        assertFalse(q.healthy)
    }

    @Test
    fun 非开奖日会被计数() {
        val q = dataQualityOf(
            listOf(draw("2026001", "2026-01-05")),
            LocalDate.parse("2026-01-06"),
        )
        assertEquals(1, q.offScheduleCount)
        assertFalse(q.healthy)
    }

    @Test
    fun 缺一二等奖金额会被计数() {
        val noPrize = draw("2026001", "2026-01-01").copy(prizeGrades = emptyList())
        val q = dataQualityOf(listOf(noPrize), LocalDate.parse("2026-01-02"))
        assertEquals(1, q.missingPrizeCount)
        assertFalse(q.healthy)
    }

    @Test
    fun 空库不健康() {
        val q = dataQualityOf(emptyList(), LocalDate.parse("2026-01-01"))
        assertFalse(q.healthy)
        assertEquals(0, q.totalDraws)
    }
}
