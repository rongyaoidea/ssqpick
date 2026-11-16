package com.ssqpick.feature.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssqpick.model.BackupBundle
import com.ssqpick.model.BackupCodec
import com.ssqpick.model.BackupPurchase
import com.ssqpick.model.BackupTicket
import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import com.ssqpick.rules.CoOccurrenceReport
import com.ssqpick.rules.DataQuality
import com.ssqpick.rules.FollowReport
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.coOccurrence
import com.ssqpick.rules.computeShape
import com.ssqpick.rules.computeStats
import com.ssqpick.rules.dataQualityOf
import com.ssqpick.rules.followersOf
import com.ssqpick.store.DrawRepository
import com.ssqpick.store.PurchaseRepository
import com.ssqpick.store.SettingsStorage
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 历史跟随的目标：红球或蓝球里的某个号。 */
data class FollowTarget(val number: Int = 1, val isBlue: Boolean = false)

/** 和值走势数据：最近若干期的红球和值 + 均值线。 */
data class TrendData(
    val sums: List<Float>,
    val mean: Float,
) {
    val min: Float get() = sums.min()
    val max: Float get() = sums.max()
}

/** 和值走势：取最新 [window] 期，按时间正序（库按日期倒序存，先 take 再反转）。 */
fun trendOf(draws: List<Draw>, window: Int): TrendData? {
    val latest = draws.take(window).reversed()
    if (latest.isEmpty()) return null
    val sums = latest.map { it.red.sum().toFloat() }
    return TrendData(sums = sums, mean = sums.average().toFloat())
}

/** 数据页：开奖日程、冷热、形态、和值走势、模拟实验室、往期开奖。 */
class DataViewModel(
    private val drawRepository: DrawRepository,
    private val settingsStore: SettingsStorage,
    /** 备份/恢复直接读写这两个仓库；默认空实现便于旧测试。 */
    private val ticketRepository: TicketRepository? = null,
    private val purchaseRepository: PurchaseRepository? = null,
    /** 派生统计的计算调度器；测试注入虚拟调度器，生产用 Default。 */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /**
     * 开奖库共享订阅：stats/shape/trend 三条派生流共用一份，
     * 不然每次库变更要跑 3 遍查询。
     */
    private val draws: SharedFlow<List<Draw>> = drawRepository.observeAll()
        // replay=1：晚加入的派生流能立刻拿到最近一次库快照
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val newest: StateFlow<Draw?> = drawRepository.observeNewest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 往期开奖全量列表（时间倒序），数据页末尾的列表用。 */
    val drawList: StateFlow<List<Draw>> = draws
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 同出/相克：全库红球两两共现。 */
    val coOccurrence: StateFlow<CoOccurrenceReport?> = draws
        .map { if (it.isEmpty()) null else coOccurrence(it) }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 历史跟随的当前目标（默认红球 01）。 */
    private val _followTarget = MutableStateFlow(FollowTarget())
    val followTarget: StateFlow<FollowTarget> = _followTarget.asStateFlow()

    fun selectFollowTarget(number: Int, isBlue: Boolean) {
        _followTarget.value = FollowTarget(number, isBlue)
    }

    /** 历史跟随：目标号出现后的下一期分布。 */
    val follow: StateFlow<FollowReport?> = combine(draws, _followTarget) { ds, t ->
        if (ds.isEmpty()) null else followersOf(ds, t.number, t.isBlue)
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notifyOn = MutableStateFlow(settingsStore.drawResultNotify)
    val notifyOn: StateFlow<Boolean> = _notifyOn.asStateFlow()

    /**
     * 设置"开奖结果通知"订阅（默认关）。
     *
     * 只有权限与系统通知开关都就绪时才会被置 true——调用方（DataScreen）先确认再调，
     * 避免出现"界面显示已订阅、实际永远收不到"的假订阅。
     */
    fun setDrawNotify(on: Boolean) {
        settingsStore.drawResultNotify = on
        _notifyOn.value = on
    }

    val stats: StateFlow<NumberStats?> = draws
        .map(::computeStats)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shape: StateFlow<ShapeStats?> = draws
        .map(::computeShape)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 和值走势：取最新 [TREND_WINDOW] 期，按时间正序。库按日期倒序存，先 take 再反转。 */
    val trend: StateFlow<TrendData?> = draws
        .map { trendOf(it, TREND_WINDOW) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 开奖库健康检查：缺期 / 号码非法 / 缺奖金字段 / 开奖日异常 / 数据过旧。 */
    val quality: StateFlow<DataQuality?> = draws
        .map { list ->
            if (list.isEmpty()) {
                null
            } else {
                val today = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                dataQualityOf(list, today)
            }
        }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 导出自己的号码与购买记录（不含开奖库，开奖数据可重新同步）。 */
    suspend fun buildBackupJson(): String {
        val tickets = ticketRepository?.all().orEmpty()
        val purchases = purchaseRepository?.all().orEmpty()
        val indexById = tickets.mapIndexed { i, t -> t.id to i }.toMap()
        val bundle = BackupBundle(
            exportedAtEpochMs = System.currentTimeMillis(),
            tickets = tickets.map { t ->
                BackupTicket(
                    createdAtEpochMs = t.createdAtEpochMs,
                    label = t.label,
                    redBold = t.draft.redBold.sorted(),
                    redPicked = t.draft.redPicked.sorted(),
                    blueBold = t.draft.blueBold.sorted(),
                    bluePicked = t.draft.bluePicked.sorted(),
                )
            },
            purchases = purchases.mapNotNull { p ->
                indexById[p.ticketId]?.let { index ->
                    BackupPurchase(
                        ticketIndex = index,
                        purchasedAtEpochMs = p.purchasedAtEpochMs,
                        costYuan = p.costYuan,
                        settled = p.settled,
                    )
                }
            },
        )
        return BackupCodec.encode(bundle)
    }

    /**
     * 合并式恢复：只新增，不覆盖、不清空。
     * 同一张票（创建时间 + 号码相同）与同一笔购买（票 + 时间相同）会跳过。
     */
    suspend fun restoreBackupJson(text: String): String {
        if (text.length > MAX_BACKUP_CHARS) return "恢复失败：文件过大"
        val bundle = try {
            BackupCodec.decode(text)
        } catch (e: IllegalArgumentException) {
            return "恢复失败：${e.message ?: "备份格式不正确"}"
        }
        val ticketRepo = ticketRepository ?: return "恢复失败：存储未就绪"
        val purchaseRepo = purchaseRepository ?: return "恢复失败：存储未就绪"

        val existingTickets = ticketRepo.all()
        val idByKey = existingTickets
            .associate { (it.createdAtEpochMs to it.draft) to it.id }
            .toMutableMap()
        val idMap = HashMap<Int, Long>()
        var addedTickets = 0
        var skippedTickets = 0
        bundle.tickets.forEachIndexed { index, bt ->
            val draft = bt.toDraft()
            val key = bt.createdAtEpochMs to draft
            val knownId = idByKey[key]
            if (knownId != null) {
                idMap[index] = knownId
                skippedTickets++
            } else {
                val id = ticketRepo.import(
                    Ticket(
                        createdAtEpochMs = bt.createdAtEpochMs,
                        label = bt.label?.take(40)?.trim()?.ifBlank { null },
                        draft = draft,
                    ),
                )
                idMap[index] = id
                idByKey[key] = id
                addedTickets++
            }
        }

        val seenPurchases = purchaseRepo.all()
            .map { it.ticketId to it.purchasedAtEpochMs }
            .toHashSet()
        var addedPurchases = 0
        var skippedPurchases = 0
        bundle.purchases.forEach { bp ->
            val ticketId = idMap[bp.ticketIndex] ?: return@forEach
            if (!seenPurchases.add(ticketId to bp.purchasedAtEpochMs)) {
                skippedPurchases++
            } else {
                purchaseRepo.import(ticketId, bp.purchasedAtEpochMs, bp.costYuan, bp.settled)
                addedPurchases++
            }
        }

        return "恢复完成：新增 $addedTickets 张号码、$addedPurchases 笔购买；" +
            "跳过 $skippedTickets 张重复号码、$skippedPurchases 笔重复购买。"
    }

    companion object {
        private const val TREND_WINDOW = 100
        private const val MAX_BACKUP_CHARS = 20_000_000
    }
}
