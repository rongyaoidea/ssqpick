package com.ssqpick.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.store.TicketRepository
import com.ssqpick.model.Ticket
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 摇号界面的 UI 状态。 */
data class DrawUiState(
    val machine: MachineUiState = MachineUiState(),
    val history: HistoryRecord? = null,
    val saved: Boolean = false,
    val saveMessage: String? = null,
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

    /** 历史开奖数据流，供界面显示最新期号与对奖入口。 */
    val ticketCount: StateFlow<Int> = ticketRepository.observeAll()
        .combine(_ui) { list, _ -> list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            controller.state.collect { machine ->
                _ui.value = _ui.value.copy(machine = machine)
                if (machine.isComplete) refreshHistory()
            }
        }
    }

    fun start() = controller.start(_ui.value.machine.mode)

    fun reset() {
        controller.reset()
        _ui.value = _ui.value.copy(saved = false, saveMessage = null, history = null)
    }

    fun selectMode(mode: DrawMode) {
        if (_ui.value.machine.isRunning) return
        controller.reset()
        _ui.value = _ui.value.copy(machine = MachineUiState(mode = mode))
    }

    fun save() {
        val state = _ui.value
        if (!state.machine.isComplete) return
        viewModelScope.launch {
            val id = ticketRepository.save(draft = state.machine.toDraft())
            _ui.value = _ui.value.copy(
                saved = true,
                saveMessage = "已保存（#$id），共 ${state.machine.betCount} 注",
            )
        }
    }

    private suspend fun refreshHistory() {
        val draft = _ui.value.machine.toDraft()
        val draws = historyProvider.history(draft)
        _ui.value = _ui.value.copy(history = HistoryScanner.scan(draft, draws))
    }
}
