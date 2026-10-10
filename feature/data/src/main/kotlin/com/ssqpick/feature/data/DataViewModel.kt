package com.ssqpick.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.rules.MonteCarloReport
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.computeShape
import com.ssqpick.rules.computeStats
import com.ssqpick.rules.simulateMonteCarlo
import com.ssqpick.store.DrawRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 和值走势数据：最近若干期的红球和值 + 均值线。 */
data class TrendData(
    val sums: List<Float>,
    val mean: Float,
) {
    val min: Float get() = sums.min()
    val max: Float get() = sums.max()
}

/** 数据页：开奖日程、冷热、形态、和值走势、模拟实验室。 */
class DataViewModel(
    private val drawRepository: DrawRepository,
) : ViewModel() {

    val newest: StateFlow<Draw?> = drawRepository.observeNewest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<NumberStats?> = drawRepository.observeAll()
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shape: StateFlow<ShapeStats?> = drawRepository.observeAll()
        .map(::computeShape)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 和值走势：取最新 [TREND_WINDOW] 期，按时间正序。库按日期倒序存，先 take 再反转。 */
    val trend: StateFlow<TrendData?> = drawRepository.observeAll()
        .map { draws ->
            val latest = draws.take(TREND_WINDOW).reversed()
            if (latest.isEmpty()) {
                null
            } else {
                val sums = latest.map { it.red.sum().toFloat() }
                TrendData(sums = sums, mean = sums.average().toFloat())
            }
        }
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
            val report = withContext(Dispatchers.Default) {
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
