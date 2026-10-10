package com.ssqpick.feature.ticket

import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.model.TicketKind
import com.ssqpick.rules.TicketRules
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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
        override suspend fun clear() {}
    }

    private class FakeDrawRepo : DrawRepository {
        override fun observeAll(): Flow<List<Draw>> = flowOf(emptyList())
        override fun observeNewest(): Flow<Draw?> = flowOf(null)
        override suspend fun all(): List<Draw> = emptyList()
        override suspend fun count(): Long = 0L
        override suspend fun insertAll(draws: List<Draw>) {}
        override suspend fun clear() {}
        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private fun vm() = TicketViewModel(FakeTicketRepo(), FakeDrawRepo())

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
}
