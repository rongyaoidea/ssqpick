package com.ssqpick.feature.machine

import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * 逐注保存：展开、勾选、存单式。
 *
 * ViewModel 的 init 就在 viewModelScope 里收状态机，所以必须 setMain；
 * 摇号推进本身是同步的，直接调 controller.advance 即可。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MachinePickerTest {

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
        val saved = mutableListOf<TicketDraft>()
        override fun observeAll(): Flow<List<Ticket>> = flowOf(emptyList())
        override suspend fun all(): List<Ticket> = emptyList()
        override suspend fun save(draft: TicketDraft, label: String?): Long {
            saved += draft
            return saved.size.toLong()
        }
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

    private fun vm(repo: FakeTicketRepo) = MachineViewModel(repo, TicketHistoryProvider(FakeDrawRepo()))

    /** 同步把状态机摇到 COMPLETE（再跑一次上面的回归）。 */
    private fun driveToComplete(vm: MachineViewModel, redCount: Int, blueCount: Int) {
        vm.controller.start(redCount, blueCount)
        var frames = 0
        while (vm.controller.state.value.phase != MachineUiState.Phase.COMPLETE && frames < 14_400) {
            vm.controller.advance(1 / 60f)
            frames++
        }
        assertEquals(MachineUiState.Phase.COMPLETE, vm.controller.state.value.phase)
    }

    @Test
    fun `单式逐注保存一注单式票`() = runTest {
        val repo = FakeTicketRepo()
        val vm = vm(repo)
        driveToComplete(vm, 6, 1)
        advanceUntilIdle() // 冲掉 init 的收集与历史刷新，_ui 才会到 COMPLETE
        assertTrue(vm.ui.value.machine.isComplete)

        vm.togglePicker()
        assertEquals(1, vm.ui.value.pickedBets.size)
        assertEquals(1L, vm.ui.value.totalBets)

        val bet = vm.ui.value.pickedBets.first()
        vm.toggleBet(bet)
        assertEquals(setOf(bet), vm.ui.value.selectedBets)
        vm.toggleBet(bet)
        assertTrue(vm.ui.value.selectedBets.isEmpty())
        vm.toggleBet(bet)

        vm.saveSelected()
        advanceUntilIdle()

        assertEquals(1, repo.saved.size)
        assertEquals(6, repo.saved.first().redPicked.size)
        assertEquals(1, repo.saved.first().bluePicked.size)
        assertTrue(vm.ui.value.saved)
        assertTrue(vm.ui.value.saveMessage?.contains("1") == true)
    }

    @Test
    fun `大复式展示截断两百注`() = runTest {
        val vm = vm(FakeTicketRepo())
        driveToComplete(vm, 10, 4)
        advanceUntilIdle()

        vm.togglePicker()
        // C(10,6)*4 = 840 注，只展示前 200
        assertEquals(840L, vm.ui.value.totalBets)
        assertEquals(200, vm.ui.value.pickedBets.size)

        vm.selectAllBets()
        assertEquals(200, vm.ui.value.selectedBets.size)
        vm.clearBetSelection()
        assertTrue(vm.ui.value.selectedBets.isEmpty())
    }
}
