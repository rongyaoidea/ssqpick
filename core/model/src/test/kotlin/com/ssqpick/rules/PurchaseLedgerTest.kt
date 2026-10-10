package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseLedgerTest {

    private fun draw(code: String, date: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = red,
        blue = blue,
    )

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private val winDraw = draw("2026002", "2026-01-04", listOf(1, 2, 3, 4, 5, 6), 7)
    private val missDraw = draw("2026003", "2026-01-06", listOf(10, 11, 12, 13, 14, 15), 8)
    private val drawsAsc = listOf(winDraw, missDraw).sortedBy { it.date }

    private val winTicket = Ticket(
        id = 1L,
        createdAtEpochMs = ms("2026-01-01T00:00:00Z"),
        draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
    )

    @Test
    fun `绑定到购买日之后的第一期`() {
        // 1月3日买 → 绑 1月4日那期
        val p = Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2)
        assertEquals("2026002", bindDraw(p, drawsAsc)?.code)
        // 1月5日买 → 绑 1月6日那期（跳过已开的 1月4日）
        val p2 = Purchase(id = 2, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2)
        assertEquals("2026003", bindDraw(p2, drawsAsc)?.code)
    }

    @Test
    fun `还没开奖是pending`() {
        val p = Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-10T00:00:00Z"), costYuan = 2)
        val o = outcomeOf(p, winTicket, drawsAsc)
        assertTrue(o.isPending)
        assertNull(o.topTier)
        assertEquals(0L, o.amountYuan)
    }

    @Test
    fun `中奖与未中奖`() {
        val won = outcomeOf(
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            winTicket,
            drawsAsc,
        )
        assertTrue(won.hasWon)
        assertEquals(PrizeTier.FIRST, won.topTier)

        val missed = outcomeOf(
            Purchase(id = 2, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
            winTicket,
            drawsAsc,
        )
        assertTrue(!missed.isPending && !missed.hasWon)
    }

    @Test
    fun `票级汇总_含待开奖与最近一笔`() {
        val purchases = listOf(
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            Purchase(id = 2, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
            Purchase(id = 3, ticketId = 1, purchasedAtEpochMs = ms("2026-01-10T10:00:00Z"), costYuan = 2),
        )
        val s = summaryOf(winTicket, purchases, drawsAsc)
        assertEquals(3, s.count)
        assertEquals(1, s.pendingCount)
        assertEquals(6L, s.costYuan)
        // 一等奖是浮动奖且该期无公告实付额：奖级在、金额为 0
        assertEquals(0L, s.prizeYuan)
        // 最近一笔（1月10日）还没开奖：无奖级
        assertTrue(s.latest!!.isPending)
        assertNull(s.latest?.topTier)
        assertTrue(s.latest!!.isPending) // 最近一笔（1月10日）还没开奖
    }

    @Test
    fun `账本累计净收益时间线`() {
        val purchases = listOf(
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
            Purchase(id = 2, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
        )
        val ledger = ledgerOf(purchases, mapOf(1L to winTicket), drawsAsc)
        assertEquals(2, ledger.count)
        assertEquals(0, ledger.pendingCount)
        assertEquals(4L, ledger.totalCostYuan)
        assertEquals(2, ledger.cumulativeNet.size)
        // 无公告时一等奖金额为 0：两笔都只是花钱
        assertEquals(-2f, ledger.cumulativeNet[0])
        assertEquals(-4f, ledger.cumulativeNet[1])
        assertEquals(-4L, ledger.netYuan)
        // 第一笔中一等奖（金额 0），第二笔未中：打点在第一笔
        assertEquals(setOf(0), ledger.winIndices)
        assertEquals(mapOf(PrizeTier.FIRST to 1L), ledger.tierCounts)
        assertEquals(mapOf(PrizeTier.FIRST to 0L), ledger.tierAmounts)
    }

    @Test
    fun `账本奖级聚合三级固定奖`() {
        // 红 1-5+8、蓝 7：对开奖 1-6/7 中 5 红 + 蓝 = 三等奖 3000
        val ticket = Ticket(
            id = 1L,
            createdAtEpochMs = ms("2026-01-01T00:00:00Z"),
            draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 8), bluePicked = setOf(7)),
        )
        val purchases = listOf(
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
        )
        val ledger = ledgerOf(purchases, mapOf(1L to ticket), drawsAsc)
        assertEquals(3000L, ledger.totalPrizeYuan)
        assertEquals(mapOf(PrizeTier.THIRD to 1L), ledger.tierCounts)
        assertEquals(mapOf(PrizeTier.THIRD to 3000L), ledger.tierAmounts)
        assertEquals(setOf(0), ledger.winIndices)
        assertEquals(2998f, ledger.cumulativeNet[0])
    }

    @Test
    fun `票被删后孤儿购买只计成本`() {
        val purchases = listOf(
            Purchase(id = 1, ticketId = 99, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
        )
        val ledger = ledgerOf(purchases, emptyMap(), drawsAsc)
        assertEquals(2L, ledger.totalCostYuan)
        assertEquals(0L, ledger.totalPrizeYuan)
    }
}
