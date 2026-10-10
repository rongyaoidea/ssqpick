package com.ssqpick.feature.ticket

import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.PurchaseOutcome
import com.ssqpick.rules.outcomeOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarMarksTest {

    private fun draw(code: String, date: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = red,
        blue = blue,
    )

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    /** 按票构造购买结算表（等价于 ViewModel 的共享结算流）。 */
    private fun outcomesOf(purchases: List<Purchase>, tickets: Map<Long, Ticket>): Map<Long, PurchaseOutcome> = purchases.mapNotNull { p ->
        tickets[p.ticketId]?.let { t -> p.id to outcomeOf(p, t, drawsAsc) }
    }.toMap()

    private val winDraw = draw("2026002", "2026-01-04", listOf(1, 2, 3, 4, 5, 6), 7)
    private val missDraw = draw("2026003", "2026-01-06", listOf(10, 11, 12, 13, 14, 15), 8)
    private val drawsAsc = listOf(winDraw, missDraw).sortedBy { it.date }

    // 对 winDraw 中 5 红 + 蓝 = 三等奖；对 missDraw 什么都不中
    private val winTicket = Ticket(
        id = 1L,
        createdAtEpochMs = ms("2026-01-01T00:00:00Z"),
        draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 8), bluePicked = setOf(7)),
    )
    private val missTicket = Ticket(
        id = 2L,
        createdAtEpochMs = ms("2026-01-01T00:00:00Z"),
        draft = TicketDraft(redPicked = setOf(20, 21, 22, 23, 24, 25), bluePicked = setOf(9)),
    )

    @Test
    fun `按购买日期打标_同天取最强信号`() {
        val purchases = listOf(
            // 1月3日买中奖票 → WIN；同天再买一张未中的 → 仍是 WIN
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            Purchase(id = 2, ticketId = 2, purchasedAtEpochMs = ms("2026-01-03T11:00:00Z"), costYuan = 2),
            // 1月5日买未中票 → 绑 1月6日那期 → MISS
            Purchase(id = 3, ticketId = 2, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
            // 1月10日买 → 还没来得及开奖 → PENDING
            Purchase(id = 4, ticketId = 1, purchasedAtEpochMs = ms("2026-01-10T10:00:00Z"), costYuan = 2),
        )
        val marks = dayMarksOf(purchases, outcomesOf(purchases, mapOf(1L to winTicket, 2L to missTicket)))
        assertEquals(DayMark.WIN, marks[LocalDate.parse("2026-01-03")])
        assertEquals(DayMark.MISS, marks[LocalDate.parse("2026-01-05")])
        assertEquals(DayMark.PENDING, marks[LocalDate.parse("2026-01-10")])
        assertEquals(3, marks.size)
    }

    @Test
    fun `孤儿购买不进日历`() {
        val purchases = listOf(
            Purchase(id = 1, ticketId = 99, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
        )
        assertEquals(emptyMap<LocalDate, DayMark>(), dayMarksOf(purchases, outcomesOf(purchases, emptyMap())))
    }
}
