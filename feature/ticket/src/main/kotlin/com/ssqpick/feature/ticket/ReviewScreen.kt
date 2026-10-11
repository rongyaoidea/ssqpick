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
 * 复盘页：只放"已经发生的记录"——我的战绩（真实购买账本）与购票日历。
 *
 * 选号指纹与历史回测**不在这里**：它们绑定的是"正在选的那组号"，
 * 跟着选号走，留在选号页。
 */
@Composable
fun ReviewScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
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
