package com.ssqpick.rules

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawScheduleTest {

    private fun ldt(s: String) = LocalDateTime.parse(s)

    @Test
    fun `开奖当天未到21点15则当期为下一期`() {
        // 2026-01-04 是周日
        val next = DrawSchedule.nextDrawAfter(ldt("2026-01-04T20:00:00"))
        assertEquals(ldt("2026-01-04T21:15:00"), next)
    }

    @Test
    fun `开奖当天已过21点15则顺延到周四`() {
        val next = DrawSchedule.nextDrawAfter(ldt("2026-01-04T21:30:00"))
        assertEquals(ldt("2026-01-06T21:15:00"), next)
    }

    @Test
    fun `周三的开奖是周四`() {
        val next = DrawSchedule.nextDrawAfter(ldt("2026-01-07T09:00:00"))
        assertEquals(ldt("2026-01-08T21:15:00"), next)
    }

    @Test
    fun `周五的开奖是周日`() {
        val next = DrawSchedule.nextDrawAfter(ldt("2026-01-09T12:00:00"))
        assertEquals(ldt("2026-01-11T21:15:00"), next)
    }

    @Test
    fun `周一的开奖是周二`() {
        val next = DrawSchedule.nextDrawAfter(ldt("2026-01-05T00:01:00"))
        assertEquals(ldt("2026-01-06T21:15:00"), next)
    }
}
