package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/** 复盘页的四个汇总：月度账单、号码覆盖、守号对照、日历明细。TZ 钉在 UTC。 */
class ReviewTest {

    private var originalTz: TimeZone? = null

    @Before
    fun pinUtc() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTz() {
        originalTz?.let { TimeZone.setDefault(it) }
    }

    private fun draw(code: String, date: String) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = listOf(1, 2, 3, 4, 5, 6),
        blue = 7,
    )

    private val win = draw("2026002", "2026-01-04")
    private val miss = draw("2026003", "2026-01-06")
    private val asc = listOf(win, miss)

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private val winTicket = Ticket(
        id = 1L,
        createdAtEpochMs = ms("2026-01-01T00:00:00Z"),
        draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
    )
    private val missTicket = Ticket(
        id = 2L,
        createdAtEpochMs = ms("2026-01-01T00:00:00Z"),
        draft = TicketDraft(redPicked = setOf(11, 12, 13, 14, 15, 16), bluePicked = setOf(8)),
    )

    private val tickets = mapOf(1L to winTicket, 2L to missTicket)

    @Test
    fun `月度账单按月汇总_时间倒序`() {
        val ps = listOf(
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            Purchase(id = 2, ticketId = 2, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
            Purchase(id = 3, ticketId = 2, purchasedAtEpochMs = ms("2026-02-01T10:00:00Z"), costYuan = 4),
        )
        val bill = monthlyLedger(ps, tickets, asc)
        assertEquals(2, bill.size)
        assertEquals(2026, bill[0].year)
        assertEquals(2, bill[0].month)
        assertEquals(1, bill[0].count)
        assertEquals(4L, bill[0].costYuan)
        assertEquals(2026, bill[1].year)
        assertEquals(1, bill[1].month)
        assertEquals(2, bill[1].count)
        assertEquals(4L, bill[1].costYuan)
        // 一等奖无公告实付额 → 奖金 0，净收益 = -投入
        assertEquals(-4L, bill[1].netYuan)
    }

    @Test
    fun `号码覆盖计数_以及从没买过的号`() {
        val cov = numberCoverage(listOf(winTicket, missTicket))
        assertEquals(2, cov.ticketCount)
        assertEquals(1, cov.redCounts[1])
        assertEquals(1, cov.redCounts[16])
        assertEquals(1, cov.blueCounts[7])
        assertEquals(1, cov.maxRed)
        assertTrue("33 从没买过", 33 in cov.neverRed)
        assertTrue("1 买过", 1 !in cov.neverRed)
        assertTrue("蓝 3 从没买过", 3 in cov.neverBlue)
        assertTrue("蓝 7 买过", 7 !in cov.neverBlue)
    }

    @Test
    fun `守号与换号分桶`() {
        val ps = listOf(
            // 票 1 买两笔 → 守号
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            Purchase(id = 2, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
            // 票 2 只买一笔 → 换号
            Purchase(id = 3, ticketId = 2, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
        )
        val r = holdSwitchReport(ps, tickets, asc)
        assertEquals(1, r.hold.tickets)
        assertEquals(2, r.hold.bets)
        assertEquals(4L, r.hold.costYuan)
        assertEquals(1, r.switch.tickets)
        assertEquals(1, r.switch.bets)
        assertEquals(2L, r.switch.costYuan)
    }

    @Test
    fun `日历当天明细_含未开奖`() {
        val ps = listOf(
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            Purchase(id = 2, ticketId = 1, purchasedAtEpochMs = ms("2026-01-10T10:00:00Z"), costYuan = 2),
        )
        val map = dayEntriesOf(ps, tickets, asc)
        val day1 = map[LocalDate.parse("2026-01-03")]!!
        assertEquals(1, day1.size)
        assertEquals("2026002", day1[0].drawCode)
        assertEquals(PrizeTier.FIRST.label, day1[0].tierLabel)
        assertTrue(!day1[0].pending)

        val day2 = map[LocalDate.parse("2026-01-10")]!!
        assertTrue(day2[0].pending)
        assertNull(day2[0].drawCode)
    }
}
