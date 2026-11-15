package com.ssqpick.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.sim.SimResult
import com.ssqpick.rules.sim.SimSource
import com.ssqpick.rules.sim.SimSpec
import com.ssqpick.rules.sim.SimStrategy
import com.ssqpick.rules.sim.simulate
import com.ssqpick.store.DrawRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 实验室的买法：每期机选，或守当前选号草稿。 */
enum class SimLabStrategyKind(val label: String) {
    RANDOM_SINGLE("每期机选"),
    MY_DRAFT("我的号码"),
}

const val FIXED_SIM_SEED = 20261116L

/**
 * 模拟实验室的界面状态。
 *
 * 只保存参数和最后一次结果；开奖库全量留在 ViewModel 内部，不复制进 Compose。
 */
data class SimLabUiState(
    val source: SimSource = SimSource.UNIFORM,
    val strategy: SimLabStrategyKind = SimLabStrategyKind.RANDOM_SINGLE,
    val paths: Int = 200,
    val drawsPerPath: Int = 1_000,
    val budgetYuan: Long? = null,
    val seed: Long = FIXED_SIM_SEED,
    val draft: TicketDraft? = null,
    val draftBetCount: Long = 0L,
    val drawsAvailable: Int = 0,
    val running: Boolean = false,
    val progress: Float = 0f,
    val result: SimResult? = null,
    val error: String? = null,
) {
    val draftValid: Boolean get() = draft != null && draftBetCount > 0

    /** 组合数量（期 × 次数），UI 用来提示规模。 */
    val drawEvaluations: Long get() = paths.toLong() * drawsPerPath

    val maxDrawEvaluations: Long
        get() = when (strategy) {
            SimLabStrategyKind.RANDOM_SINGLE -> SimSpec.MAX_DRAW_EVALUATIONS
            SimLabStrategyKind.MY_DRAFT -> SimSpec.MAX_DRAFT_DRAW_EVALUATIONS
        }

    val scaleTooLarge: Boolean get() = drawEvaluations > maxDrawEvaluations
}

/**
 * 模拟实验室 ViewModel。
 *
 * 计算在 [computeDispatcher] 上跑，进度通过 StateFlow 回传；取消会直接停掉引擎。
 */
class SimLabViewModel(
    private val drawRepository: DrawRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _ui = MutableStateFlow(SimLabUiState())
    val ui: StateFlow<SimLabUiState> = _ui.asStateFlow()

    private var draws: List<Draw> = emptyList()
    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            drawRepository.observeAll()
                .catch { /* 数据层异常不应让实验室整页打不开；运行时再提示 */ }
                .collect { list ->
                    draws = list
                    _ui.update { it.copy(drawsAvailable = list.size) }
                }
        }
    }

    fun setSource(value: SimSource) {
        if (_ui.value.running) return
        _ui.update { it.copy(source = value, error = null) }
    }

    fun setStrategy(value: SimLabStrategyKind) {
        if (_ui.value.running) return
        _ui.update { it.copy(strategy = value, error = null) }
    }

    fun setPaths(value: Int) {
        if (_ui.value.running) return
        _ui.update { it.copy(paths = value.coerceIn(1, SimSpec.MAX_PATHS), error = null) }
    }

    fun setDrawsPerPath(value: Int) {
        if (_ui.value.running) return
        _ui.update { it.copy(drawsPerPath = value.coerceIn(1, SimSpec.MAX_DRAWS), error = null) }
    }

    fun setBudget(value: Long?) {
        if (_ui.value.running) return
        _ui.update { it.copy(budgetYuan = value, error = null) }
    }

    fun rerollSeed() {
        if (_ui.value.running) return
        _ui.update { it.copy(seed = System.currentTimeMillis(), error = null) }
    }

    /** 打开实验室/切换策略时把选号页当前草稿带进来。 */
    fun setDraft(draft: TicketDraft?) {
        val bets = if (draft != null && draft.isValid) draft.betCount else 0L
        _ui.update {
            it.copy(
                draft = if (bets > 0) draft else null,
                draftBetCount = bets,
            )
        }
    }

    /** 开始实验；参数不合法时留在页面并给明确提示。 */
    fun run() {
        val state = _ui.value
        if (state.running) return
        if (state.scaleTooLarge) {
            _ui.update {
                it.copy(error = "实验规模过大：${it.paths} 次 × ${it.drawsPerPath} 期，请降低一项。")
            }
            return
        }
        val strategy = when (state.strategy) {
            SimLabStrategyKind.RANDOM_SINGLE -> SimStrategy.RandomSingle

            SimLabStrategyKind.MY_DRAFT -> {
                val draft = state.draft
                if (draft == null || !draft.isValid) {
                    _ui.update { it.copy(error = "选号页还没有合法号码，先在选号页选一注。") }
                    return
                }
                SimStrategy.FixedDraft(draft)
            }
        }
        if (state.source == SimSource.HISTORICAL && draws.isEmpty()) {
            _ui.update { it.copy(error = "历史重放需要先在“数据”页同步开奖数据。") }
            return
        }

        val spec = try {
            SimSpec(
                source = state.source,
                strategy = strategy,
                paths = state.paths,
                drawsPerPath = state.drawsPerPath,
                budgetYuan = state.budgetYuan,
                seed = state.seed,
            )
        } catch (e: IllegalArgumentException) {
            _ui.update { it.copy(error = e.message ?: "实验参数不合法") }
            return
        }

        _ui.update { it.copy(running = true, progress = 0f, error = null, result = null) }
        runJob = viewModelScope.launch {
            try {
                val result = withContext(computeDispatcher) {
                    simulate(
                        spec = spec,
                        draws = draws,
                        onProgress = { p -> _ui.update { it.copy(progress = p) } },
                        shouldCancel = { !isActive },
                    )
                }
                _ui.update {
                    it.copy(
                        running = false,
                        progress = 1f,
                        result = result ?: it.result,
                    )
                }
            } catch (c: CancellationException) {
                _ui.update { it.copy(running = false) }
                throw c
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        running = false,
                        error = e.message ?: "模拟失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
    }
}
