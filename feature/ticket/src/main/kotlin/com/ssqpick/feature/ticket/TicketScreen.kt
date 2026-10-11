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
 * 选号主屏：把这一注号选出来，并给出这组号的即时反馈。
 *
 * 指纹与回测绑定的是"正在选的那组号"（draft），所以留在这里、跟着选号走；
 * 已保存的号码与"已发生的记录"（战绩/日历）分别在"对奖"和"复盘"页。
 */
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val fingerprint by viewModel.fingerprint.collectAsStateWithLifecycle()
    val bt by viewModel.backtest.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(6.dp))
                SyncCard(
                    drawCount = ui.drawCount,
                    message = ui.syncMessage,
                    newest = ui.newestDraw,
                    onSync = viewModel::syncDraws,
                )
                Spacer(Modifier.height(14.dp))
                DraftCard(
                    ui = ui,
                    stats = stats,
                    onToggleRed = viewModel::toggleRed,
                    onToggleBlue = viewModel::toggleBlue,
                    onBoldMode = viewModel::setBoldMode,
                    onBlueBoldMode = viewModel::setBlueBoldMode,
                    onClear = viewModel::clear,
                    onRandom = viewModel::randomFill,
                    onSave = { viewModel.save() },
                )
                ui.actionMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.Ink)
                }
                Spacer(Modifier.height(14.dp))
                FingerprintStrip(fingerprint)
                Spacer(Modifier.height(14.dp))
                BacktestCard(
                    bt = bt,
                    draft = ui.draft,
                    range = ui.backtestRange,
                    onRange = viewModel::setBacktestRange,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SyncCard(
    drawCount: Long,
    message: String?,
    newest: com.ssqpick.model.Draw?,
    onSync: () -> Unit,
) {
    SketchCard(seed = "ticket-sync", tilt = 0.3f) {
        SketchSectionTitle("开奖数据")
        Spacer(Modifier.height(8.dp))
        Text(
            newest?.let { "已存 $drawCount 期 · 最新第 ${it.code} 期 ${it.date}" }
                ?: "本地尚无开奖数据",
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        Text(
            "数据来源：中国福彩发行管理中心官方公告（2013 年至今，首次同步会补齐全部历史）。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.Ink)
        }
        Spacer(Modifier.height(10.dp))
        SketchButton(
            label = "从官方同步开奖数据",
            onClick = onSync,
            filled = false,
            seed = "ticket-sync-btn",
            tilt = 0.4f,
        )
    }
}

@Composable
private fun DraftCard(
    ui: TicketUiState,
    stats: NumberStats?,
    onToggleRed: (Int) -> Unit,
    onToggleBlue: (Int) -> Unit,
    onBoldMode: (Boolean) -> Unit,
    onBlueBoldMode: (Boolean) -> Unit,
    onClear: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = ui.draft
    // 遗漏小字只在有开奖数据时出现；库里空时显示全 0 反而误导
    val showOmission = (stats?.totalDraws ?: 0) > 0
    SketchCard(seed = "ticket-draft", tilt = -0.3f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle(draft.kind.label)
            SketchChip(
                label = "${draft.betCount} 注 / ${draft.priceYuan} 元",
                seed = "ticket-bet-${draft.betCount}",
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!ui.isValid) {
            Text(
                ui.validation.message ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.RedInk,
            )
            Spacer(Modifier.height(4.dp))
        }

        SectionLabel("红球（1-33）", "胆 ${draft.redBold.size} · 拖 ${draft.redPicked.size}")
        Spacer(Modifier.height(6.dp))
        PickModeToggle(
            boldMode = ui.boldMode,
            boldLabel = "胆码（最多5个）",
            seed = "red-mode",
            onSelect = onBoldMode,
        )
        Spacer(Modifier.height(6.dp))
        NumberGrid(
            range = 1..33,
            selected = draft.redPool,
            bold = draft.redBold,
            onToggle = onToggleRed,
            omission = if (showOmission) stats?.redOmission else null,
        )
        val redLegend = buildList {
            if (showOmission) add("球下小字 = 遗漏期数")
            if (ui.boldMode || draft.redBold.isNotEmpty()) add("双圈 = 胆码，每注必含")
        }.joinToString(" · ")
        if (redLegend.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                redLegend,
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("蓝球（1-16）", "胆 ${draft.blueBold.size} · 拖 ${draft.bluePicked.size}")
        Spacer(Modifier.height(6.dp))
        PickModeToggle(
            boldMode = ui.blueBoldMode,
            boldLabel = "胆码（最多1个）",
            seed = "blue-mode",
            onSelect = onBlueBoldMode,
        )
        Spacer(Modifier.height(6.dp))
        NumberGrid(
            range = 1..16,
            selected = draft.bluePool,
            bold = draft.blueBold,
            onToggle = onToggleBlue,
            omission = if (showOmission) stats?.blueOmission else null,
        )
        val blueLegend = buildList {
            if (showOmission) add("球下小字 = 遗漏期数")
            if (ui.blueBoldMode || draft.blueBold.isNotEmpty()) add("双圈 = 胆码，每注必含")
        }.joinToString(" · ")
        if (blueLegend.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                blueLegend,
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }

        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SketchButton(
                label = "保存这注",
                onClick = onSave,
                enabled = ui.isValid,
                seed = "ticket-save",
                tilt = -0.4f,
            )
            SketchButton(
                label = "机选",
                onClick = onRandom,
                filled = false,
                seed = "ticket-random",
                tilt = 0.4f,
            )
            SketchButton(
                label = "清空",
                onClick = onClear,
                filled = false,
                seed = "ticket-clear",
                tilt = -0.3f,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
        Text(
            count,
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 拖码/胆码取号模式切换。胆与拖互斥，切换模式不丢已选号。 */
@Composable
private fun PickModeToggle(
    boldMode: Boolean,
    boldLabel: String,
    seed: String,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeBox(
            label = "拖码",
            filled = !boldMode,
            seed = "$seed-drag",
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
        ModeBox(
            label = boldLabel,
            filled = boldMode,
            seed = "$seed-bold",
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeBox(
    label: String,
    filled: Boolean,
    seed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .sketchBox(seed = seed, filled = filled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) SketchPalette.Paper else SketchPalette.InkSoft,
        )
    }
}

/** 号码网格。未选中的球渲染为暗色轮廓态，点任意位置即可切换。 */
@Composable
private fun NumberGrid(
    range: IntRange,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    perRow: Int = 6,
    bold: Set<Int> = emptySet(),
    /** 号码 → 遗漏期数。开奖数据未同步时为 null，格下不显示小字。 */
    omission: Map<Int, Int>? = null,
) {
    val isBlue = range.last == 16
    val numbers = range.toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        numbers.chunked(perRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { n ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NumberBall(
                            number = n,
                            isBlue = isBlue,
                            size = SsqDimens.ballSizeCompact,
                            selected = n in selected,
                            bold = n in bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggle(n) },
                        )
                        if (omission != null) {
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "${omission[n] ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SketchPalette.InkFaint,
                            )
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 选号指纹（紧凑条）：这组号在历史分布里的位置。跟着选号走，不单独成卡。 */
@Composable
private fun FingerprintStrip(fp: DraftFingerprint?) {
    if (fp == null || fp.draws == 0) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("选号指纹")
            Text(
                "跟着这组号走",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (fp.singleShape) {
            Text(
                "和值 ${fp.sum} · 历史 %.0f%% 分位".format(fp.sumPercentile * 100),
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.Ink,
            )
            SketchRangeMarker(
                position = fp.sumPercentile.toFloat(),
                seed = "fp-sum",
                modifier = Modifier.padding(vertical = 2.dp),
            )
        } else {
            Text(
                "复式/胆拖的和值不唯一，只看组成结构。",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        Text(
            "奇偶 ${fp.oddCount}:${fp.redCount - fp.oddCount}（历史均值 %.1f 个奇数）".format(fp.oddMean),
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
        Text(
            "三区 " + fp.zoneCounts.joinToString("/") + "（历史 " +
                fp.zoneShareMean.map { "%.0f".format(it * 100) }.joinToString("/") + "%）",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "只描述这组号的位置与结构，不改变中奖概率。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}

/** 历史回测：当前草稿在选定窗口上的表现。默认只给一行摘要，展开才看细节。 */
@Composable
private fun BacktestCard(
    bt: DraftBacktest?,
    draft: com.ssqpick.model.TicketDraft,
    range: BacktestRange,
    onRange: (BacktestRange) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SketchCard(seed = "ticket-backtest", tilt = -0.25f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("历史回测")
            SketchMiniButton(
                label = if (expanded) "收起" else "展开",
                seed = "bt-toggle",
                onClick = { expanded = !expanded },
            )
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
        Text(
            "${bt.draws} 期 · 中奖 ${bt.winningBets} 注 · 回报率 %.1f%%".format(bt.roi * 100),
            style = MaterialTheme.typography.bodyMedium,
            color = SketchPalette.Ink,
        )
        if (!expanded) return@SketchCard

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

/** 折线抽稀：超过上限时等步长取样（保头保尾），几千个点画出来也是一条抖线。 */
private fun downsampleFloats(values: List<Float>, maxPoints: Int): List<Float> {
    if (values.size <= maxPoints) return values
    val stride = values.size / maxPoints + 1
    return values.filterIndexed { i, _ -> i % stride == 0 || i == values.lastIndex }
}
