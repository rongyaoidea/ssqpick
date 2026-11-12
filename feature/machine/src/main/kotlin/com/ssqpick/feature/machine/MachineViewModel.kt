package com.ssqpick.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Bet
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.HistoryFilter
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.targetLabel
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 摇号界面的 UI 状态。 */
data class DrawUiState(
    val machine: MachineUiState = MachineUiState(),
    val history: HistoryRecord? = null,
    val saved: Boolean = false,
    val saveMessage: String? = null,
    /** 逐注挑选是否展开。 */
    val pickerOpen: Boolean = false,
    /** 展开的注（最多展示 [PICKER_DISPLAY_LIMIT] 注）。 */
    val pickedBets: List<Bet> = emptyList(),
    /** 本次摇号总注数（展示被截断时提示用）。 */
    val totalBets: Long = 0L,
    /** 已勾选的注。 */
    val selectedBets: Set<Bet> = emptySet(),
    /** 摇号前要排除的历史奖级（空集合 = 不过滤）。 */
    val excludedTiers: Set<PrizeTier> = emptySet(),
    /** 正在后台做历史筛选：此时开始按钮禁用，避免连点重复起摇。 */
    val filterBusy: Boolean = false,
    /** 筛选失败/无数据时的提示。 */
    val filterMessage: String? = null,
)

/**
 * 摇号界 ViewModel。
 *
 * 负责：驱动摇号状态机、保存票、并对摇出的结果查一遍历史战绩
 * （"这组号码历史上最好中过什么"）。
 */
class MachineViewModel(
    private val ticketRepository: TicketRepository,
    private val historyProvider: TicketHistoryProvider,
    val controller: MachineController = MachineController(),
    /** 历史筛选在后台算：2000 期 × 若干次尝试不应占住主线程。 */
    private val filterDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _ui = MutableStateFlow(DrawUiState())
    val ui: StateFlow<DrawUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { machine ->
                _ui.value = _ui.value.copy(machine = machine)
                if (machine.isComplete) refreshHistory()
            }
        }
    }

    /**
     * 开始摇号。勾了"排除历史中奖"时先把历史数据取回来，在后台做拒样，
     * 取到满足条件的号码再交给控制器演出；否则同步起摇，不多一次调度。
     */
    fun start() {
        val current = _ui.value
        if (current.machine.isRunning || current.filterBusy) return
        closePicker()
        val state = _ui.value
        if (state.excludedTiers.isEmpty()) {
            controller.start(state.machine.redCount, state.machine.blueCount)
            return
        }

        val redCount = state.machine.redCount
        val blueCount = state.machine.blueCount
        val tiers = state.excludedTiers
        _ui.value = state.copy(filterBusy = true, filterMessage = null)
        viewModelScope.launch {
            try {
                val draws = historyProvider.draws()
                if (draws.isEmpty()) {
                    _ui.value = _ui.value.copy(
                        filterBusy = false,
                        filterMessage = "还没有开奖数据，先在“数据”页同步，才能排除历史中奖组合。",
                    )
                    return@launch
                }
                val sample = withContext(filterDispatcher) {
                    HistoryFilter.sample(redCount, blueCount, draws, tiers)
                }
                if (sample == null) {
                    _ui.value = _ui.value.copy(
                        filterBusy = false,
                        filterMessage = "找不到同时满足这些排除项的号码，请少选一档或缩小复式规模。",
                    )
                } else {
                    controller.start(redCount, blueCount, sample.reds, sample.blues)
                    _ui.value = _ui.value.copy(filterBusy = false)
                }
            } catch (c: CancellationException) {
                // 页面销毁时取消原样抛出，但 busy 标记要归位，避免复用 VM 时卡死
                _ui.value = _ui.value.copy(filterBusy = false)
                throw c
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    filterBusy = false,
                    filterMessage = "筛选失败：${e.message ?: e::class.simpleName ?: "未知错误"}",
                )
            }
        }
    }

    /** 勾选/取消一档排除项；摇号或筛选进行中忽略。 */
    fun toggleExclusion(tier: PrizeTier) {
        val state = _ui.value
        if (state.machine.isRunning || state.filterBusy) return
        val next = if (tier in state.excludedTiers) {
            state.excludedTiers - tier
        } else {
            state.excludedTiers + tier
        }
        _ui.value = state.copy(excludedTiers = next, filterMessage = null)
    }

    fun reset() {
        controller.reset()
        _ui.value = _ui.value.copy(
            saved = false,
            saveMessage = null,
            history = null,
            pickerOpen = false,
            pickedBets = emptyList(),
            selectedBets = emptySet(),
            filterBusy = false,
            filterMessage = null,
        )
    }

    /** 选预设：等价于把两个数量设成预设值。 */
    fun selectMode(mode: DrawMode) {
        applyCounts(mode.redCount, mode.blueCount)
    }

    /** 自定义红球数：蓝球被牵连时一起夹到合法范围（注数上限 5 万）。 */
    fun setRedCount(value: Int) {
        val (red, blue) = DrawLimits.clampRed(value, _ui.value.machine.blueCount)
        applyCounts(red, blue)
    }

    /** 自定义蓝球数：红球被牵连时一起夹到合法范围。 */
    fun setBlueCount(value: Int) {
        val (red, blue) = DrawLimits.clampBlue(_ui.value.machine.redCount, value)
        applyCounts(red, blue)
    }

    private fun applyCounts(red: Int, blue: Int) {
        if (_ui.value.machine.isRunning) return
        // 先让控制器进入 IDLE(red, blue)，再照抄它的状态——
        // 否则控制器稍后的 IDLE 发射会把 _ui 里的旧数量覆盖回去。
        controller.configure(red, blue)
        _ui.value = _ui.value.copy(
            machine = controller.state.value,
            pickerOpen = false,
            pickedBets = emptyList(),
            selectedBets = emptySet(),
            filterMessage = null,
        )
    }

    fun save() {
        val state = _ui.value
        if (!state.machine.isComplete) return
        viewModelScope.launch {
            val draft = state.machine.toDraft()
            val id = ticketRepository.save(draft = draft)
            val period = targetLabel(System.currentTimeMillis(), historyProvider.history(draft).asReversed())
            _ui.value = _ui.value.copy(
                saved = true,
                saveMessage = "已保存（#$id），共 ${state.machine.betCount} 注 · 对应 $period",
                pickerOpen = false,
                selectedBets = emptySet(),
            )
        }
    }

    // ------------------------------------------------------------ 逐注保存

    /**
     * 展开/收起逐注挑选。复式一次摇出几十注时整单存太粗，
     * 这里拆成单注勾选。展示截断在 [PICKER_DISPLAY_LIMIT] 注——
     * 大复式 840 注全渲染又卡又没法选，真要全存就用"保存这注"。
     */
    fun togglePicker() {
        val state = _ui.value
        if (state.pickerOpen) {
            closePicker()
            return
        }
        if (!state.machine.isComplete || state.saved) return
        val draft = state.machine.toDraft()
        _ui.value = state.copy(
            pickerOpen = true,
            pickedBets = TicketRules.expand(draft, limit = PICKER_DISPLAY_LIMIT.toLong()),
            totalBets = state.machine.betCount,
            selectedBets = emptySet(),
        )
    }

    private fun closePicker() {
        _ui.value = _ui.value.copy(
            pickerOpen = false,
            pickedBets = emptyList(),
            selectedBets = emptySet(),
        )
    }

    fun toggleBet(bet: Bet) {
        val sel = _ui.value.selectedBets
        _ui.value = _ui.value.copy(
            selectedBets = if (bet in sel) sel - bet else sel + bet,
        )
    }

    fun selectAllBets() {
        _ui.value = _ui.value.copy(selectedBets = _ui.value.pickedBets.toSet())
    }

    fun clearBetSelection() {
        _ui.value = _ui.value.copy(selectedBets = emptySet())
    }

    /** 把勾选的注逐注存成单式票。 */
    fun saveSelected() {
        val state = _ui.value
        if (!state.machine.isComplete || state.saved || state.selectedBets.isEmpty()) return
        viewModelScope.launch {
            var lastId = 0L
            // 逐注存：每注都是独立单式票，对奖与状态都按单注走
            val ordered = state.pickedBets.filter { it in state.selectedBets }
            for (bet in ordered) {
                lastId = ticketRepository.save(
                    draft = TicketDraft(
                        redPicked = bet.red.toSet(),
                        bluePicked = bet.blues.toSet(),
                    ),
                )
            }
            val period = targetLabel(
                System.currentTimeMillis(),
                historyProvider.history(state.machine.toDraft()).asReversed(),
            )
            _ui.value = _ui.value.copy(
                saved = true,
                saveMessage = "已保存 ${ordered.size} 注单式票（至 #$lastId）· 对应 $period",
                pickerOpen = false,
                pickedBets = emptyList(),
                selectedBets = emptySet(),
            )
        }
    }

    companion object {
        const val PICKER_DISPLAY_LIMIT = 200
    }

    private suspend fun refreshHistory() {
        val draft = _ui.value.machine.toDraft()
        val draws = historyProvider.history(draft)
        _ui.value = _ui.value.copy(history = HistoryScanner.scan(draft, draws))
    }
}
