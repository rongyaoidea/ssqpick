package com.ssqpick.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Bet
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.TicketRules
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
        controller.start(_ui.value.machine.mode)
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

    fun selectMode(mode: DrawMode) {
        if (_ui.value.machine.isRunning) return
        controller.reset()
        _ui.value = _ui.value.copy(
            machine = MachineUiState(mode = mode),
            pickerOpen = false,
            pickedBets = emptyList(),
            selectedBets = emptySet(),
        )
    }

    fun save() {
        val state = _ui.value
        if (!state.machine.isComplete) return
        viewModelScope.launch {
            val id = ticketRepository.save(draft = state.machine.toDraft())
            _ui.value = _ui.value.copy(
                saved = true,
                saveMessage = "已保存（#$id），共 ${state.machine.betCount} 注",
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
            _ui.value = _ui.value.copy(
                saved = true,
                saveMessage = "已保存 ${ordered.size} 注单式票（至 #$lastId）",
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
