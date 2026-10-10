package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 购买记录的结算口径。
 *
 * 一笔购买绑定到"购买日期（本地时区）当天或之后的第一期开奖"——
 * 买票时下一期还没开，期号不可知，所以绑定是读取时推导的纯函数，
 * 不落库。已知误差：开奖当晚 21:15 之后记录的购买会绑到当晚那期
 * （实际想买的是下一期），按周中位使用场景概率可接受。
 */
data class PurchaseOutcome(
    val purchase: Purchase,
    /** 绑定的开奖；null 表示还没开奖。 */
    val draw: Draw?,
    val amountYuan: Long,
    val topTier: PrizeTier?,
) {
    val isPending: Boolean get() = draw == null
    val hasWon: Boolean get() = topTier != null
}

/** [drawsAsc] 必须按日期升序。 */
fun bindDraw(purchase: Purchase, drawsAsc: List<Draw>): Draw? {
    val date = Instant.fromEpochMilliseconds(purchase.purchasedAtEpochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return drawsAsc.firstOrNull { it.date >= date }
}

fun outcomeOf(purchase: Purchase, ticket: Ticket, drawsAsc: List<Draw>): PurchaseOutcome {
    val draw = bindDraw(purchase, drawsAsc)
        ?: return PurchaseOutcome(purchase, null, 0L, null)
    val (amount, breakdown) = TicketRules.evaluate(ticket.draft, draw)
    return PurchaseOutcome(purchase, draw, amount, breakdown.keys.maxByOrNull { it.rank })
}

/** 一张票的购买汇总，票行展示用。 */
data class TicketPurchaseSummary(
    val count: Int,
    val pendingCount: Int,
    val costYuan: Long,
    val prizeYuan: Long,
    /** 最近一笔购买的结果；无购买时为 null。 */
    val latest: PurchaseOutcome?,
)

fun summaryOf(ticket: Ticket, purchases: List<Purchase>, drawsAsc: List<Draw>): TicketPurchaseSummary {
    if (purchases.isEmpty()) return TicketPurchaseSummary(0, 0, 0L, 0L, null)
    val ordered = purchases.sortedBy { it.purchasedAtEpochMs }
    var cost = 0L
    var prize = 0L
    var pending = 0
    var latest: PurchaseOutcome? = null
    for (p in ordered) {
        cost += p.costYuan
        val o = outcomeOf(p, ticket, drawsAsc)
        if (o.isPending) pending++ else prize += o.amountYuan
        latest = o
    }
    return TicketPurchaseSummary(ordered.size, pending, cost, prize, latest)
}

/** 全部购买的真实账本：投入、奖金、累计净收益时间线。 */
data class PurchaseLedger(
    val count: Int,
    val pendingCount: Int,
    val totalCostYuan: Long,
    val totalPrizeYuan: Long,
    /** 按购买时间排序的累计净收益（元）：奖金 − 投入。折线直接用。 */
    val cumulativeNet: List<Float>,
) {
    val netYuan: Long get() = totalPrizeYuan - totalCostYuan
}

fun ledgerOf(
    purchases: List<Purchase>,
    ticketsById: Map<Long, Ticket>,
    drawsAsc: List<Draw>,
): PurchaseLedger {
    val ordered = purchases.sortedBy { it.purchasedAtEpochMs }
    var cost = 0L
    var prize = 0L
    var pending = 0
    val timeline = ArrayList<Float>(ordered.size)
    for (p in ordered) {
        cost += p.costYuan
        val ticket = ticketsById[p.ticketId]
        if (ticket != null) {
            val o = outcomeOf(p, ticket, drawsAsc)
            if (o.isPending) pending++ else prize += o.amountYuan
        }
        timeline += (prize - cost).toFloat()
    }
    return PurchaseLedger(ordered.size, pending, cost, prize, timeline)
}
