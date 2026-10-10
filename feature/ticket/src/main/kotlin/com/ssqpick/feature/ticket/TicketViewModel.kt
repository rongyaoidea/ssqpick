package com.ssqpick.feature.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.computeShape
import com.ssqpick.rules.computeStats
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ssqpick.rules.backtest as runBacktest

/** 手选号码界面。 */
data class TicketUiState(
    val draft: TicketDraft = TicketDraft(),
    /** 红区取号模式：true=点选胆码，false=点选拖码。 */
    val boldMode: Boolean = false,
    /** 蓝区取号模式。 */
    val blueBoldMode: Boolean = false,
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

    /** 冷热统计：开奖库变化时重算，几百期只是毫秒级。 */
    val stats: StateFlow<NumberStats?> = drawRepository.observeAll()
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 开奖形态：同上，画像卡用。 */
    val shape: StateFlow<ShapeStats?> = drawRepository.observeAll()
        .map(::computeShape)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 当前草稿的历史回测：随选号实时更新。
     * 全库扫描是毫秒级，但仍扔到 Default 线程，别占主线程。
     */
    val backtest: StateFlow<DraftBacktest?> = combine(_ui, drawRepository.observeAll()) { u, draws ->
        if (u.draft.isValid) runBacktest(u.draft, draws) else null
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 每张存票的历史最佳：库或票变化时重算。 */
    val histories: StateFlow<Map<Long, HistoryRecord>> =
        combine(tickets, drawRepository.observeAll()) { ts, draws ->
            ts.associate { it.id to HistoryScanner.scan(it.draft, draws) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    /**
     * 红球点选。两种模式互相搬运、互不丢号：在拖码模式点胆码球，
     * 是把它"降级"为拖码而不是删掉，反之亦然。
     */
    fun toggleRed(number: Int) {
        val d = _ui.value.draft
        if (_ui.value.boldMode) {
            toggleRedBold(number)
            return
        }
        _ui.value = _ui.value.copy(
            draft = when {
                number in d.redBold -> d.copy(redBold = d.redBold - number, redPicked = d.redPicked + number)
                number in d.redPicked -> d.withoutRed(number)
                else -> d.withRed(number)
            },
        )
    }

    fun toggleBlue(number: Int) {
        val d = _ui.value.draft
        if (_ui.value.blueBoldMode) {
            toggleBlueBold(number)
            return
        }
        _ui.value = _ui.value.copy(
            draft = when {
                number in d.blueBold -> d.copy(blueBold = emptySet(), bluePicked = d.bluePicked + number)
                number in d.bluePicked -> d.withoutBlue(number)
                else -> d.withBlue(number)
            },
        )
    }

    /**
     * 红球胆码切换：胆与拖互斥，点进来就从另一边摘掉。
     *
     * 之前这里有个 bug：新号码会同时进胆和拖，校验直接报重叠。
     */
    fun toggleRedBold(number: Int) {
        val d = _ui.value.draft
        _ui.value = _ui.value.copy(
            draft = if (number in d.redBold) {
                d.copy(redBold = d.redBold - number)
            } else {
                d.copy(redBold = d.redBold + number, redPicked = d.redPicked - number)
            },
        )
    }

    /**
     * 蓝球胆码切换：最多 1 个，点别的号码是**替换**而不是报错——
     * 点两下才能换胆属于故意为难用户。
     */
    fun toggleBlueBold(number: Int) {
        val d = _ui.value.draft
        _ui.value = _ui.value.copy(
            draft = if (number in d.blueBold) {
                d.copy(blueBold = emptySet())
            } else {
                d.copy(blueBold = setOf(number), bluePicked = d.bluePicked - number)
            },
        )
    }

    fun setBoldMode(on: Boolean) {
        _ui.value = _ui.value.copy(boldMode = on)
    }

    fun setBlueBoldMode(on: Boolean) {
        _ui.value = _ui.value.copy(blueBoldMode = on)
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
