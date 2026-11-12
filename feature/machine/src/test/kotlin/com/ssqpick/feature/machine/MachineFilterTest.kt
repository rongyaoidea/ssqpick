package com.ssqpick.feature.machine

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.HistoryFilter
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
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 摇号前排除历史中奖组合：筛选在后台完成，结果交给控制器演出；
 * 没同步开奖数据时必须明确拒绝，而不是悄悄忽略勾选。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MachineFilterTest {

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

    private class FakeDrawRepo(private val draws: List<Draw>) : DrawRepository {
        override fun observeAll(): Flow<List<Draw>> = flowOf(draws)
        override fun observeNewest(): Flow<Draw?> = flowOf(draws.firstOrNull())
        override suspend fun all(): List<Draw> = draws
        override suspend fun count(): Long = draws.size.toLong()
        override suspend fun insertAll(draws: List<Draw>) {}
        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private fun vm(draws: List<Draw>) = MachineViewModel(
        ticketRepository = FakeTicketRepo(),
        historyProvider = TicketHistoryProvider(FakeDrawRepo(draws)),
        filterDispatcher = dispatcher,
    )

    @Test
    fun `勾选排除一等奖后不会摇出同一组历史头奖`() = runTest {
        val prize = Draw(
            code = "2026112",
            date = LocalDate(2026, 10, 1),
            red = listOf(1, 2, 3, 4, 5, 6),
            blue = 7,
        )
        val vm = vm(listOf(prize))
        vm.toggleExclusion(PrizeTier.FIRST)
        vm.start()
        advanceUntilIdle()

        // 筛选完成，控制器已进入红球混合
        assertEquals(MachineUiState.Phase.RED_MIXING, vm.controller.state.value.phase)
        assertFalse(vm.ui.value.filterBusy)

        // 驱动到完成，检查整组号码没有复现历史头奖
        var frames = 0
        while (vm.controller.state.value.phase != MachineUiState.Phase.COMPLETE && frames < 60 * 30) {
            vm.controller.advance(1f / 60f)
            frames++
        }
        val done = vm.controller.state.value
        assertEquals(MachineUiState.Phase.COMPLETE, done.phase)
        val draft = TicketDraft(
            redPicked = done.redNumbers.toSet(),
            bluePicked = done.blueNumbers.toSet(),
        )
        assertFalse(
            "摇出的号码不应是历史一等奖组合",
            HistoryFilter.hitsAny(draft, listOf(prize), setOf(PrizeTier.FIRST)),
        )
    }

    @Test
    fun `没有开奖数据时提示并留在待机`() {
        val vm = vm(emptyList())
        vm.toggleExclusion(PrizeTier.THIRD)
        vm.start()
        // 手工推进 testScheduler 上的协程
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MachineUiState.Phase.IDLE, vm.controller.state.value.phase)
        assertNotNull(vm.ui.value.filterMessage)
        assertTrue(vm.ui.value.filterMessage!!.contains("开奖数据"))
        assertFalse(vm.ui.value.filterBusy)
    }

    @Test
    fun `取消勾选后恢复普通摇号`() {
        val vm = vm(emptyList())
        vm.toggleExclusion(PrizeTier.FIRST)
        assertEquals(setOf(PrizeTier.FIRST), vm.ui.value.excludedTiers)
        vm.toggleExclusion(PrizeTier.FIRST)
        assertTrue(vm.ui.value.excludedTiers.isEmpty())
    }
}
