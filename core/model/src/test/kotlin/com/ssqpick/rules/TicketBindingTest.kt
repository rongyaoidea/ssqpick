package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * 选号与期号的绑定：每注号码对应"保存时刻之后的第一期开奖"，
 * 对奖只核对同一期的票。
 *
 * 测试假定 TZ=UTC（与 CI 一致）：绑定口径按本地时区的 21:15 截止。
 */
class TicketBindingTest {

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

    // 2026-10-04 周日、10-06 周二、10-08 周四（都是开奖日）
    private fun draw(code: String, date: String) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = listOf(1, 2, 3, 4, 5, 6),
        blue = 7,
    )

    private val d1 = draw("2026114", "2026-10-04")
    private val d2 = draw("2026115", "2026-10-06")
    private val d3 = draw("2026116", "2026-10-08")
    private val asc = listOf(d1, d2, d3)

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private fun ticket(createdIso: String, id: Long = 1L) = Ticket(
        id = id,
        createdAtEpochMs = ms(createdIso),
        draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
    )

    @Test
    fun `周一保存的票绑周二那期`() {
        assertEquals("2026115", bindTicketDraw(ticket("2026-10-05T10:00:00Z"), asc)?.code)
    }

    @Test
    fun `开奖当晚21点15后保存算下一期`() {
        assertEquals("2026116", bindTicketDraw(ticket("2026-10-06T22:00:00Z"), asc)?.code)
        assertEquals("2026115", bindTicketDraw(ticket("2026-10-06T20:00:00Z"), asc)?.code)
    }

    @Test
    fun `还没开奖时没有绑定_文案给预计开奖日`() {
        val t = ticket("2026-10-09T10:00:00Z") // 周五保存，下期是周日 10-11
        assertNull(bindTicketDraw(t, asc))
        assertEquals("10月11日开奖", targetLabel(t.createdAtEpochMs, asc))
    }

    @Test
    fun `对奖只挑本期的票`() {
        val mine = ticket("2026-10-05T10:00:00Z", id = 1) // 绑 2026115
        val next = ticket("2026-10-07T10:00:00Z", id = 2) // 绑 2026116
        val old = ticket("2026-10-03T10:00:00Z", id = 3) // 绑 2026114
        val (forTarget, others) = ticketsForDraw(listOf(mine, next, old), d2, asc)
        assertEquals(listOf(1L), forTarget.map { it.id })
        assertEquals(listOf(2L, 3L), others.map { it.id })
    }

    @Test
    fun `期号文案优先用真实期号`() {
        assertEquals("第 2026115 期", targetLabel(ms("2026-10-05T10:00:00Z"), asc))
    }
}
