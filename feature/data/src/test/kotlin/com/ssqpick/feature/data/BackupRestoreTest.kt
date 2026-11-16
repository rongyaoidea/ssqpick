package com.ssqpick.feature.data

import com.ssqpick.model.BackupBundle
import com.ssqpick.model.BackupCodec
import com.ssqpick.model.BackupPurchase
import com.ssqpick.model.BackupTicket
import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.PurchaseRepository
import com.ssqpick.store.SettingsStorage
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeDraws : DrawRepository {
        override fun observeAll(): Flow<List<Draw>> = MutableStateFlow(emptyList())
        override fun observeNewest(): Flow<Draw?> = MutableStateFlow(null)
        override suspend fun all(): List<Draw> = emptyList()
        override suspend fun count(): Long = 0
        override suspend fun insertAll(draws: List<Draw>) = Unit
        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private class FakeSettings : SettingsStorage {
        override var drawResultNotify: Boolean = false
        override var onboardingDismissed: Boolean = true
    }

    private class FakeTickets : TicketRepository {
        val items = MutableStateFlow<List<Ticket>>(emptyList())
        private var nextId = 1L
        override fun observeAll(): Flow<List<Ticket>> = items
        override suspend fun all(): List<Ticket> = items.value
        override suspend fun save(draft: TicketDraft, label: String?): Long = import(Ticket(createdAtEpochMs = 1_700_000_000_000L, label = label, draft = draft))

        override suspend fun rename(id: Long, label: String?) = Unit
        override suspend fun delete(id: Long) {
            items.value = items.value.filterNot { it.id == id }
        }

        override suspend fun import(ticket: Ticket): Long {
            val id = nextId++
            items.value = items.value + ticket.copy(id = id)
            return id
        }
    }

    private class FakePurchases : PurchaseRepository {
        val items = MutableStateFlow<List<Purchase>>(emptyList())
        private var nextId = 1L
        override fun observeAll(): Flow<List<Purchase>> = items
        override suspend fun all(): List<Purchase> = items.value
        override suspend fun unsettled(): List<Purchase> = items.value.filterNot { it.settled }
        override suspend fun record(ticketId: Long, costYuan: Long) = import(ticketId, 1_700_000_100_000L, costYuan)

        override suspend fun import(
            ticketId: Long,
            purchasedAtEpochMs: Long,
            costYuan: Long,
            settled: Boolean,
        ) {
            items.value = items.value + Purchase(
                id = nextId++,
                ticketId = ticketId,
                purchasedAtEpochMs = purchasedAtEpochMs,
                costYuan = costYuan,
                settled = settled,
            )
        }

        override suspend fun settle(ids: List<Long>) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private fun vm(tickets: FakeTickets, purchases: FakePurchases) = DataViewModel(
        drawRepository = FakeDraws(),
        settingsStore = FakeSettings(),
        ticketRepository = tickets,
        purchaseRepository = purchases,
        computeDispatcher = dispatcher,
    )

    @Test
    fun 导出再恢复会保留时间且重复恢复不重复插入() = runTest {
        val sourceTickets = FakeTickets()
        val sourcePurchases = FakePurchases()
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        val id = sourceTickets.import(Ticket(createdAtEpochMs = 1_700_000_000_000L, label = "守号", draft = draft))
        sourcePurchases.import(id, purchasedAtEpochMs = 1_700_000_500_000L, costYuan = 2, settled = true)

        val json = vm(sourceTickets, sourcePurchases).buildBackupJson()

        val targetTickets = FakeTickets()
        val targetPurchases = FakePurchases()
        val target = vm(targetTickets, targetPurchases)
        val first = target.restoreBackupJson(json)
        assertTrue(first, first.contains("新增 1 张号码"))
        assertTrue(first, first.contains("1 笔购买"))
        assertEquals(1, targetTickets.items.value.size)
        assertEquals(1_700_000_000_000L, targetTickets.items.value.single().createdAtEpochMs)
        assertEquals("守号", targetTickets.items.value.single().label)
        assertEquals(1_700_000_500_000L, targetPurchases.items.value.single().purchasedAtEpochMs)
        assertEquals(targetTickets.items.value.single().id, targetPurchases.items.value.single().ticketId)
        assertTrue(targetPurchases.items.value.single().settled)

        val second = target.restoreBackupJson(json)
        assertTrue(second, second.contains("跳过 1 张重复号码"))
        assertTrue(second, second.contains("1 笔重复购买"))
        assertEquals(1, targetTickets.items.value.size)
        assertEquals(1, targetPurchases.items.value.size)
    }

    @Test
    fun 坏文件返回可读错误而不是崩溃() = runTest {
        val result = vm(FakeTickets(), FakePurchases()).restoreBackupJson("{oops")
        assertTrue(result, result.startsWith("恢复失败"))
    }

    @Test
    fun 备份里的购买指向不存在的号码会被拒绝() = runTest {
        val json = BackupCodec.encode(
            BackupBundle(
                exportedAtEpochMs = 1L,
                tickets = listOf(
                    BackupTicket(
                        createdAtEpochMs = 1_700_000_000_000L,
                        redPicked = listOf(1, 2, 3, 4, 5, 6),
                        bluePicked = listOf(7),
                    ),
                ),
                purchases = listOf(BackupPurchase(ticketIndex = 3, purchasedAtEpochMs = 1L, costYuan = 2)),
            ),
        )
        val result = vm(FakeTickets(), FakePurchases()).restoreBackupJson(json)
        assertTrue(result, result.startsWith("恢复失败"))
    }
}
