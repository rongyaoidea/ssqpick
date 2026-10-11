package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.Ticket
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

// 选号与期号的绑定。
//
// 每一注号码都对应"保存时刻之后的第一期开奖"——和购买记录同一口径：
// 开奖当晚 21:15（DrawSchedule.DRAW_TIME）之后保存的算下一期。
// 绑定是读取时推导的纯函数，不落库：口径只改这一处，历史数据也不会
// 带着过期的期号。
//
// 对奖时只有绑定期号与目标期一致的票才参与核对——存给下一期的号码，
// 不会拿上一期的开奖结果去"对"。

/** 保存/购买时刻对应的目标开奖日（本地时区）。 */
fun targetDrawDate(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate {
    val at = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    return if (at.time >= DrawSchedule.DRAW_TIME) at.date.plus(1, DateTimeUnit.DAY) else at.date
}

/**
 * [drawsAsc] 必须按日期升序；返回目标日当天或之后的第一期开奖。
 * 用二分查找，购买/选号的每次结算都调用它。
 */
fun boundDrawFor(
    epochMs: Long,
    drawsAsc: List<Draw>,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Draw? {
    val threshold = targetDrawDate(epochMs, zone)
    var lo = 0
    var hi = drawsAsc.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (drawsAsc[mid].date < threshold) lo = mid + 1 else hi = mid
    }
    return drawsAsc.getOrNull(lo)
}

/** 一张票绑定的开奖期；还没开奖时为 null。 */
fun bindTicketDraw(ticket: Ticket, drawsAsc: List<Draw>): Draw? = boundDrawFor(ticket.createdAtEpochMs, drawsAsc)

/** 把票按"是否属于 [target] 这一期"分成（本期，其他期）。 */
fun ticketsForDraw(
    tickets: List<Ticket>,
    target: Draw,
    drawsAsc: List<Draw>,
): Pair<List<Ticket>, List<Ticket>> = tickets.partition { bindTicketDraw(it, drawsAsc)?.code == target.code }

/**
 * 期号文案：库里已有那期就写"第 X 期"，否则按开奖日历写"X月X日开奖"。
 * 不猜期号——下一期的号码在开奖前并不存在。
 */
fun targetLabel(epochMs: Long, drawsAsc: List<Draw>): String {
    boundDrawFor(epochMs, drawsAsc)?.let { return "第 ${it.code} 期" }
    val at = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val next = DrawSchedule.nextDrawAfter(at).date
    return "${next.monthNumber}月${next.dayOfMonth}日开奖"
}
