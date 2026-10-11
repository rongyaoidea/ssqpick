package com.ssqpick.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Bet
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.targetLabel
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun start() {
        closePicker()
        controller.start(_ui.value.machine.redCount, _ui.value.machine.blueCount)
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
