package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketStatusTest {

    private fun draw() = Draw(
        code = "2026001",
        date = LocalDate(2026, 1, 1),
        red = listOf(1, 2, 3, 4, 5, 6),
        blue = 7,
    )

    private fun ticket(draft: TicketDraft, createdAt: String) = Ticket(
        id = 1L,
        createdAtEpochMs = Instant.parse(createdAt).toEpochMilliseconds(),
        draft = draft,
    )

    @Test
    fun `没有开奖就是未开奖`() {
        val t = ticket(
            TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
            "2026-01-02T12:00:00Z",
        )
        assertEquals(TicketStatus.NotDrawn, TicketRules.statusOf(t, null))
    }

    @Test
    fun `最新一期之后存的算未开奖`() {
        val t = ticket(
            TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
            "2026-01-02T12:00:00Z",
        )
        assertEquals(TicketStatus.NotDrawn, TicketRules.statusOf(t, draw()))
    }

    @Test
    fun `全中是一等奖_无公告时金额为零但奖级照算`() {
        val t = ticket(
            TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
            "2025-12-31T12:00:00Z",
        )
        val s = TicketRules.statusOf(t, draw())
        assertTrue(s is TicketStatus.Won)
        assertEquals(PrizeTier.FIRST, (s as TicketStatus.Won).topTier)
    }

    @Test
    fun `三等奖金额取固定奖`() {
        // 中 5 红 + 蓝 = 三等奖 3000（无公告时固定奖）
        val t = ticket(
            TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 8), bluePicked = setOf(7)),
            "2025-12-31T12:00:00Z",
        )
        val s = TicketRules.statusOf(t, draw())
        assertTrue(s is TicketStatus.Won)
        s as TicketStatus.Won
        assertEquals(PrizeTier.THIRD, s.topTier)
        assertEquals(PrizeTier.THIRD.fixedAmountYuan, s.amountYuan)
    }

    @Test
    fun `不中就是未中奖`() {
        val t = ticket(
            TicketDraft(redPicked = setOf(11, 12, 13, 14, 15, 16), bluePicked = setOf(8)),
            "2025-12-31T12:00:00Z",
        )
        assertEquals(TicketStatus.Missed, TicketRules.statusOf(t, draw()))
    }
}
