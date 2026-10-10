package com.ssqpick.rules

import com.ssqpick.model.Draw
import kotlin.math.sqrt

/** 一对号码在同一期里共同出现的统计。 */
data class PairStat(val a: Int, val b: Int, val count: Int)

/** 同出/相克报告（红球两两共现，全库口径）。 */
data class CoOccurrenceReport(
    val draws: Int,
    val topPairs: List<PairStat>,
    val bottomPairs: List<PairStat>,
    /** 每对的理论期望次数。 */
    val expectedPerPair: Double,
    /** 单对计数的理论标准差。 */
    val sigma: Double,
)

/**
 * 红球两两共现统计。
 *
 * 固定一对号码在某一期同时出现的概率 = (6/33)×(5/32) ≈ 2.84%，
 * 2073 期的理论期望约 59 次、σ≈7.6——所谓"易同出/相克"都只是围绕
 * 这个期望的随机涨落。报告带上期望与 σ，UI 必须把这点讲清楚。
 */
fun coOccurrence(draws: List<Draw>, topN: Int = 8): CoOccurrenceReport {
    val counts = HashMap<Int, Int>()
    for (d in draws) {
        val reds = d.red
        for (i in reds.indices) {
            for (j in i + 1 until reds.size) {
                val a = minOf(reds[i], reds[j])
                val b = maxOf(reds[i], reds[j])
                val key = a * 100 + b
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
    }
    val all = ArrayList<PairStat>(counts.size)
    for ((key, count) in counts) {
        all += PairStat(key / 100, key % 100, count)
    }
    val p = 6.0 / 33.0 * 5.0 / 32.0
    return CoOccurrenceReport(
        draws = draws.size,
        topPairs = all.sortedWith(
            compareByDescending<PairStat> { it.count }.thenBy { it.a }.thenBy { it.b },
        ).take(topN),
        bottomPairs = all.sortedWith(
            compareBy<PairStat> { it.count }.thenBy { it.a }.thenBy { it.b },
        ).take(topN),
        expectedPerPair = draws.size * p,
        sigma = sqrt(draws.size * p * (1 - p)),
    )
}

/** 跟随统计的一项：下一期某号出现了多少次。 */
data class FollowStat(val number: Int, val count: Int)

/** 历史跟随报告：目标号出现后，下一期各号的出现分布。 */
data class FollowReport(
    val target: Int,
    val isBlue: Boolean,
    /** 目标号出现且有"下一期"可统计的次数。 */
    val appearances: Int,
    val followers: List<FollowStat>,
    /** 下一期每个号的理论期望次数。 */
    val expectedPerNumber: Double,
)

/**
 * 历史跟随：目标号出现的每一期，统计其**下一期**的红/蓝球出现次数。
 *
 * 传入顺序不限（内部按日期升序）。最后一期没有"下一期"，不计入。
 * 红球每号理论期望 = 出现次数×6/33；蓝球 = 出现次数×1/16——
 * 同样只是描述，不是"必跟号"。
 */
fun followersOf(draws: List<Draw>, target: Int, isBlue: Boolean, topN: Int = 8): FollowReport {
    val asc = draws.sortedBy { it.date }
    val counts = HashMap<Int, Int>()
    var appearances = 0
    for (i in 0 until asc.size - 1) {
        val cur = asc[i]
        val has = if (isBlue) cur.blue == target else target in cur.red
        if (!has) continue
        appearances++
        val next = asc[i + 1]
        if (isBlue) {
            counts[next.blue] = (counts[next.blue] ?: 0) + 1
        } else {
            for (n in next.red) counts[n] = (counts[n] ?: 0) + 1
        }
    }
    val followers = counts.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .take(topN)
        .map { FollowStat(it.key, it.value) }
    return FollowReport(
        target = target,
        isBlue = isBlue,
        appearances = appearances,
        followers = followers,
        expectedPerNumber = appearances * (if (isBlue) 1.0 / 16.0 else 6.0 / 33.0),
    )
}

/** 一期历史对照：某一期与目标期的重合情况。 */
data class PeriodMatch(val draw: Draw, val redOverlap: Int, val blueMatch: Boolean)

/**
 * 历年同期：期号后缀相同、年份不同的历史开奖（按年倒序，最多 [maxYears] 年）。
 * 期号形如 2026116，后缀 = "116"。
 */
fun samePeriodOf(draws: List<Draw>, target: Draw, maxYears: Int = 5): List<PeriodMatch> {
    if (target.code.length <= 4) return emptyList()
    val suffix = target.code.drop(4)
    val year = target.code.take(4)
    return draws.asSequence()
        .filter { it.code.length > 4 && it.code.drop(4) == suffix && it.code.take(4) != year }
        .sortedByDescending { it.code }
        .take(maxYears)
        .map { match(it, target) }
        .toList()
}

/** 往年的今天：月日相同、年份不同的历史开奖（按日期倒序，最多 [maxYears] 条）。 */
fun onThisDayOf(draws: List<Draw>, target: Draw, maxYears: Int = 3): List<PeriodMatch> {
    val month = target.date.monthNumber
    val day = target.date.dayOfMonth
    return draws.asSequence()
        .filter {
            it.date.year != target.date.year &&
                it.date.monthNumber == month &&
                it.date.dayOfMonth == day
        }
        .sortedByDescending { it.date }
        .take(maxYears)
        .map { match(it, target) }
        .toList()
}

private fun match(d: Draw, target: Draw): PeriodMatch = PeriodMatch(d, d.red.count { it in target.red }, d.blue == target.blue)
