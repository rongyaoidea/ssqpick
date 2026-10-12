package com.ssqpick.feature.ticket

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.sketch.SketchBarRow
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchLineChart
import com.ssqpick.designsystem.sketch.SketchMetaRow
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchRangeMarker
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.SketchSheet
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketKind
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.DraftFingerprint
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.HoldBucket
import com.ssqpick.rules.HoldSwitchReport
import com.ssqpick.rules.MonthLedger
import com.ssqpick.rules.NumberCoverage
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.PurchaseDayEntry
import com.ssqpick.rules.PurchaseLedger
import com.ssqpick.rules.SmsCommand
import com.ssqpick.rules.TicketPurchaseSummary
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.TicketStatus
import com.ssqpick.rules.theoreticalBlueHitRate
import com.ssqpick.rules.theoreticalRedDistribution
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 复盘页：只读自己的记录。
 *
 * - 时间范围（全部/今年/近90天）过滤账本与月度账单
 * - 我的战绩（真实账本）、月度账单、号码覆盖、守号 vs 换号、待开奖、购票日历
 *
 * 选号指纹与历史回测不在这里：它们绑定"正在选的那组号"，留在选号页。
 */
@Composable
fun ReviewScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val range by viewModel.reviewRange.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val bill by viewModel.monthlyBill.collectAsStateWithLifecycle()
    val coverage by viewModel.coverage.collectAsStateWithLifecycle()
    val holdSwitch by viewModel.holdSwitch.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val calendarMarks by viewModel.calendarMarks.collectAsStateWithLifecycle()
    val dayEntries by viewModel.dayEntries.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReviewRange.entries.forEach { r ->
                        SketchMiniButton(
                            label = r.label,
                            seed = "review-range-${r.name}",
                            onClick = { viewModel.setReviewRange(r) },
                            filled = r == range,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                PnlCard(ledger)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                MonthlyBillCard(bill)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                CoverageCard(coverage, stats)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                HoldSwitchCard(holdSwitch)
                Spacer(Modifier.height(14.dp))
            }
        }
        if (pending.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    PendingCard(pending)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                CalendarCard(marks = calendarMarks, dayEntries = dayEntries)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 月度账单：按月汇总投入与奖金，时间倒序。 */
@Composable
private fun MonthlyBillCard(bill: List<MonthLedger>) {
    SketchCard(seed = "review-months", tilt = -0.2f) {
        SketchSectionTitle("月度账单")
        Spacer(Modifier.height(8.dp))
        if (bill.isEmpty()) {
            Text(
                "记录购买后，这里按月汇总投入与奖金。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        bill.forEach { m ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%d年%d月 · %d 笔".format(m.year, m.month, m.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.Ink,
                )
                Text(
                    "投入 %,d · 奖金 %,d · 净 %+,d".format(m.costYuan, m.prizeYuan, m.netYuan),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.netYuan >= 0) SketchPalette.InkSoft else SketchPalette.RedInk,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "按购买月份（本地时区），最多 12 个月；奖金只算已开奖且中奖的笔。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}

/** 号码覆盖：买过的号越深；顺带对照"我买过的号"和全体历史频次。 */
@Composable
private fun CoverageCard(cov: NumberCoverage?, stats: NumberStats?) {
    SketchCard(seed = "review-coverage", tilt = 0.25f) {
        SketchSectionTitle("号码覆盖")
        Spacer(Modifier.height(8.dp))
        if (cov == null) {
            Text(
                "存几注号码后，这里看你存过的号。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        Text("红球 · 颜色越深存得越多", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
        Spacer(Modifier.height(4.dp))
        CoverageGrid(cov.redCounts, cov.maxRed, 33, isBlue = false)
        Spacer(Modifier.height(8.dp))
        Text("蓝球", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
        Spacer(Modifier.height(4.dp))
        CoverageGrid(cov.blueCounts, cov.maxBlue, 16, isBlue = true)
        Spacer(Modifier.height(8.dp))
        if (cov.neverRed.isNotEmpty()) {
            Text(
                "从没存过的红球：" + cov.neverRed.take(12).joinToString(" ") { "%02d".format(it) } +
                    if (cov.neverRed.size > 12) " …" else "",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        if (cov.neverBlue.isNotEmpty()) {
            Text(
                "从没存过的蓝球：" + cov.neverBlue.take(12).joinToString(" ") { "%02d".format(it) },
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        // 我买过的号 vs 历史频次：冷热不改变下一期概率
        val picked = cov.redCounts.filter { it.value > 0 }.keys
        if (stats != null && stats.totalDraws > 0 && picked.isNotEmpty()) {
            val mine = picked.map { stats.redFrequency[it] ?: 0 }.average()
            val all = (1..33).map { stats.redFrequency[it] ?: 0 }.average()
            Spacer(Modifier.height(6.dp))
            Text(
                "你存过的号历史平均出现 %.1f 次，全体平均 %.1f 次 —— 冷热不改变下一期概率。"
                    .format(mine, all),
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
    }
}

/** 号码覆盖网格：复盘页与"已保存的号码"的下一期覆盖共用。 */
@Composable
internal fun CoverageGrid(counts: Map<Int, Int>, maxCount: Int, total: Int, isBlue: Boolean) {
    val base = if (isBlue) SketchPalette.BlueBall else SketchPalette.RedBall
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (isBlue) 8 else 11,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        (1..total).forEach { n ->
            val c = counts[n] ?: 0
            val frac = if (maxCount > 0) c.toFloat() / maxCount else 0f
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(base.copy(alpha = if (c == 0) 0.06f else 0.28f + 0.62f * frac)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(n),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (c == 0) SketchPalette.InkFaint else SketchPalette.Ink,
                )
            }
        }
    }
}

/** 守号 vs 换号：两组真实战绩。期望一样，差别只是样本量。 */
@Composable
private fun HoldSwitchCard(r: HoldSwitchReport?) {
    SketchCard(seed = "review-hold", tilt = -0.25f) {
        SketchSectionTitle("守号 vs 换号")
        Spacer(Modifier.height(8.dp))
        if (r == null) {
            Text(
                "记录购买后，这里对比「同一组号买多期」和「每期换号」的真实战绩。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        HoldRow("守号（同一组号买多期）", r.hold)
        HoldRow("换号（每期换一组）", r.switch)
        Spacer(Modifier.height(2.dp))
        Text(
            "两组的期望其实一样：每期是否命中只取决于当期开奖。差异来自样本量，不是守号更灵。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}

@Composable
private fun HoldRow(label: String, b: HoldBucket) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SketchPalette.Ink)
        Text(
            if (b.bets == 0) {
                "还没有"
            } else {
                "%d 张票 · %d 笔 · 投入 %,d 元 · 奖金 %,d 元 · 回报率 %.0f%%"
                    .format(b.tickets, b.bets, b.costYuan, b.prizeYuan, b.roi * 100)
            },
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** 待开奖：哪几张票、绑哪期、多少钱。开奖后自动结算。 */
@Composable
private fun PendingCard(pending: List<PendingEntry>) {
    SketchCard(seed = "review-pending", tilt = 0.2f) {
        SketchSectionTitle("待开奖")
        Spacer(Modifier.height(8.dp))
        Text(
            "共 ${pending.size} 笔 · 合计 %,d 元".format(pending.sumOf { it.costYuan }),
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        Spacer(Modifier.height(4.dp))
        pending.take(3).forEach { e ->
            Text(
                "号码 #${e.ticketId} · ${e.costYuan} 元 · 对应 ${e.periodLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        if (pending.size > 3) {
            Text("…还有 ${pending.size - 3} 笔", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkFaint)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "开奖后会自动结算；开启通知才会收到中奖提醒。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}

/** 我的战绩：记录购买后才有，是真账不是假设。 */
@Composable
private fun PnlCard(ledger: PurchaseLedger) {
    SketchCard(seed = "ticket-pnl", tilt = 0.2f) {
        SketchSectionTitle("我的战绩")
        Spacer(Modifier.height(8.dp))
        if (ledger.count == 0) {
            Text(
                "在号码上点「记录购买」，这里会记真实的投入、奖金与累计净收益。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        SketchMetaRow(label = "投入", value = "%,d 元".format(ledger.totalCostYuan))
        SketchMetaRow(label = "奖金", value = "%,d 元".format(ledger.totalPrizeYuan))
        SketchMetaRow(
            label = "净收益",
            value = "%+,d 元".format(ledger.netYuan),
            valueColor = if (ledger.netYuan >= 0) SketchPalette.Ink else SketchPalette.RedInk,
        )
        Text(
            when {
                ledger.netYuan < 0 -> "还差 %,d 元回本".format(-ledger.netYuan)
                ledger.netYuan > 0 -> "领先 %,d 元".format(ledger.netYuan)
                else -> "正好回本"
            },
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
        if (ledger.pendingCount > 0) {
            SketchMetaRow(label = "待开奖", value = "${ledger.pendingCount} 笔")
        }
        if (ledger.totalCostYuan > 0) {
            Spacer(Modifier.height(8.dp))
            SketchBarRow(
                label = "回收率",
                fraction = (ledger.totalPrizeYuan.toDouble() / ledger.totalCostYuan).toFloat().coerceIn(0f, 1f),
                valueText = "%.0f%%".format(100.0 * ledger.totalPrizeYuan / ledger.totalCostYuan),
                subText = "条满 = 保本；短一截就是差额",
                seed = "pnl-recover",
            )
        }
        Spacer(Modifier.height(8.dp))
        SketchLineChart(
            values = ledger.cumulativeNet,
            baseline = 0f,
            includeZero = true,
            area = true,
            markers = ledger.winIndices,
            seed = "pnl-line",
        )
        Text(
            "累计净收益（奖金 − 投入），按购买时间；圆点 = 中奖的那几笔。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        if (ledger.tierCounts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PrizeTier.entries.sortedByDescending { it.rank }.forEach { tier ->
                val count = ledger.tierCounts[tier] ?: 0L
                if (count > 0) {
                    val amount = ledger.tierAmounts[tier] ?: 0L
                    SketchMetaRow(
                        label = tier.label,
                        value = if (amount > 0) {
                            "$count 次 · %,d 元".format(amount)
                        } else {
                            "$count 次 · 金额待公告"
                        },
                        valueColor = if (amount > 0) SketchPalette.RedInk else SketchPalette.Ink,
                    )
                }
            }
        }
    }
}

/**
 * 购票日历：每天一个格子，标出当天记录的购买结果。
 * 金底 = 中奖 · 红点 = 未中 · 灰点 = 待开奖。点某天看当天明细。
 */
@Composable
private fun CalendarCard(
    marks: Map<LocalDate, DayMark>,
    dayEntries: Map<LocalDate, List<PurchaseDayEntry>>,
) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var openDay by remember { mutableStateOf<LocalDate?>(null) }
    val zone = TimeZone.currentSystemDefault()
    val today = Instant.fromEpochMilliseconds(System.currentTimeMillis()).toLocalDateTime(zone).date
    val base = today.year * 12 + (today.monthNumber - 1) + monthOffset
    val year = base / 12
    val month = base % 12 + 1
    val first = LocalDate(year, month, 1)
    val daysInMonth = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    val leading = first.dayOfWeek.isoDayNumber - 1 // 周一为一周之始
    val cellCount = leading + daysInMonth
    val rowCount = (cellCount + 6) / 7

    SketchCard(seed = "ticket-calendar", tilt = -0.2f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("购票日历")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SketchMiniButton(label = "‹", seed = "cal-prev", onClick = { monthOffset-- })
                SketchMiniButton(label = "›", seed = "cal-next", onClick = { monthOffset++ })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "%d 年 %d 月".format(year, month),
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(w, style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkFaint)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rowCount) { r ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { c ->
                        val day = r * 7 + c - leading + 1
                        if (day in 1..daysInMonth) {
                            val date = LocalDate(year, month, day)
                            DayCell(
                                day = day,
                                mark = marks[date] ?: DayMark.NONE,
                                onClick = { openDay = date },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "金底 = 中奖 · 红点 = 未中 · 灰点 = 待开奖；点某天看当天记录",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }

    openDay?.let { date ->
        val list = dayEntries[date].orEmpty()
        SketchSheet(onDismiss = { openDay = null }) { dismiss ->
            Text(
                "%d月%d日 · %d 笔".format(date.monthNumber, date.dayOfMonth, list.size),
                style = MaterialTheme.typography.titleMedium,
                color = SketchPalette.Ink,
            )
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Text(
                    "这天没有记录购买。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.InkSoft,
                )
            } else {
                list.forEach { e ->
                    Text(
                        "号码 #${e.ticketId} · ${e.costYuan} 元 · " + when {
                            e.pending -> "待开奖"
                            e.tierLabel != null -> "${e.tierLabel}（第 ${e.drawCode} 期）"
                            else -> "未中奖（第 ${e.drawCode} 期）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SketchPalette.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            SketchMiniButton(
                label = "关闭",
                seed = "cal-day-close",
                onClick = dismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DayCell(day: Int, mark: DayMark, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (mark == DayMark.WIN) SketchPalette.Highlighter else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text("$day", style = MaterialTheme.typography.labelSmall, color = SketchPalette.Ink)
        }
        val dot = when (mark) {
            DayMark.WIN -> Color.Transparent

            // 金底已表达
            DayMark.MISS -> SketchPalette.RedInk

            DayMark.PENDING -> SketchPalette.InkFaint

            DayMark.NONE -> Color.Transparent
        }
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(dot),
        )
    }
}
