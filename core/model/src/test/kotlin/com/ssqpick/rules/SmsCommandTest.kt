package com.ssqpick.rules

import com.ssqpick.model.TicketDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 短信投注指令：每条断言都对齐用户提供的官方示例，改格式必先改这里。
 */
class SmsCommandTest {

    private fun draft(
        reds: Set<Int> = emptySet(),
        blues: Set<Int> = emptySet(),
        redBold: Set<Int> = emptySet(),
    ) = TicketDraft(redBold = redBold, redPicked = reds - redBold, bluePicked = blues)

    @Test
    fun `单式与官方示例一致`() {
        val d = draft(reds = setOf(1, 2, 3, 4, 5, 6), blues = setOf(12))
        assertEquals("00CP#01#010203040506*12#1", SmsCommand.of(d))
    }

    @Test
    fun `倍投与多期后缀`() {
        val d = draft(reds = setOf(1, 2, 3, 4, 5, 6), blues = setOf(1))
        assertEquals("00CP#01#010203040506*01#2", SmsCommand.of(d, times = 2))
        assertEquals("00CP#01#010203040506*01#1#5", SmsCommand.of(d, periods = 5))
        assertEquals("00CP#01#010203040506*01#3#2", SmsCommand.of(d, times = 3, periods = 2))
        // 期数默认不带：periods=1 时不出 #期数
        assertEquals("00CP#01#010203040506*01#1", SmsCommand.of(d, periods = 1))
    }

    @Test
    fun `复式与官方示例一致`() {
        val d = draft(reds = setOf(1, 2, 3, 4, 5, 6, 7), blues = setOf(1, 12))
        assertEquals("00CP#01#01020304050607*0112#1", SmsCommand.of(d))
    }

    @Test
    fun `蓝复式走同一形状`() {
        val d = draft(reds = setOf(1, 2, 3, 4, 5, 6), blues = setOf(1, 12))
        assertEquals("00CP#01#010203040506*0112#1", SmsCommand.of(d))
    }

    @Test
    fun `红胆拖与官方示例一致_多蓝连写`() {
        val single = draft(redBold = setOf(1, 2, 3, 4, 5), reds = setOf(6, 7, 8), blues = setOf(1))
        assertEquals("00CP#01#0102030405*060708*01#1", SmsCommand.of(single))

        val multiBlue = draft(redBold = setOf(1, 2, 3, 4, 5), reds = setOf(6, 7, 8), blues = setOf(1, 2))
        assertEquals("00CP#01#0102030405*060708*0102#1", SmsCommand.of(multiBlue))
    }

    @Test
    fun `单式多注与官方示例一致`() {
        val a = draft(reds = setOf(1, 2, 3, 4, 5, 6), blues = setOf(1))
        val b = draft(reds = setOf(7, 8, 9, 10, 11, 12), blues = setOf(10))
        assertEquals(
            "00CP#01#010203040506*01**070809101112*10#1",
            SmsCommand.multi(listOf(a, b)),
        )
    }

    @Test
    fun `多注限制_最多五注_仅单式`() {
        val simple = draft(reds = setOf(1, 2, 3, 4, 5, 6), blues = setOf(1))
        assertEquals(5, SmsCommand.MAX_MULTI_BETS)
        assertNull("超过 5 注应拒绝", SmsCommand.multi(List(6) { simple }))
        val compound = draft(reds = setOf(1, 2, 3, 4, 5, 6, 7), blues = setOf(1))
        assertNull("非单式应拒绝", SmsCommand.multi(listOf(simple, compound)))
        assertNull("空列表应拒绝", SmsCommand.multi(emptyList()))
    }

    @Test
    fun `蓝胆拖与非法草稿不支持`() {
        val blueBold = TicketDraft(
            redPicked = setOf(1, 2, 3, 4, 5, 6),
            blueBold = setOf(5),
            bluePicked = setOf(7, 8),
        )
        assertNull("双色球没有蓝胆拖，不应生成指令", SmsCommand.of(blueBold))
        assertNull(SmsCommand.of(TicketDraft()))
    }
}
