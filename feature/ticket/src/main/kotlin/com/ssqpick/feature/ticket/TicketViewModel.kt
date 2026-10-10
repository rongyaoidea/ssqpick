package com.ssqpick.feature.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.NumberGenerator
import com.ssqpick.rules.PurchaseLedger
import com.ssqpick.rules.TicketPurchaseSummary
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.TicketStatus
import com.ssqpick.rules.ledgerOf
import com.ssqpick.rules.outcomeOf
import com.ssqpick.rules.summaryOf
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.PurchaseRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ssqpick.rules.backtest as runBacktest

/** 回测的时间窗口。 */
enum class BacktestRange(val label: String, val draws: Int?) {
    ALL("全部", null),
    LAST_100("近100期", 100),
    LAST_30("近30期", 30),
}

/** 手选号码界面。 */
data class TicketUiState(
    val draft: TicketDraft = TicketDraft(),
    /** 红区取号模式：true=点选胆码，false=点选拖码。 */
    val boldMode: Boolean = false,
    /** 蓝区取号模式。 */
    val blueBoldMode: Boolean = false,
    /** 机选填的号码：再点机选整体替换，手选的不动。 */
    val autoReds: Set<Int> = emptySet(),
    val autoBlues: Set<Int> = emptySet(),
    val tickets: List<Ticket> = emptyList(),
    val syncMessage: String? = null,
    /** 购买/备注等操作的即时反馈。 */
    val actionMessage: String? = null,
    val drawCount: Long = 0L,
    val newestDraw: Draw? = null,
    val backtestRange: BacktestRange = BacktestRange.ALL,
) {
    val validation by lazy { TicketRules.validate(draft) }
    val isValid: Boolean get() = validation.isValid
    val betCount: Long get() = draft.betCount
    val priceYuan: Long get() = draft.priceYuan
}

class TicketViewModel(
    private val ticketRepository: TicketRepository,
    private val drawRepository: DrawRepository,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TicketUiState())
    val ui: StateFlow<TicketUiState> = _ui.asStateFlow()

    val tickets: StateFlow<List<Ticket>> = ticketRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val purchases: StateFlow<List<Purchase>> = purchaseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前草稿的历史回测：随选号与区间切换实时更新。
     * 全库扫描是毫秒级，但仍扔到 Default 线程，别占主线程。
     */
    val backtest: StateFlow<DraftBacktest?> =
        combine(_ui, drawRepository.observeAll()) { u, draws ->
            val window = u.backtestRange.draws?.let { n -> draws.take(n) } ?: draws
            if (u.draft.isValid) runBacktest(u.draft, window) else null
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 每张存票的状态。
     *
     * 有购买记录：看最近一笔——绑定到哪期就报哪期的结果，没绑到就是未开奖。
     * 无购买记录：沿用旧口径（相对最新一期的假设核对），漂在"假如买了"的语义上。
     */
    val statuses: StateFlow<Map<Long, TicketStatus>> =
        combine(tickets, purchases, drawRepository.observeAll()) { ts, ps, draws ->
            val asc = draws.asReversed()
            val newest = draws.firstOrNull()
            val byTicket = ps.groupBy { it.ticketId }
            ts.associate { t ->
                val mine = byTicket[t.id].orEmpty()
                t.id to if (mine.isEmpty()) {
                    TicketRules.statusOf(t, newest)
                } else {
                    val latest = mine.maxByOrNull { it.purchasedAtEpochMs }!!
                    val o = outcomeOf(latest, t, asc)
                    when {
                        o.isPending -> TicketStatus.NotDrawn
                        o.hasWon -> TicketStatus.Won(o.amountYuan, o.topTier)
                        else -> TicketStatus.Missed
                    }
                }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 每张存票的购买汇总（笔数/投入/奖金/最近一笔）。 */
    val purchaseSummaries: StateFlow<Map<Long, TicketPurchaseSummary>> =
        combine(tickets, purchases, drawRepository.observeAll()) { ts, ps, draws ->
            val asc = draws.asReversed()
            val byTicket = ps.groupBy { it.ticketId }
            ts.associate { t -> t.id to summaryOf(t, byTicket[t.id].orEmpty(), asc) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 真实账本：全部购买的投入/奖金/累计净收益时间线。 */
    val ledger: StateFlow<PurchaseLedger> =
        combine(purchases, tickets, drawRepository.observeAll()) { ps, ts, draws ->
            ledgerOf(ps, ts.associateBy { it.id }, draws.asReversed())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseLedger(0, 0, 0L, 0L, emptyList()))

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
     *
     * 手一碰，这组红号就整组归手选（autoReds 清空）：否则下次机选
     * 会把你刚亲手留下的号也重摇掉。
     */
    fun toggleRed(number: Int) {
        _ui.value = _ui.value.copy(autoReds = emptySet())
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
        _ui.value = _ui.value.copy(autoBlues = emptySet())
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

    fun setBacktestRange(range: BacktestRange) {
        _ui.value = _ui.value.copy(backtestRange = range)
    }

    fun clear() {
        _ui.value = _ui.value.copy(draft = TicketDraft(), autoReds = emptySet(), autoBlues = emptySet())
    }

    /**
     * 机选：只补齐缺的部分，替换上次机选填的号，手选的不动。
     *
     * 点一次出一组随机；再点是全新的一组，不会越点越多。
     * 手选已凑满时什么都不做——总不能把人选好的票再撑爆。
     */
    fun randomFill() {
        val state = _ui.value
        val d = state.draft
        val handPicked = d.redPicked - state.autoReds
        val handBluePool = d.bluePool - state.autoBlues
        val redNeed = (TicketRules.RED_PICK - d.redBold.size - handPicked.size).coerceAtLeast(0)
        val blueNeed = if (handBluePool.isEmpty()) 1 else 0
        if (redNeed == 0 && blueNeed == 0) return
        val redAvail = (1..TicketRules.RED_TOTAL).filter { it !in d.redPool || it in state.autoReds }
        val blueAvail = (1..TicketRules.BLUE_TOTAL).filter { it !in d.bluePool || it in state.autoBlues }
        val reds = NumberGenerator.sampleFrom(redAvail, redNeed).toSet()
        val blues = NumberGenerator.sampleFrom(blueAvail, blueNeed).toSet()
        _ui.value = state.copy(
            draft = d.copy(
                redPicked = handPicked + reds,
                bluePicked = (d.bluePicked - state.autoBlues) + blues,
            ),
            autoReds = reds,
            autoBlues = blues,
        )
    }

    fun save(label: String? = null) {
        val d = _ui.value.draft
        if (!TicketRules.validate(d).isValid) return
        viewModelScope.launch {
            ticketRepository.save(d, label)
            _ui.value = _ui.value.copy(draft = TicketDraft(), autoReds = emptySet(), autoBlues = emptySet())
        }
    }

    // ------------------------------------------------------------ 票管理

    fun delete(ticket: Ticket) {
        viewModelScope.launch {
            ticketRepository.delete(ticket.id)
            _ui.value = _ui.value.copy(actionMessage = "已删除号码 #${ticket.id}")
        }
    }

    fun rename(ticket: Ticket, label: String?) {
        val clean = label?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            ticketRepository.rename(ticket.id, clean)
            _ui.value = _ui.value.copy(
                actionMessage = if (clean == null) "已清除 #${ticket.id} 的备注" else "备注已保存：$clean",
            )
        }
    }

    /** 记录一笔购买：花费按当前注数 × 2 元。 */
    fun recordPurchase(ticket: Ticket) {
        viewModelScope.launch {
            val cost = ticket.draft.priceYuan
            purchaseRepository.record(ticket.id, cost)
            _ui.value = _ui.value.copy(
                actionMessage = "已记录一笔购买（$cost 元）：下一期开奖后自动对奖",
            )
        }
    }

    /** 撤销最近一笔购买（手滑记错时用）。 */
    fun undoLastPurchase(ticket: Ticket) {
        val latest = purchases.value.filter { it.ticketId == ticket.id }
            .maxByOrNull { it.purchasedAtEpochMs } ?: return
        viewModelScope.launch {
            purchaseRepository.delete(latest.id)
            _ui.value = _ui.value.copy(actionMessage = "已撤销 #${ticket.id} 最近一笔购买")
        }
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
