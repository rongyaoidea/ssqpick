package com.ssqpick.feature.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.DraftFingerprint
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HistoryScanner
import com.ssqpick.rules.NumberGenerator
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.PurchaseLedger
import com.ssqpick.rules.PurchaseOutcome
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.SmsCommand
import com.ssqpick.rules.TicketPurchaseSummary
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.TicketStatus
import com.ssqpick.rules.computeShape
import com.ssqpick.rules.computeStats
import com.ssqpick.rules.fingerprintOf
import com.ssqpick.rules.ledgerOf
import com.ssqpick.rules.outcomeOf
import com.ssqpick.rules.summaryOf
import com.ssqpick.rules.targetLabel
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.PurchaseRepository
import com.ssqpick.store.SyncResult
import com.ssqpick.store.TicketRepository
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    /** 单式多注复制的勾选（最多 [SmsCommand.MAX_MULTI_BETS] 注）。 */
    val selectedCopyIds: Set<Long> = emptySet(),
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
    private val draftStorage: DraftStorage,
    /** 派生统计的计算调度器；测试注入虚拟调度器，生产用 Default。 */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _ui = MutableStateFlow(TicketUiState())
    val ui: StateFlow<TicketUiState> = _ui.asStateFlow()

    /**
     * 开奖库的共享订阅：下面多条派生流共用一份——
     * SQLDelight 的 asFlow 是冷流，每 combine 一次就多一次独立查询，
     * 不共享的话每次库变更要跑 7 遍 selectAllDraws + 映射。
     */
    private val draws: SharedFlow<List<Draw>> = drawRepository.observeAll()
        // replay=1：晚加入的派生流能立刻拿到最近一次库快照
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val tickets: StateFlow<List<Ticket>> = ticketRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val purchases: StateFlow<List<Purchase>> = purchaseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 购买结算共享流：所有"这笔购买结果如何"的派生流都从这里取，
     * 一笔购买只结算一次。此前 statuses/summaries/ledger/calendar 各自
     * 调 outcomeOf，同一笔购买要重复算 4 遍。
     */
    private val settlements: StateFlow<Map<Long, PurchaseOutcome>> =
        combine(purchases, tickets, draws) { ps, ts, ds ->
            val byId = ts.associateBy { it.id }
            val asc = ds.asReversed()
            val out = HashMap<Long, PurchaseOutcome>(ps.size)
            for (p in ps) {
                val t = byId[p.ticketId] ?: continue
                out[p.id] = outcomeOf(p, t, asc)
            }
            out
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 当前草稿的历史回测：随选号与区间切换实时更新。
     * 全库扫描是毫秒级，但仍扔到 Default 线程，别占主线程。
     */
    val backtest: StateFlow<DraftBacktest?> =
        combine(_ui, draws) { u, draws ->
            val window = u.backtestRange.draws?.let { n -> draws.take(n) } ?: draws
            if (u.draft.isValid) runBacktest(u.draft, window) else null
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 每张存票的状态。
     *
     * 有购买记录：看最近一笔——绑定到哪期就报哪期的结果，没绑到就是未开奖。
     * 无购买记录：沿用旧口径（相对最新一期的假设核对），漂在"假如买了"的语义上。
     */
    val statuses: StateFlow<Map<Long, TicketStatus>> =
        combine(tickets, purchases, settlements, draws) { ts, ps, settled, ds ->
            val asc = ds.asReversed()
            val byTicket = ps.groupBy { it.ticketId }
            ts.associate { t ->
                val mine = byTicket[t.id].orEmpty()
                t.id to if (mine.isEmpty()) {
                    // 没有购买记录：按票自己绑定的那一期判（不是最新一期）
                    TicketRules.statusOf(t, asc)
                } else {
                    val latest = mine.maxByOrNull { it.purchasedAtEpochMs }!!
                    when (val o = settled[latest.id]) {
                        null -> TicketStatus.NotDrawn

                        else -> when {
                            o.isPending -> TicketStatus.NotDrawn
                            o.hasWon -> TicketStatus.Won(o.amountYuan, o.topTier)
                            else -> TicketStatus.Missed
                        }
                    }
                }
            }
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 形态统计：库变化时算一次，指纹等派生流共用，避免每次选号重扫全库。 */
    private val shapeStats: StateFlow<ShapeStats> = draws
        .map(::computeShape)
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), computeShape(emptyList()))

    /** 选号指纹：当前草稿在历史分布中的位置，随选号实时更新。 */
    val fingerprint: StateFlow<DraftFingerprint?> =
        combine(_ui, draws, shapeStats) { u, ds, shape ->
            fingerprintOf(u.draft, ds, shape)
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 号码遗漏：选号格下的小字用。开奖库变化时重算，毫秒级。 */
    val stats: StateFlow<NumberStats?> = draws
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 每张存票的购买汇总（笔数/投入/奖金/最近一笔）。 */
    val purchaseSummaries: StateFlow<Map<Long, TicketPurchaseSummary>> =
        combine(tickets, purchases, settlements) { ts, ps, settled ->
            val byTicket = ps.groupBy { it.ticketId }
            ts.associate { t -> t.id to summaryOf(byTicket[t.id].orEmpty(), settled) }
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 真实账本：全部购买的投入/奖金/累计净收益时间线。 */
    val ledger: StateFlow<PurchaseLedger> =
        combine(purchases, settlements) { ps, settled -> ledgerOf(ps, settled) }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseLedger(0, 0, 0L, 0L, emptyList()))

    /** 购票日历：购买日期 → 当日最高信号标记。 */
    val calendarMarks: StateFlow<Map<LocalDate, DayMark>> =
        combine(purchases, settlements) { ps, settled -> dayMarksOf(ps, settled) }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 每张存票的历史最佳：库或票变化时重算。 */
    val histories: StateFlow<Map<Long, HistoryRecord>> =
        combine(tickets, draws) { ts, draws ->
            ts.associate { it.id to HistoryScanner.scan(it.draft, draws) }
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 每张票对应的期号文案：库里已有那期就写期号，否则写预计开奖日。 */
    val ticketPeriods: StateFlow<Map<Long, String>> =
        combine(tickets, draws) { ts, ds ->
            val asc = ds.asReversed()
            ts.associate { t -> t.id to targetLabel(t.createdAtEpochMs, asc) }
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // 恢复上次的草稿：进程被杀或退出应用后，选了一半的号还在
        _ui.value = _ui.value.copy(draft = draftStorage.load())
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
        draftStorage.save(_ui.value.draft)
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
        draftStorage.save(_ui.value.draft)
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
        draftStorage.save(_ui.value.draft)
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
        draftStorage.save(_ui.value.draft)
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
        draftStorage.save(_ui.value.draft)
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
        draftStorage.save(_ui.value.draft)
    }

    fun save(label: String? = null) {
        val d = _ui.value.draft
        if (!TicketRules.validate(d).isValid) return
        viewModelScope.launch {
            val id = ticketRepository.save(d, label)
            val period = targetLabel(System.currentTimeMillis(), drawRepository.all().asReversed())
            _ui.value = _ui.value.copy(
                draft = TicketDraft(),
                autoReds = emptySet(),
                autoBlues = emptySet(),
                actionMessage = "已保存（#$id）· 对应 $period",
            )
            draftStorage.save(_ui.value.draft)
        }
    }

    // ------------------------------------------------------------ 票管理

    fun delete(ticket: Ticket) {
        viewModelScope.launch {
            ticketRepository.delete(ticket.id)
            _ui.value = _ui.value.copy(
                actionMessage = "已删除号码 #${ticket.id}",
                selectedCopyIds = _ui.value.selectedCopyIds - ticket.id,
            )
        }
    }

    /**
     * 单式多注复制：勾选/取消一张单式票。
     * 上限 [SmsCommand.MAX_MULTI_BETS] 注（单票最多 5 注），超了给明确提示而不是静默。
     */
    fun toggleCopySelection(ticket: Ticket) {
        val cur = _ui.value.selectedCopyIds
        _ui.value = when {
            ticket.id in cur -> _ui.value.copy(selectedCopyIds = cur - ticket.id)

            cur.size >= SmsCommand.MAX_MULTI_BETS -> _ui.value.copy(
                actionMessage = "单票最多勾选 ${SmsCommand.MAX_MULTI_BETS} 注（单式多注上限）",
            )

            else -> _ui.value.copy(selectedCopyIds = cur + ticket.id)
        }
    }

    fun clearCopySelection() {
        _ui.value = _ui.value.copy(selectedCopyIds = emptySet())
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
}

/** 购票日历的单日标记。 */
enum class DayMark { NONE, PENDING, MISS, WIN }

/** 购买日期 → 当日标记；同一天多笔取信息量最大的一个（中奖 > 未中 > 待开奖）。 */
fun dayMarksOf(
    purchases: List<Purchase>,
    outcomes: Map<Long, PurchaseOutcome>,
): Map<LocalDate, DayMark> {
    val out = HashMap<LocalDate, DayMark>()
    for (p in purchases) {
        val o = outcomes[p.id] ?: continue
        // 与购买绑定同一口径：本地时区的购买日期
        val date = Instant.fromEpochMilliseconds(p.purchasedAtEpochMs)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val mark = when {
            o.isPending -> DayMark.PENDING
            o.hasWon -> DayMark.WIN
            else -> DayMark.MISS
        }
        if (rankOf(mark) > rankOf(out[date] ?: DayMark.NONE)) out[date] = mark
    }
    return out
}

private fun rankOf(mark: DayMark): Int = when (mark) {
    DayMark.WIN -> 3
    DayMark.MISS -> 2
    DayMark.PENDING -> 1
    DayMark.NONE -> 0
}
