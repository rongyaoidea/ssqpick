package com.ssqpick.feature.data

import com.ssqpick.model.Draw
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SettingsStorage
import com.ssqpick.store.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 反应性验证：新一期开奖进入数据库后，数据页**全部派生功能**必须同步重算。
 *
 * 用 MutableStateFlow 冒充开奖库，先订阅所有流，再推入新一期，
 * 断言每一项（列表/统计/形态/走势/共现/跟随/最新期）都算上了新数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataViewModelReactivityTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDrawRepository : DrawRepository {
        val state = MutableStateFlow<List<Draw>>(emptyList())

        // 与真实仓库同契约：按日期倒序（新的在前）
        override fun observeAll(): Flow<List<Draw>> = state.map { it.sortedByDescending { d -> d.date } }
        override fun observeNewest(): Flow<Draw?> = state.map { it.maxByOrNull { d -> d.date } }
        override suspend fun all(): List<Draw> = state.value
        override suspend fun count(): Long = state.value.size.toLong()
        override suspend fun insertAll(draws: List<Draw>) {
            state.value += draws
        }

        override suspend fun syncFromOfficial(): SyncResult = SyncResult.AlreadyUpToDate
    }

    private class FakeSettings : SettingsStorage {
        override var drawResultNotify: Boolean = false
    }

    private fun draw(code: String, date: String, red: List<Int>, blue: Int) = Draw(
        code = code,
        date = LocalDate.parse(date),
        red = red.sorted(),
        blue = blue,
    )

    @Test
    fun `新开奖数据进来后所有派生功能同步重算`() = runTest {
        val repo = FakeDrawRepository()
        val d1 = draw("2026001", "2026-01-01", listOf(7, 1, 2, 3, 4, 5), 1)
        val d2 = draw("2026002", "2026-01-02", listOf(7, 12, 13, 14, 15, 16), 2)
        repo.state.value = listOf(d1, d2)

        val vm = DataViewModel(repo, FakeSettings(), computeDispatcher = dispatcher)
        vm.selectFollowTarget(7, isBlue = false)

        // WhileSubscribed 的流需要有人订阅才会收集
        backgroundScope.launch { vm.drawList.collect { } }
        backgroundScope.launch { vm.stats.collect { } }
        backgroundScope.launch { vm.shape.collect { } }
        backgroundScope.launch { vm.trend.collect { } }
        backgroundScope.launch { vm.coOccurrence.collect { } }
        backgroundScope.launch { vm.follow.collect { } }
        backgroundScope.launch { vm.newest.collect { } }
        advanceUntilIdle()

        // 初始：两期数据
        assertEquals(2, vm.drawList.value.size)
        assertEquals(2, vm.stats.value!!.totalDraws)
        assertEquals(2, vm.shape.value!!.draws)
        assertEquals(2, vm.trend.value!!.sums.size)
        assertEquals(2, vm.coOccurrence.value!!.draws)
        assertEquals("2026002", vm.newest.value!!.code)
        // 跟随：7 出现于 d1，其下一期是 d2（d2 本身也含 7，跟随榜里 7 并列在列）
        assertEquals(1, vm.follow.value!!.appearances)
        assertEquals(
            listOf(7, 12, 13, 14, 15, 16),
            vm.follow.value!!.followers.map { it.number }.sorted(),
        )

        // 新一期入库
        val d3 = draw("2026003", "2026-01-03", listOf(8, 9, 10, 11, 17, 18), 3)
        repo.state.value = repo.state.value + d3
        advanceUntilIdle()

        // 全部功能都算上新数据
        assertEquals(3, vm.drawList.value.size)
        assertEquals("2026003", vm.drawList.value.first().code)
        assertEquals(3, vm.stats.value!!.totalDraws)
        assertEquals(3, vm.shape.value!!.draws)
        assertEquals(3, vm.trend.value!!.sums.size)
        assertEquals(3, vm.coOccurrence.value!!.draws)
        assertEquals("2026003", vm.newest.value!!.code)
        // 跟随：d2 也含 7，现在 d2→d3 也算上，出现次数 1 → 2
        assertEquals(2, vm.follow.value!!.appearances)
        assertEquals(2 * 6.0 / 33.0, vm.follow.value!!.expectedPerNumber, 1e-9)
        // d3 的号码（8..）进入跟随分布
        assertTrue(vm.follow.value!!.followers.any { it.number == 8 })
    }
}
