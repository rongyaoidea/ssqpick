package com.ssqpick.feature.data

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.sim.SimSource
import com.ssqpick.rules.sim.SimStrategy
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimLabViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDraws(private val draws: List<Draw> = emptyList()) : DrawRepository {
        override fun observeAll(): Flow<List<Draw>> = flowOf(draws)
        override fun observeNewest(): Flow<Draw?> = flowOf(draws.firstOrNull())
        override suspend fun all(): List<Draw> = draws
        override suspend fun count(): Long = draws.size.toLong()
        override suspend fun insertAll(draws: List<Draw>) {}
        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private fun vm(draws: List<Draw> = emptyList()) = SimLabViewModel(
        drawRepository = FakeDraws(draws),
        computeDispatcher = dispatcher,
    )

    @Test
    fun `默认参数可以跑完一次小实验`() = runTest {
        val vm = vm()
        vm.setPaths(4)
        vm.setDrawsPerPath(6)
        vm.run()
        advanceUntilIdle()

        assertTrue(!vm.ui.value.running)
        assertNotNull(vm.ui.value.result)
        val r = vm.ui.value.result!!
        assertEquals(4L * 6 * 2, r.totalCostYuan)
        assertEquals(24L, r.totalBetDraws)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `守号模式使用当前草稿的注数`() = runTest {
        val vm = vm()
        val draft = TicketDraft(
            redPicked = setOf(1, 2, 3, 4, 5, 6, 7),
            bluePicked = setOf(1, 2),
        )
        vm.setDraft(draft)
        vm.setStrategy(SimLabStrategyKind.MY_DRAFT)
        vm.setPaths(2)
        vm.setDrawsPerPath(3)
        vm.run()
        advanceUntilIdle()

        val r = vm.ui.value.result!!
        assertEquals(84L, r.totalBetDraws) // 7+2 = 14 注/期 × 3 期 × 2 路径
        assertEquals(SimStrategy.FixedDraft(draft), r.spec.strategy)
    }

    @Test
    fun `AB 对照拒绝复式号码`() = runTest {
        val vm = vm()
        vm.setDraft(TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6, 7), bluePicked = setOf(1)))
        vm.setPaths(2)
        vm.setDrawsPerPath(2)
        vm.runComparison()
        advanceUntilIdle()

        assertNull(vm.ui.value.comparison)
        assertNotNull(vm.ui.value.error)
        assertTrue(!vm.ui.value.running)
    }

    @Test
    fun `AB 对照用同一成本和种子跑守号与机选`() = runTest {
        val vm = vm()
        val draft = TicketDraft(redPicked = setOf(1, 2, 3, 4, 5, 6), bluePicked = setOf(7))
        vm.setDraft(draft)
        vm.setPaths(2)
        vm.setDrawsPerPath(3)
        vm.runComparison()
        advanceUntilIdle()

        val c = vm.ui.value.comparison!!
        assertNull(vm.ui.value.error)
        assertEquals(SimStrategy.FixedDraft(draft), c.fixed.spec.strategy)
        assertEquals(SimStrategy.RandomSingle, c.random.spec.strategy)
        assertEquals(c.fixed.totalCostYuan, c.random.totalCostYuan)
        assertEquals(c.fixed.spec.seed, c.random.spec.seed)
    }

    @Test
    fun `历史重放无数据时明确提示`() = runTest {
        val vm = vm()
        vm.setSource(SimSource.HISTORICAL)
        vm.setPaths(2)
        vm.setDrawsPerPath(2)
        vm.run()
        advanceUntilIdle()

        assertEquals(SimSource.HISTORICAL, vm.ui.value.source) // 只验证状态没有被偷偷改成别的
        assertNotNull(vm.ui.value.error)
        assertTrue(vm.ui.value.error!!.contains("开奖数据"))
        assertNull(vm.ui.value.result)
    }

    @Test
    fun `规模过大时不启动`() = runTest {
        val vm = vm()
        vm.setPaths(5_000)
        vm.setDrawsPerPath(5_000)
        vm.run()
        advanceUntilIdle()

        assertNotNull(vm.ui.value.error)
        assertTrue(vm.ui.value.error!!.contains("规模"))
        assertNull(vm.ui.value.result)
    }

    @Test
    fun `草稿不合法时我的号码不可用`() = runTest {
        val vm = vm()
        vm.setDraft(TicketDraft())
        assertTrue(!vm.ui.value.draftValid)
        vm.setStrategy(SimLabStrategyKind.MY_DRAFT)
        vm.setPaths(1)
        vm.setDrawsPerPath(1)
        vm.run()
        advanceUntilIdle()
        assertNotNull(vm.ui.value.error)
        assertTrue(vm.ui.value.error!!.contains("号码"))
    }

    @Test
    fun `历史重放有数据时按真实开奖算`() = runTest {
        val draw = Draw(
            code = "T1",
            date = LocalDate(2026, 1, 1),
            red = listOf(1, 2, 3, 4, 5, 6),
            blue = 7,
        )
        val vm = vm(listOf(draw))
        advanceUntilIdle() // 让 init 里的 observeAll 先收到数据
        vm.setSource(SimSource.HISTORICAL)
        vm.setPaths(1)
        vm.setDrawsPerPath(1)
        vm.run()
        advanceUntilIdle()

        val r = vm.ui.value.result
        assertNotNull(r)
        assertEquals(1L, r!!.spec.drawsPerPath.toLong())
        assertEquals(2L, r.totalCostYuan)
        assertEquals(1L, r.totalBetDraws)
        assertEquals(PrizeTier.entries.size, r.tierStats.size)
        assertTrue(vm.ui.value.drawsAvailable == 1)
    }
}
