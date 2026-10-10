package com.ssqpick.store.mapper

import com.ssqpick.model.Draw
import com.ssqpick.model.DrawPrizeGrade
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Purchase
import com.ssqpick.model.TicketDraft
import kotlinx.datetime.LocalDate
import com.ssqpick.store.db.Draw as DrawRow
import com.ssqpick.store.db.Purchase as PurchaseRow
import com.ssqpick.store.db.Ticket as TicketRow

/**
 * 号码集合 ⇄ 逗号分隔字符串。
 *
 * 刻意不用 SQLDelight 的 ColumnAdapter：显式编解码更直白，
 * 也避免"实体是 List<Int>、insert 却收 String"这类不对称的坑。
 */
fun List<Int>.encode(): String = sorted().joinToString(",")

fun String.decodeNumbers(): List<Int> = if (isBlank()) emptyList() else split(',').mapNotNull { it.trim().toIntOrNull() }

// ---------------------------------------------------------------- Draw

fun DrawRow.toDraw(): Draw = Draw(
    code = code,
    date = LocalDate.parse(date),
    red = red.decodeNumbers(),
    blue = blue.toInt(),
    sales = sales,
    poolAmount = pool_amount,
    prizeGrades = buildList {
        // 库里只为了一二等奖的实付额各留一列；固定奖由奖级表推导
        if (first_prize_amount > 0) add(DrawPrizeGrade(PrizeTier.FIRST, 0, first_prize_amount))
        if (second_prize_amount > 0) add(DrawPrizeGrade(PrizeTier.SECOND, 0, second_prize_amount))
    },
)

// ---------------------------------------------------------------- Ticket

fun TicketRow.toDraft(): TicketDraft = TicketDraft(
    redBold = red_bold.decodeNumbers().toSet(),
    redPicked = red_picked.decodeNumbers().toSet(),
    blueBold = blue_bold.decodeNumbers().toSet(),
    bluePicked = blue_picked.decodeNumbers().toSet(),
)

// ---------------------------------------------------------------- Purchase

fun PurchaseRow.toPurchase(): Purchase = Purchase(
    id = id,
    ticketId = ticket_id,
    purchasedAtEpochMs = purchased_at,
    costYuan = cost_yuan,
    settled = settled == 1L,
)
