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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
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
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.model.PrizeTier
import com.ssqpick.model.Ticket
import com.ssqpick.model.TicketKind
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.DraftFingerprint
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.NumberStats
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
 * 复盘页：把"选号"里读数据的那几块搬出来单独成页。
 *
 * 选号是手在点，复盘是眼睛在读；两种节奏挤在一页时，选号要滚四屏才能点
 * 一次保存。这一页只管读：指纹、回测、战绩、购票日历，每块一个 item，
 * 不再挤在一个巨型 item 里整体测量。
 */
@Composable
fun ReviewScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val bt by viewModel.backtest.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val fingerprint by viewModel.fingerprint.collectAsStateWithLifecycle()
    val calendarMarks by viewModel.calendarMarks.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(6.dp))
                FingerprintCard(fingerprint)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                BacktestCard(
                    bt = bt,
                    draft = ui.draft,
                    range = ui.backtestRange,
                    onRange = viewModel::setBacktestRange,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                PnlCard(ledger)
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                CalendarCard(marks = calendarMarks)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 选号指纹：把这组号放进历史分布里定位——只描述位置与结构，不预测。 */
@Composable
private fun FingerprintCard(fp: DraftFingerprint?) {
    SketchCard(seed = "ticket-fingerprint", tilt = 0.2f) {
        SketchSectionTitle("选号指纹")
        Spacer(Modifier.height(8.dp))
        if (fp == null) {
            Text(
                "先选几个号，这里把你这组号放进历史分布里定位。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        if (fp.draws == 0) {
            Text(
                "同步开奖数据后，这里能把你的选号放进历史分布。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        if (fp.singleShape) {
            Text(
                "和值 ${fp.sum} · 历史 %.0f%% 分位".format(fp.sumPercentile * 100),
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.Ink,
            )
            SketchRangeMarker(
                position = fp.sumPercentile.toFloat(),
                seed = "fp-sum",
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Text(
                "淡墨带 = 九成期次所在的和值范围",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        } else {
            Text(
                "复式/胆拖的和值不唯一，不画标尺；看下面的组成结构。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
        }
        Spacer(Modifier.height(8.dp))
        SketchMetaRow(
            label = "奇数",
            value = "${fp.oddCount} 个（历史均值 %.1f 个）".format(fp.oddMean),
        )
        Spacer(Modifier.height(6.dp))
        fp.zoneCounts.forEachIndexed { i, count ->
            SketchBarRow(
                label = ZONE_LABELS[i],
                fraction = if (fp.redCount > 0) count.toFloat() / fp.redCount else 0f,
                valueText = "$count 个",
                subText = "历史 %.0f%%".format(fp.zoneShareMean.getOrElse(i) { 0.0 } * 100),
                seed = "fp-zone-$i",
            )
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "指纹只描述这组号的位置与结构，不改变中奖概率。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

private val ZONE_LABELS = listOf("一区 1-11", "二区 12-22", "三区 23-33")

/** 折线抽稀：超过上限时等步长取样（保头保尾），几千个点画出来也是一条抖线。 */
private fun downsampleFloats(values: List<Float>, maxPoints: Int): List<Float> {
    if (values.size <= maxPoints) return values
    val stride = values.size / maxPoints + 1
    return values.filterIndexed { i, _ -> i % stride == 0 || i == values.lastIndex }
}

/** 历史回测：当前草稿在选定窗口上的表现，随选号实时更新，只描述不预测。 */
@Composable
private fun BacktestCard(
    bt: DraftBacktest?,
    draft: com.ssqpick.model.TicketDraft,
    range: BacktestRange,
    onRange: (BacktestRange) -> Unit,
) {
    SketchCard(seed = "ticket-backtest", tilt = -0.25f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("历史回测")
            if (bt != null && bt.draws > 0) {
                SketchChip(label = "共 ${bt.draws} 期", seed = "bt-draws")
            }
        }
        Spacer(Modifier.height(8.dp))
        // 窗口切换：全部 / 近100 / 近30。全量是"从首期守到今天"的近似。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BacktestRange.entries.forEach { r ->
                SketchMiniButton(
                    label = r.label,
                    seed = "bt-range-${r.name}",
                    onClick = { onRange(r) },
                    filled = r == range,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (bt == null || bt.totalBetDraws == 0L) {
            Text(
                "选出合法号码后，这里显示它在历史上的表现。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }

        // 分布条共用一把尺子：以实测最大值归一，理论值只写小字对照
        val theory = remember(draft) { theoreticalRedDistribution(draft) }
        val rows = listOf(6, 5, 4, 3).map { k ->
            Triple("中${k}红", bt.redRate(k), theory[k] ?: 0.0)
        } + Triple(
            "中≤2红",
            (0..2).sumOf { bt.redRate(it) },
            1.0 - (3..6).sumOf { theory[it] ?: 0.0 },
        )
        val maxFrac = (rows.maxOf { it.second }.coerceAtLeast(1e-9)).toFloat()
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            rows.forEach { (label, rate, t) ->
                SketchBarRow(
                    label = label,
                    fraction = (rate / maxFrac).toFloat(),
                    valueText = "%.2f%%".format(rate * 100),
                    subText = "理论 %.2f%%".format(t * 100),
                    seed = "bt-$label",
                )
            }
        }

        // 逐期轨迹：慢流血 + 偶发跃升，是彩票回报最真实的样子
        if (bt.cumulativeNet.size >= 2) {
            Spacer(Modifier.height(8.dp))
            Text(
                "假想累计净收益（逐期）",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            val line = remember(bt.cumulativeNet) { downsampleFloats(bt.cumulativeNet, 240) }
            SketchLineChart(
                values = line,
                baseline = 0f,
                includeZero = true,
                area = true,
                seed = "bt-line",
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        val blueTheory = remember(draft) { theoreticalBlueHitRate(draft) }
        SketchMetaRow(
            label = "蓝球命中",
            value = "%.2f%%（理论 %.2f%%）".format(bt.blueRate * 100, blueTheory * 100),
        )
        if (bt.tierCounts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "中过的奖级：" + PrizeTier.entries.sortedByDescending { it.rank }
                    .filter { (bt.tierCounts[it] ?: 0L) > 0L }
                    .joinToString(" · ") { "${it.label} ${bt.tierCounts[it]} 注" },
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.Ink,
            )
        }
        Spacer(Modifier.height(4.dp))
        SketchMetaRow(label = "假装期期都买", value = "投入 %,d 元".format(bt.costYuan))
        SketchMetaRow(
            label = "实付奖金",
            value = "%,d 元".format(bt.prizeYuan),
            valueColor = if (bt.prizeYuan >= bt.costYuan) SketchPalette.Ink else SketchPalette.RedInk,
        )
        SketchMetaRow(label = "回报率", value = "%.1f%%".format(bt.roi * 100))
        Spacer(Modifier.height(6.dp))
        Text(
            "期望回报为负是数学，不是运气不好。全部历史 ≈ 从首期守号至今。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
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
                "在号码上点“记录购买”，这里会记真实的投入、奖金与累计净收益。",
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
 * 金底 = 中奖 · 红点 = 未中 · 灰点 = 待开奖。只统计"记录过购买"的日期。
 */
@Composable
private fun CalendarCard(marks: Map<LocalDate, DayMark>) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
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
                            DayCell(
                                day = day,
                                mark = marks[LocalDate(year, month, day)] ?: DayMark.NONE,
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
            "金底 = 中奖 · 红点 = 未中 · 灰点 = 待开奖（按记录购买的日期）",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}

@Composable
private fun DayCell(day: Int, mark: DayMark, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 3.dp),
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
