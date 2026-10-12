package com.ssqpick.feature.ticket

import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.PurchaseRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 待开奖列表的口径：**绑定的那期还没开**才算待开奖。
 *
 * 不看 DB 的 settled 标记（只有后台 Worker 会写它）——手动"同步并对奖"看到
 * 结果之后，这笔不能再挂在"待开奖"上，否则和账本、日历明细互相矛盾。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketPendingTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Tickets(private val rows: List<Ticket>) : TicketRepository {
        override fun observeAll(): Flow<List<Ticket>> = flowOf(rows)
        override suspend fun all(): List<Ticket> = rows
        override suspend fun save(draft: TicketDraft, label: String?): Long = 1L
        override suspend fun rename(id: Long, label: String?) {}
        override suspend fun delete(id: Long) {}
    }

    private class Purchases(private val rows: List<Purchase>) : PurchaseRepository {
        override fun observeAll(): Flow<List<Purchase>> = MutableStateFlow(rows)
        override suspend fun unsettled(): List<Purchase> = rows.filter { !it.settled }
        override suspend fun record(ticketId: Long, costYuan: Long) {}
        override suspend fun settle(ids: List<Long>) {}
        override suspend fun delete(id: Long) {}
    }

    private class Draws(private val rows: List<Draw>) : DrawRepository {
        override fun observeAll(): Flow<List<Draw>> = MutableStateFlow(rows)
        override fun observeNewest(): Flow<Draw?> = flowOf(rows.maxByOrNull { it.date })
        override suspend fun all(): List<Draw> = rows
        override suspend fun count(): Long = rows.size.toLong()
        override suspend fun insertAll(draws: List<Draw>) {}
        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private class Drafts : DraftStorage {
        override fun load(): TicketDraft = TicketDraft()
        override fun save(draft: TicketDraft) {}
    }

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private val ticket = Ticket(
        id = 1L,
        createdAtEpochMs = ms("2026-01-03T10:00:00Z"),
        draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
    )

    private fun vm(purchases: List<Purchase>, draws: List<Draw>) = TicketViewModel(
        ticketRepository = Tickets(listOf(ticket)),
        drawRepository = Draws(draws),
        purchaseRepository = Purchases(purchases),
        draftStorage = Drafts(),
        computeDispatcher = dispatcher,
    )

    @Test
    fun `绑定期还没开_算待开奖`() = runTest {
        // 1 月 5 日买 → 绑下一期，而库里还没有那一期
        val p = Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2)
        val model = vm(listOf(p), emptyList())
        backgroundScope.launch { model.pending.collect { } }
        advanceUntilIdle()
        assertEquals(1, model.pending.value.size)
        assertEquals(1L, model.pending.value.first().ticketId)
    }

    @Test
    fun `下一期覆盖只统计等开奖的票`() = runTest {
        val pending = Ticket(
            id = 1L,
            createdAtEpochMs = ms("2026-01-03T10:00:00Z"),
            draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
        )
        val drawn = Ticket(
            id = 2L,
            createdAtEpochMs = ms("2026-01-03T10:00:00Z"),
            draft = TicketDraft(redPicked = setOf(10, 11, 12, 13, 14, 15), bluePicked = setOf(8)),
        )
        // 3 号票只存没买：绑定"保存后的第一期"，库里还没有 → 也算等开奖
        val savedOnly = Ticket(
            id = 3L,
            createdAtEpochMs = ms("2026-01-06T10:00:00Z"),
            draft = TicketDraft(redPicked = setOf(20, 21, 22, 23, 24, 25), bluePicked = setOf(9)),
        )
        val purchases = listOf(
            // 1 月 5 日买 → 绑下一期（库里没有）
            Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-05T10:00:00Z"), costYuan = 2),
            // 1 月 3 日买 → 绑 1 月 4 日那期（库里已有）→ 已开奖，不计入
            Purchase(id = 2, ticketId = 2, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2),
        )
        val draw = Draw("2026002", LocalDate.parse("2026-01-04"), listOf(1, 2, 3, 4, 5, 6), 7)
        val model = TicketViewModel(
            ticketRepository = Tickets(listOf(pending, drawn, savedOnly)),
            drawRepository = Draws(listOf(draw)),
            purchaseRepository = Purchases(purchases),
            draftStorage = Drafts(),
            computeDispatcher = dispatcher,
        )
        backgroundScope.launch { model.nextDrawCoverage.collect { } }
        advanceUntilIdle()

        val cov = model.nextDrawCoverage.value!!
        assertEquals(2, cov.ticketCount)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 20, 21, 22, 23, 24, 25), cov.redCounts.keys)
        assertEquals(setOf(7, 9), cov.blueCounts.keys)
        // 已开奖的 2 号票（10-15 / 8）不进下一期覆盖
        assertTrue(10 !in cov.redCounts.keys)
        assertEquals(21, cov.neverRed.size)
    }

    @Test
    fun `没有等开奖的票时下一期覆盖为 null`() = runTest {
        val p = Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2)
        val draw = Draw(
            code = "2026002",
            date = LocalDate.parse("2026-01-04"),
            red = listOf(1, 2, 3, 4, 5, 6),
            blue = 7,
        )
        val model = vm(listOf(p), listOf(draw))
        backgroundScope.launch { model.nextDrawCoverage.collect { } }
        advanceUntilIdle()
        assertEquals(null, model.nextDrawCoverage.value)
    }

    @Test
    fun `绑定期已开_即使 settled 还是 0 也不算待开奖`() = runTest {
        // 1 月 3 日买 → 绑 1 月 4 日那期，库里已有 → 已开奖，不该再列为待开奖
        val p = Purchase(id = 1, ticketId = 1, purchasedAtEpochMs = ms("2026-01-03T10:00:00Z"), costYuan = 2)
        val draw = Draw(
            code = "2026002",
            date = LocalDate.parse("2026-01-04"),
            red = listOf(1, 2, 3, 4, 5, 6),
            blue = 7,
        )
        val model = vm(listOf(p), listOf(draw))
        backgroundScope.launch { model.pending.collect { } }
        advanceUntilIdle()
        assertEquals(0, model.pending.value.size)
    }
}
