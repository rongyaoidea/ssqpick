package com.ssqpick.model

import kotlinx.serialization.Serializable

/**
 * 一笔购买记录：某张票在某天被买了一次。
 *
 * 同一张票可以有多笔（守号逐期买）。绑定到哪一期不落库——
 * 由"购买日期之后的第一期开奖"在读取时推导（见 rules 的 PurchaseLedger），
 * 因为买票时下一期还没开、期号甚至还没生成。
 */
@Serializable
data class Purchase(
    val id: Long = 0L,
    val ticketId: Long,
    val purchasedAtEpochMs: Long,
    val costYuan: Long,
    /** 后台结算标记：只用于中奖通知去重，UI 不读。 */
    val settled: Boolean = false,
)
