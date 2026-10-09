package com.ssqpick.feature.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.TicketRules
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 手选号码界面。 */
data class TicketUiState(
    val draft: TicketDraft = TicketDraft(),
    /** 胆码模式下选中的胆码（红）。 */
    val boldMode: Boolean = false,
    val tickets: List<Ticket> = emptyList(),
    val syncMessage: String? = null,
    val drawCount: Long = 0L,
    val newestDraw: Draw? = null,
) {
    val validation by lazy { TicketRules.validate(draft) }
    val isValid: Boolean get() = validation.isValid
    val betCount: Long get() = draft.betCount
    val priceYuan: Long get() = draft.priceYuan
}

class TicketViewModel(
    private val ticketRepository: TicketRepository,
    private val drawRepository: DrawRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TicketUiState())
    val ui: StateFlow<TicketUiState> = _ui.asStateFlow()

    val tickets: StateFlow<List<Ticket>> = ticketRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(drawCount = drawRepository.count())
        }
        viewModelScope.launch {
            drawRepository.observeNewest().collect { newest ->
                _ui.value = _ui.value.copy(newestDraw = newest)
            }
        }
    }

    // ------------------------------------------------------------ 选号

    fun toggleRed(number: Int) {
        val d = _ui.value.draft
        _ui.value = _ui.value.copy(
            draft = if (number in d.redPicked) d.withoutRed(number) else d.withRed(number),
        )
    }

    fun toggleBlue(number: Int) {
        val d = _ui.value.draft
        _ui.value = _ui.value.copy(
            draft = if (number in d.bluePicked) d.withoutBlue(number) else d.withBlue(number),
        )
    }

    fun toggleRedBold(number: Int) {
        val d = _ui.value.draft
        val picked = if (number in d.redPicked) d.redPicked - number else d.redPicked + number
        _ui.value = _ui.value.copy(
            draft = d.copy(
                redBold = if (number in d.redBold) d.redBold - number else d.redBold + number,
                redPicked = if (number in d.redBold) picked - number else picked,
            ),
        )
    }

    fun clear() {
        _ui.value = _ui.value.copy(draft = TicketDraft())
    }

    /** 随机机选一注（不覆盖手选）。 */
    fun randomFill() {
        val mode = _ui.value.draft
        if (mode.redPool.size >= 20) return
        val reds = com.ssqpick.rules.NumberGenerator.sample(33, 6).toSet() - mode.redPool
        val blues = com.ssqpick.rules.NumberGenerator.sample(16, 1).toSet() - mode.bluePool
        _ui.value = _ui.value.copy(
            draft = mode.copy(
                redPicked = mode.redPicked + reds,
                bluePicked = mode.bluePicked + blues,
            ),
        )
    }

    fun save(label: String? = null) {
        val d = _ui.value.draft
        if (!TicketRules.validate(d).isValid) return
        viewModelScope.launch {
            ticketRepository.save(d, label)
            _ui.value = _ui.value.copy(draft = TicketDraft())
        }
    }

    fun delete(ticket: Ticket) {
        viewModelScope.launch { ticketRepository.delete(ticket.id) }
    }

    // ------------------------------------------------------------ 历史数据同步

    /** 从官方接口拉取开奖数据（首次会导入全部历史）。 */
    fun syncDraws() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(syncMessage = "正在同步…")
            when (val r = drawRepository.syncFromOfficial()) {
                is SyncResult.Success ->
                    _ui.value = _ui.value.copy(
                        syncMessage = "新增 ${r.imported} 期，本地共 ${r.totalDraws} 期",
                        drawCount = r.totalDraws,
                    )

                SyncResult.AlreadyUpToDate ->
                    _ui.value = _ui.value.copy(syncMessage = "已是最新，共 ${_ui.value.drawCount} 期")

                is SyncResult.Failure ->
                    _ui.value = _ui.value.copy(syncMessage = "同步失败：${r.reason}")
            }
        }
    }

    /** 某张票的历史战绩。 */
    suspend fun historyOf(draft: TicketDraft) = HistoryScanner.scan(draft, drawRepository.all())
}
