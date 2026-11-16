package com.ssqpick.rules

import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * 本地开奖库的健康检查结果。
 *
 * 只检查"数据本身是否完整"，不做任何走势判断：
 * - 期号在每一年内是否从 001 连续到当年最后一期；
 * - 每期是否 6 个不重复红球（1..33）+ 1 个蓝球（1..16）；
 * - 开奖日是否在周二/周四/周日；
 * - 一二等奖金额字段是否缺失（缺失时对奖只能报奖级，不能报金额）；
 * - 最新一期距今几天。
 */
data class DataQuality(
    val totalDraws: Int = 0,
    val earliestDate: LocalDate? = null,
    val latestDate: LocalDate? = null,
    val latestCode: String? = null,
    val missingCodeCount: Int = 0,
    val missingCodeSamples: List<String> = emptyList(),
    val invalidCodeCount: Int = 0,
    val invalidCodeSamples: List<String> = emptyList(),
    val missingPrizeCount: Int = 0,
    val offScheduleCount: Int = 0,
    val daysSinceLatest: Long? = null,
) {
    /** 没有缺期、坏数据、缺金额，且最新一期不超过 4 天。 */
    val healthy: Boolean
        get() = totalDraws > 0 &&
            missingCodeCount == 0 &&
            invalidCodeCount == 0 &&
            missingPrizeCount == 0 &&
            offScheduleCount == 0 &&
            (daysSinceLatest ?: 0L) <= STALE_DAYS

    companion object {
        const val STALE_DAYS = 4L
    }
}

/** [draws] 顺序不限；同 code 重复视为坏数据。 */
fun dataQualityOf(draws: List<Draw>, today: LocalDate): DataQuality {
    if (draws.isEmpty()) return DataQuality()

    val sorted = draws.sortedBy { it.code }
    val invalid = ArrayList<String>()
    val seenCodes = HashSet<String>()
    var missingPrize = 0
    var offSchedule = 0

    for (d in sorted) {
        val badNumbers = d.red.size != RED_COUNT ||
            d.red.toSet().size != RED_COUNT ||
            d.red.any { it !in 1..RED_TOTAL } ||
            d.blue !in 1..BLUE_TOTAL
        if (!seenCodes.add(d.code) || badNumbers) invalid += d.code
        if (d.prizeGrades.none { it.tier == PrizeTier.FIRST && it.amountPerTicket > 0 } &&
            d.prizeGrades.none { it.tier == PrizeTier.SECOND && it.amountPerTicket > 0 }
        ) {
            missingPrize++
        }
        // 双色球固定周二/周四/周日开奖（1=周一 … 7=周日）
        if (d.date.dayOfWeek.isoDayNumber !in setOf(2, 4, 7)) offSchedule++
    }

    val missing = ArrayList<String>()
    sorted.groupBy { it.code.take(4) }.forEach { (year, list) ->
        val issues = list.mapNotNull { it.code.drop(4).toIntOrNull() }.toSet()
        val max = issues.maxOrNull() ?: return@forEach
        for (n in 1..max) {
            if (n !in issues) missing += year + "%03d".format(n)
        }
    }

    val earliest = sorted.minOf { it.date }
    val latest = sorted.maxOf { it.date }
    val latestDraw = sorted.last()

    return DataQuality(
        totalDraws = draws.size,
        earliestDate = earliest,
        latestDate = latest,
        latestCode = latestDraw.code,
        missingCodeCount = missing.size,
        missingCodeSamples = missing.take(6),
        invalidCodeCount = invalid.size,
        invalidCodeSamples = invalid.take(6),
        missingPrizeCount = missingPrize,
        offScheduleCount = offSchedule,
        daysSinceLatest = (today.toEpochDays() - latest.toEpochDays()).coerceAtLeast(0),
    )
}

private const val RED_COUNT = 6
private const val RED_TOTAL = 33
private const val BLUE_TOTAL = 16
