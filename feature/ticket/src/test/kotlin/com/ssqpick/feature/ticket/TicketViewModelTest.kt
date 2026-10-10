package com.ssqpick.feature.ticket

import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.model.TicketKind
import com.ssqpick.rules.TicketRules
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 胆拖点选逻辑：胆与拖互斥、模式切换不丢号。
 *
 * 只测同步的 toggle 路径（不碰 save/sync 那些走 viewModelScope 的），
 * 但构造 ViewModel 本身就会踩 viewModelScope，所以还是要 setMain。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTicketRepo : TicketRepository {
        override fun observeAll(): Flow<List<Ticket>> = flowOf(emptyList())
        override suspend fun all(): List<Ticket> = emptyList()
        override suspend fun save(draft: TicketDraft, label: String?): Long = 1L
        override suspend fun rename(id: Long, label: String?) {}
        override suspend fun delete(id: Long) {}
    }

    private class FakeDrawRepo : DrawRepository {
        override fun observeAll(): Flow<List<Draw>> = flowOf(emptyList())
        override fun observeNewest(): Flow<Draw?> = flowOf(null)
        override suspend fun all(): List<Draw> = emptyList()
        override suspend fun count(): Long = 0L
        override suspend fun insertAll(draws: List<Draw>) {}
        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private class FakePurchaseRepo : PurchaseRepository {
        private val state = MutableStateFlow<List<Purchase>>(emptyList())
        private var nextId = 1L
        val rows: List<Purchase> get() = state.value

        override fun observeAll(): Flow<List<Purchase>> = state
        override suspend fun unsettled(): List<Purchase> = state.value.filter { !it.settled }
        override suspend fun record(ticketId: Long, costYuan: Long) {
            state.value += Purchase(id = nextId++, ticketId = ticketId, purchasedAtEpochMs = 0L, costYuan = costYuan)
        }

        override suspend fun settle(ids: List<Long>) {}
        override suspend fun delete(id: Long) {
            state.value = state.value.filter { it.id != id }
        }
    }

    private fun vm(
        purchaseRepo: FakePurchaseRepo = FakePurchaseRepo(),
    ) = TicketViewModel(FakeTicketRepo(), FakeDrawRepo(), purchaseRepo)

    @Test
    fun `新号码进胆码不进拖码`() {
        val vm = vm()
        vm.toggleRedBold(5)
        val d = vm.ui.value.draft
        assertEquals(setOf(5), d.redBold)
        assertTrue(d.redPicked.isEmpty())
        assertTrue(TicketRules.validate(d).let { !it.isValid }) // 胆1拖0还不合法，但不能是重叠错
        assertTrue(TicketRules.validate(d).error != TicketRules.ValidationError.RED_BOLD_OVERLAP)
    }

    @Test
    fun `胆码点两次取消`() {
        val vm = vm()
        vm.toggleRedBold(5)
        vm.toggleRedBold(5)
        assertTrue(vm.ui.value.draft.redBold.isEmpty())
    }

    @Test
    fun `拖码模式点胆码球降级为拖码不丢号`() {
        val vm = vm()
        vm.setBoldMode(true)
        vm.toggleRed(7)
        assertEquals(setOf(7), vm.ui.value.draft.redBold)
        vm.setBoldMode(false)
        vm.toggleRed(7)
        val d = vm.ui.value.draft
        assertTrue(7 !in d.redBold)
        assertEquals(setOf(7), d.redPicked)
    }

    @Test
    fun `蓝胆点别的号码是替换`() {
        val vm = vm()
        vm.toggleBlueBold(3)
        vm.toggleBlueBold(5)
        assertEquals(setOf(5), vm.ui.value.draft.blueBold)
        vm.toggleBlueBold(5)
        assertTrue(vm.ui.value.draft.blueBold.isEmpty())
    }

    @Test
    fun `走一遍红胆拖合法且注数正确`() {
        val vm = vm()
        vm.setBoldMode(true)
        vm.toggleRed(1)
        vm.setBoldMode(false)
        (2..8).forEach { vm.toggleRed(it) }
        vm.toggleBlue(9)
        val ui = vm.ui.value
        assertTrue(ui.isValid)
        assertEquals(TicketKind.RED_DAN_TUO, ui.draft.kind)
        // 胆1 + 拖7 = C(7,5) = 21
        assertEquals(21L, ui.draft.betCount)
    }

    @Test
    fun `机选两次不累计`() {
        val vm = vm()
        vm.randomFill()
        assertEquals(6, vm.ui.value.draft.redPicked.size)
        assertEquals(1, vm.ui.value.draft.bluePicked.size)
        vm.randomFill()
        assertEquals(6, vm.ui.value.draft.redPicked.size)
        assertEquals(1, vm.ui.value.draft.bluePicked.size)
        assertTrue(vm.ui.value.isValid)
    }

    @Test
    fun `机选保留手选只补缺`() {
        val vm = vm()
        vm.toggleRed(1)
        vm.toggleRed(2)
        vm.toggleRed(3)
        vm.randomFill()
        val d = vm.ui.value.draft
        assertTrue(d.redPicked.containsAll(setOf(1, 2, 3)))
        assertEquals(6, d.redPicked.size)
        assertEquals(1, d.bluePicked.size)
        assertTrue(vm.ui.value.isValid)
    }

    @Test
    fun `手选凑满后机选不动`() {
        val vm = vm()
        (1..6).forEach { vm.toggleRed(it) }
        vm.toggleBlue(1)
        val before = vm.ui.value.draft
        vm.randomFill()
        assertEquals(before, vm.ui.value.draft)
    }

    @Test
    fun `手碰过机选号就整组归手选`() {
        val vm = vm()
        vm.randomFill()
        val before = vm.ui.value.draft.redPicked
        assertEquals(6, before.size)
        val victim = before.first()
        vm.toggleRed(victim) // 亲手取消一个：剩下 5 个转手选
        val kept = vm.ui.value.draft.redPicked
        assertEquals(5, kept.size)
        vm.randomFill() // 只补 1 个新的，之前留下的都在
        val d = vm.ui.value.draft
        assertEquals(6, d.redPicked.size)
        assertTrue(d.redPicked.containsAll(kept))
    }

    @Test
    fun `记录购买两笔后撤销最近一笔`() = runTest {
        val purchases = FakePurchaseRepo()
        val vm = vm(purchaseRepo = purchases)
        // purchases 是 WhileSubscribed 的 stateIn：测试里得先有人订阅才会收集
        backgroundScope.launch { vm.purchases.collect { } }
        advanceUntilIdle()

        val ticket = Ticket(
            id = 1L,
            createdAtEpochMs = 0L,
            draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7)),
        )
        vm.recordPurchase(ticket)
        advanceUntilIdle()
        assertEquals(1, purchases.rows.size)
        assertEquals(2L, purchases.rows.first().costYuan)

        vm.recordPurchase(ticket)
        advanceUntilIdle()
        assertEquals(2, purchases.rows.size)

        vm.undoLastPurchase(ticket)
        advanceUntilIdle()
        assertEquals(1, purchases.rows.size)
        assertTrue(vm.ui.value.actionMessage?.contains("撤销") == true)
    }
}
