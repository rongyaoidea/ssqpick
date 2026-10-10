package com.ssqpick.feature.data

import com.ssqpick.model.Draw
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendTest {

    private fun draw(red: List<Int>) = Draw(
        code = "2026001",
        date = LocalDate(2026, 1, 1),
        red = red,
        blue = 1,
    )

    @Test
    fun `走势按时间正序且只取最新窗口`() {
        // 库按日期倒序：最新的在前。和值分别是 21（最新）、81、102（最旧）
        val draws = listOf(
            draw(listOf(1, 2, 3, 4, 5, 6)), // 21
            draw(listOf(11, 12, 13, 14, 15, 16)), // 81
            draw(listOf(13, 14, 15, 16, 20, 24)), // 102
        )
        val trend = trendOf(draws, window = 2)!!
        // 窗口取最新 2 期，再反转成时间正序（旧→新）
        assertEquals(listOf(81f, 21f), trend.sums)
        assertEquals(51f, trend.mean, 1e-3f)
    }

    @Test
    fun `空库返回null`() {
        assertNull(trendOf(emptyList(), window = 100))
    }
}
