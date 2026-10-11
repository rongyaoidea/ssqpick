package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Purchase
import com.ssqpick.model.Ticket

/**
 * 购买记录的结算口径。
 *
 * 一笔购买绑定到"购买时刻之后的第一期开奖"；具体规则（含开奖当晚 21:15
 * 之后算下一期）在 [boundDrawFor]，与选号的期号绑定共用同一份实现。
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
fun bindDraw(purchase: Purchase, drawsAsc: List<Draw>): Draw? = boundDrawFor(purchase.purchasedAtEpochMs, drawsAsc)

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

/**
 * 一张票的购买汇总，票行展示用。
 *
 * [outcomes] 为购买 id → 结算结果（由 ViewModel 的共享结算流一次算好），
 * 这里不再自己逐笔 outcomeOf。
 */
fun summaryOf(purchases: List<Purchase>, outcomes: Map<Long, PurchaseOutcome>): TicketPurchaseSummary {
    if (purchases.isEmpty()) return TicketPurchaseSummary(0, 0, 0L, 0L, null)
    val ordered = purchases.sortedBy { it.purchasedAtEpochMs }
    var cost = 0L
    var prize = 0L
    var pending = 0
    var latest: PurchaseOutcome? = null
    for (p in ordered) {
        cost += p.costYuan
        val o = outcomes[p.id] ?: continue
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
    /** 时间线里中奖的那几笔（水位线打点用）。 */
    val winIndices: Set<Int> = emptySet(),
    /** 真账奖级分布：奖级 → 中奖笔数。 */
    val tierCounts: Map<PrizeTier, Long> = emptyMap(),
    /** 真账各奖级金额合计（浮动奖无公告时为 0）。 */
    val tierAmounts: Map<PrizeTier, Long> = emptyMap(),
) {
    val netYuan: Long get() = totalPrizeYuan - totalCostYuan
}

/**
 * 全部购买的真实账本：投入、奖金、累计净收益时间线。
 *
 * [outcomes] 为购买 id → 结算结果；孤儿购买（票已删）不在表里，只计成本。
 */
fun ledgerOf(
    purchases: List<Purchase>,
    outcomes: Map<Long, PurchaseOutcome>,
): PurchaseLedger {
    val ordered = purchases.sortedBy { it.purchasedAtEpochMs }
    var cost = 0L
    var prize = 0L
    var pending = 0
    val timeline = ArrayList<Float>(ordered.size)
    val winIndices = HashSet<Int>()
    val tierCounts = HashMap<PrizeTier, Long>()
    val tierAmounts = HashMap<PrizeTier, Long>()
    ordered.forEachIndexed { index, p ->
        cost += p.costYuan
        val o = outcomes[p.id]
        if (o != null) {
            if (o.isPending) {
                pending++
            } else {
                prize += o.amountYuan
                o.topTier?.let { tier ->
                    winIndices += index
                    tierCounts[tier] = (tierCounts[tier] ?: 0L) + 1L
                    tierAmounts[tier] = (tierAmounts[tier] ?: 0L) + o.amountYuan
                }
            }
        }
        timeline += (prize - cost).toFloat()
    }
    return PurchaseLedger(
        count = ordered.size,
        pendingCount = pending,
        totalCostYuan = cost,
        totalPrizeYuan = prize,
        cumulativeNet = timeline,
        winIndices = winIndices,
        tierCounts = tierCounts,
        tierAmounts = tierAmounts,
    )
}
