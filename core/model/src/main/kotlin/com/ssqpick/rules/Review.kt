package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// 复盘页的数据来源。全部是纯函数：输入购买/票/开奖，输出可直接上屏的汇总。
// 口径与 [ledgerOf] 同源（一笔购买绑到它自己那期），复盘页各块之间不会打架。

/** 一个月的账：笔数、投入、奖金。 */
data class MonthLedger(
    val year: Int,
    val month: Int,
    val count: Int,
    val costYuan: Long,
    val prizeYuan: Long,
) {
    val netYuan: Long get() = prizeYuan - costYuan
}

/** 按月汇总购买（本地时区）。返回按时间倒序，最多 [maxMonths] 个月。 */
fun monthlyLedger(
    purchases: List<Purchase>,
    ticketsById: Map<Long, Ticket>,
    drawsAsc: List<Draw>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    maxMonths: Int = 12,
): List<MonthLedger> {
    if (purchases.isEmpty()) return emptyList()
    class Acc {
        var count = 0
        var cost = 0L
        var prize = 0L
    }

    val acc = HashMap<Int, Acc>()
    for (p in purchases) {
        val date = Instant.fromEpochMilliseconds(p.purchasedAtEpochMs).toLocalDateTime(zone).date
        val key = date.year * 12 + (date.monthNumber - 1)
        val a = acc.getOrPut(key) { Acc() }
        a.count++
        a.cost += p.costYuan
        ticketsById[p.ticketId]?.let { a.prize += outcomeOf(p, it, drawsAsc).amountYuan }
    }
    return acc.entries
        .sortedByDescending { it.key }
        .take(maxMonths)
        .map { (key, a) -> MonthLedger(key / 12, key % 12 + 1, a.count, a.cost, a.prize) }
}

/** 号码覆盖：每个号被多少张存票选中过。 */
data class NumberCoverage(
    val redCounts: Map<Int, Int>,
    val blueCounts: Map<Int, Int>,
    val ticketCount: Int,
) {
    val maxRed: Int get() = redCounts.values.maxOrNull() ?: 0
    val maxBlue: Int get() = blueCounts.values.maxOrNull() ?: 0

    /** 一次都没买过的号。 */
    val neverRed: List<Int> get() = (1..TicketRules.RED_TOTAL).filter { (redCounts[it] ?: 0) == 0 }
    val neverBlue: List<Int> get() = (1..TicketRules.BLUE_TOTAL).filter { (blueCounts[it] ?: 0) == 0 }
}

fun numberCoverage(tickets: List<Ticket>): NumberCoverage {
    val red = HashMap<Int, Int>()
    val blue = HashMap<Int, Int>()
    for (t in tickets) {
        for (n in t.draft.redPool) red[n] = (red[n] ?: 0) + 1
        for (n in t.draft.bluePool) blue[n] = (blue[n] ?: 0) + 1
    }
    return NumberCoverage(red, blue, tickets.size)
}

/** 一组守号/换号的战绩。 */
data class HoldBucket(
    val tickets: Int,
    val bets: Int,
    val costYuan: Long,
    val prizeYuan: Long,
) {
    val roi: Double get() = if (costYuan > 0) prizeYuan.toDouble() / costYuan else 0.0
}

/** 守号 vs 换号：同一组号买多期 vs 每期换号，两组的真实战绩。 */
data class HoldSwitchReport(val hold: HoldBucket, val switch: HoldBucket)

/**
 * 按票分组算两桶：
 * - **守号**：同一张票买了 ≥2 笔（同一组号逐期买）
 * - **换号**：只买过 1 笔的票
 *
 * 两组的期望其实一样（每期是否命中只取决于当期开奖），差异来自样本量。
 */
fun holdSwitchReport(
    purchases: List<Purchase>,
    ticketsById: Map<Long, Ticket>,
    drawsAsc: List<Draw>,
): HoldSwitchReport {
    var holdTickets = 0
    var holdBets = 0
    var holdCost = 0L
    var holdPrize = 0L
    var swTickets = 0
    var swBets = 0
    var swCost = 0L
    var swPrize = 0L
    for ((ticketId, ps) in purchases.groupBy { it.ticketId }) {
        val ticket = ticketsById[ticketId] ?: continue
        var cost = 0L
        var prize = 0L
        for (p in ps) {
            cost += p.costYuan
            prize += outcomeOf(p, ticket, drawsAsc).amountYuan
        }
        if (ps.size >= 2) {
            holdTickets++
            holdBets += ps.size
            holdCost += cost
            holdPrize += prize
        } else {
            swTickets++
            swBets += ps.size
            swCost += cost
            swPrize += prize
        }
    }
    return HoldSwitchReport(
        hold = HoldBucket(holdTickets, holdBets, holdCost, holdPrize),
        switch = HoldBucket(swTickets, swBets, swCost, swPrize),
    )
}

/** 购票日历里某一天的明细。 */
data class PurchaseDayEntry(
    val ticketId: Long,
    val costYuan: Long,
    /** 绑定的期号；还没开奖时为 null。 */
    val drawCode: String?,
    /** 中奖奖级；未中奖/未开奖为 null。 */
    val tierLabel: String?,
    val pending: Boolean,
)

/** 购买日期（本地时区）→ 当天记录。 */
fun dayEntriesOf(
    purchases: List<Purchase>,
    ticketsById: Map<Long, Ticket>,
    drawsAsc: List<Draw>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Map<LocalDate, List<PurchaseDayEntry>> {
    val out = HashMap<LocalDate, MutableList<PurchaseDayEntry>>()
    for (p in purchases) {
        val ticket = ticketsById[p.ticketId] ?: continue
        val date = Instant.fromEpochMilliseconds(p.purchasedAtEpochMs).toLocalDateTime(zone).date
        val o = outcomeOf(p, ticket, drawsAsc)
        out.getOrPut(date) { mutableListOf() } += PurchaseDayEntry(
            ticketId = p.ticketId,
            costYuan = p.costYuan,
            drawCode = o.draw?.code,
            tierLabel = o.topTier?.label,
            pending = o.isPending,
        )
    }
    return out
}
