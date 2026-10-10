package com.ssqpick.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.rules.CoOccurrenceReport
import com.ssqpick.rules.FollowReport
import com.ssqpick.rules.MonteCarloReport
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.coOccurrence
import com.ssqpick.rules.computeShape
import com.ssqpick.rules.computeStats
import com.ssqpick.rules.followersOf
import com.ssqpick.rules.simulateMonteCarlo
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SettingsStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 历史跟随的目标：红球或蓝球里的某个号。 */
data class FollowTarget(val number: Int = 1, val isBlue: Boolean = false)

/** 和值走势数据：最近若干期的红球和值 + 均值线。 */
data class TrendData(
    val sums: List<Float>,
    val mean: Float,
) {
    val min: Float get() = sums.min()
    val max: Float get() = sums.max()
}

/** 和值走势：取最新 [window] 期，按时间正序（库按日期倒序存，先 take 再反转）。 */
fun trendOf(draws: List<Draw>, window: Int): TrendData? {
    val latest = draws.take(window).reversed()
    if (latest.isEmpty()) return null
    val sums = latest.map { it.red.sum().toFloat() }
    return TrendData(sums = sums, mean = sums.average().toFloat())
}

/** 数据页：开奖日程、冷热、形态、和值走势、模拟实验室、往期开奖。 */
class DataViewModel(
    private val drawRepository: DrawRepository,
    private val settingsStore: SettingsStorage,
    /** 派生统计的计算调度器；测试注入虚拟调度器，生产用 Default。 */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /**
     * 开奖库共享订阅：stats/shape/trend 三条派生流共用一份，
     * 不然每次库变更要跑 3 遍查询。
     */
    private val draws: SharedFlow<List<Draw>> = drawRepository.observeAll()
        // replay=1：晚加入的派生流能立刻拿到最近一次库快照
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val newest: StateFlow<Draw?> = drawRepository.observeNewest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 往期开奖全量列表（时间倒序），数据页末尾的列表用。 */
    val drawList: StateFlow<List<Draw>> = draws
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 同出/相克：全库红球两两共现。 */
    val coOccurrence: StateFlow<CoOccurrenceReport?> = draws
        .map { if (it.isEmpty()) null else coOccurrence(it) }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 历史跟随的当前目标（默认红球 01）。 */
    private val _followTarget = MutableStateFlow(FollowTarget())
    val followTarget: StateFlow<FollowTarget> = _followTarget.asStateFlow()

    fun selectFollowTarget(number: Int, isBlue: Boolean) {
        _followTarget.value = FollowTarget(number, isBlue)
    }

    /** 历史跟随：目标号出现后的下一期分布。 */
    val follow: StateFlow<FollowReport?> = combine(draws, _followTarget) { ds, t ->
        if (ds.isEmpty()) null else followersOf(ds, t.number, t.isBlue)
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notifyOn = MutableStateFlow(settingsStore.drawResultNotify)
    val notifyOn: StateFlow<Boolean> = _notifyOn.asStateFlow()

    /** 切换"开奖结果通知"订阅（默认关）。 */
    fun toggleDrawNotify() {
        val next = !_notifyOn.value
        settingsStore.drawResultNotify = next
        _notifyOn.value = next
    }

    val stats: StateFlow<NumberStats?> = draws
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shape: StateFlow<ShapeStats?> = draws
        .map(::computeShape)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 和值走势：取最新 [TREND_WINDOW] 期，按时间正序。库按日期倒序存，先 take 再反转。 */
    val trend: StateFlow<TrendData?> = draws
        .map { trendOf(it, TREND_WINDOW) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sim = MutableStateFlow<MonteCarloReport?>(null)
    val sim: StateFlow<MonteCarloReport?> = _sim.asStateFlow()

    private val _simming = MutableStateFlow(false)
    val simming: StateFlow<Boolean> = _simming.asStateFlow()

    init {
        // 首次用固定种子：结果可复现，"再模拟一次"才换种子
        runSimulation(seed = FIXED_SEED)
    }

    fun runSimulation(seed: Long = System.nanoTime()) {
        if (_simming.value) return
        _simming.value = true
        viewModelScope.launch {
            val report = withContext(computeDispatcher) {
                simulateMonteCarlo(paths = PATHS, drawsPerPath = DRAWS_PER_PATH, seed = seed)
            }
            _sim.value = report
            _simming.value = false
        }
    }

    companion object {
        const val PATHS = 200
        const val DRAWS_PER_PATH = 1000
        private const val TREND_WINDOW = 100
        private const val FIXED_SEED = 20261010L
    }
}
