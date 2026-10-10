package com.ssqpick.feature.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.sketch.SketchBarRow
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchMetaRow
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.NumberStats
import com.ssqpick.rules.ShapeStats
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.TicketStatus
import com.ssqpick.rules.theoreticalBlueHitRate
import com.ssqpick.rules.theoreticalRedDistribution

/**
 * 手选号码：点格选号，支持单式与复式，保存后可查看历史战绩。
 *
 * 手绘只作用在外壳（卡片、按钮、标签）；号码球本身保持规整实心，
 * 点选格只管切换选中态，不引入第二套视觉语言。
 *
 * 主题由 MainActivity 提供，这里不再包一层。
 */
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val shape by viewModel.shape.collectAsStateWithLifecycle()
    val bt by viewModel.backtest.collectAsStateWithLifecycle()
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()

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
                StatsCard(stats)
                Spacer(Modifier.height(14.dp))
                ShapeCard(shape = shape, draft = ui.draft)
                Spacer(Modifier.height(14.dp))
            }
        }

        item {
            Column(Modifier.padding(horizontal = 14.dp)) {
                DraftCard(
                    ui = ui,
                    onToggleRed = viewModel::toggleRed,
                    onToggleBlue = viewModel::toggleBlue,
                    onBoldMode = viewModel::setBoldMode,
                    onBlueBoldMode = viewModel::setBlueBoldMode,
                    onClear = viewModel::clear,
                    onRandom = viewModel::randomFill,
                    onSave = { viewModel.save() },
                )
                Spacer(Modifier.height(14.dp))
                BacktestCard(bt = bt, draft = ui.draft)
                Spacer(Modifier.height(14.dp))
            }
        }

        if (tickets.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SketchSectionTitle("已保存的号码")
                    SketchChip(label = "${tickets.size} 张", seed = "ticket-count")
                }
                Spacer(Modifier.height(10.dp))
            }
            items(tickets, key = { it.id }) { ticket ->
                Column(Modifier.padding(horizontal = 14.dp)) {
                    TicketRow(
                        ticket = ticket,
                        history = histories[ticket.id],
                        status = statuses[ticket.id],
                        onDelete = { viewModel.delete(ticket) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    SketchCard(seed = "ticket-empty", tilt = 0.3f) {
                        Text(
                            "还没有保存的号码。摇号或手选后点“保存这注”。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SketchPalette.InkSoft,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
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
            "数据来源：中国福彩发行管理中心官方开奖公告。",
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

/** 冷热参考：热门与久未出的号码，只做历史描述，不做预测。 */
@Composable
private fun StatsCard(stats: NumberStats?) {
    SketchCard(seed = "ticket-stats", tilt = 0.25f) {
        SketchSectionTitle("冷热参考")
        Spacer(Modifier.height(8.dp))
        if (stats == null || stats.totalDraws == 0) {
            Text(
                "同步开奖数据后，这里会统计号码冷热。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
        } else {
            StatGroup(
                label = "热门红球",
                hint = "近 ${stats.totalDraws} 期出现最多",
                numbers = stats.hotReds(5),
                isBlue = false,
                counts = stats.redFrequency,
                unit = "次",
                extra = { n -> "%.1f%%".format((stats.redFrequency[n] ?: 0) * 100.0 / stats.totalDraws) },
            )
            Spacer(Modifier.height(8.dp))
            StatGroup(
                label = "久未出红球",
                hint = "遗漏期数最长",
                numbers = stats.coldReds(5),
                isBlue = false,
                counts = stats.redOmission,
                unit = "期",
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    StatGroup(
                        label = "热门蓝球",
                        hint = null,
                        numbers = stats.hotBlues(3),
                        isBlue = true,
                        counts = stats.blueFrequency,
                        unit = "次",
                        extra = { n -> "%.1f%%".format((stats.blueFrequency[n] ?: 0) * 100.0 / stats.totalDraws) },
                    )
                }
                Column(Modifier.weight(1f)) {
                    StatGroup(
                        label = "久未出蓝球",
                        hint = null,
                        numbers = stats.coldBlues(3),
                        isBlue = true,
                        counts = stats.blueOmission,
                        unit = "期",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "红球理论出场率 18.2% · 蓝球 6.3%。冷热只是历史统计，不提高中奖率。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
        }
    }
}

@Composable
private fun StatGroup(
    label: String,
    hint: String?,
    numbers: List<Int>,
    isBlue: Boolean,
    counts: Map<Int, Int>,
    unit: String,
    /** 号码下的第二行小字：热门组传出场率，遗漏组不传。 */
    extra: ((Int) -> String)? = null,
) {
    Text(
        if (hint == null) label else "$label · $hint",
        style = MaterialTheme.typography.labelSmall,
        color = SketchPalette.InkSoft,
    )
    Spacer(Modifier.height(5.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        numbers.forEach { n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NumberBall(n, isBlue = isBlue, size = SsqDimens.ballSizeSmall)
                Text(
                    "${counts[n] ?: 0}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkSoft,
                )
                extra?.let {
                    Text(
                        it(n),
                        style = MaterialTheme.typography.labelSmall,
                        color = SketchPalette.InkFaint,
                    )
                }
            }
        }
    }
}

/** 开奖形态：和值、奇偶、区间、连号，并对照当前草稿（仅单式有唯一形态）。 */
@Composable
private fun ShapeCard(shape: ShapeStats?, draft: com.ssqpick.model.TicketDraft) {
    SketchCard(seed = "ticket-shape", tilt = 0.3f) {
        SketchSectionTitle("开奖形态")
        Spacer(Modifier.height(8.dp))
        if (shape == null || shape.draws == 0) {
            Text(
                "同步开奖数据后，这里描述历史开奖的形态。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
            return@SketchCard
        }
        SketchMetaRow(
            label = "和值",
            value = "均值 %.0f · 九成在 %d~%d".format(shape.sumMean, shape.sumLow, shape.sumHigh),
        )
        SketchMetaRow(label = "奇数", value = "平均 %.1f 个/期".format(shape.oddMean))
        SketchMetaRow(
            label = "三区占比",
            value = shape.zoneShare.joinToString(" / ") { "%.0f%%".format(it * 100) },
        )
        SketchMetaRow(label = "含连号", value = "%.0f%% 的期次".format(shape.consecutiveShare * 100))
        Spacer(Modifier.height(8.dp))
        val reds = draft.redPool
        if (draft.redBold.isEmpty() && reds.size == TicketRules.RED_PICK && draft.bluePool.isNotEmpty()) {
            val sorted = reds.sorted()
            val zones = sorted.groupBy { (it - 1) / 11 }.mapValues { it.value.size }
            Text(
                "当前：和值 ${sorted.sum()} · 奇数 ${sorted.count { it % 2 == 1 }} 个 · " +
                    "分区 ${zones[0] ?: 0}/${zones[1] ?: 0}/${zones[2] ?: 0} · " +
                    (if (sorted.zipWithNext().any { (a, b) -> b - a == 1 }) "有连号" else "无连号"),
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.Ink,
            )
        } else {
            Text(
                "复式/胆拖不止一组，形态不唯一，只看上面历史。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "形态描述历史，不改变每期概率。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 历史回测：当前草稿在全部历史上的表现，随选号实时更新，只描述不预测。 */
@Composable
private fun BacktestCard(bt: DraftBacktest?, draft: com.ssqpick.model.TicketDraft) {
    SketchCard(seed = "ticket-backtest", tilt = -0.25f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("历史回测")
            if (bt != null && bt.draws > 0) {
                SketchChip(label = "近 ${bt.draws} 期", seed = "bt-draws")
            }
        }
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(8.dp))
        val blueTheory = remember(draft) { theoreticalBlueHitRate(draft) }
        SketchMetaRow(
            label = "蓝球命中",
            value = "%.2f%%（理论 %.2f%%）".format(bt.blueRate * 100, blueTheory * 100),
        )
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
            "期望回报为负是数学，不是运气不好。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

@Composable
private fun DraftCard(
    ui: TicketUiState,
    onToggleRed: (Int) -> Unit,
    onToggleBlue: (Int) -> Unit,
    onBoldMode: (Boolean) -> Unit,
    onBlueBoldMode: (Boolean) -> Unit,
    onClear: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = ui.draft
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
        )
        if (ui.boldMode || draft.redBold.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "双圈 = 胆码，每注必含",
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
        )
        if (ui.blueBoldMode || draft.blueBold.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "双圈 = 胆码，每注必含",
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

/** 存票相对最新一期的状态徽标：数据没到之前不占位。 */
@Composable
private fun StatusChip(status: TicketStatus?) {
    when (status) {
        null -> Unit

        TicketStatus.NotDrawn -> SketchChip(label = "未开奖", seed = "st-notdrawn", fill = null)

        TicketStatus.Missed -> SketchChip(label = "未中奖", seed = "st-missed", fill = null)

        is TicketStatus.Won -> SketchChip(
            // 一二等奖无公告时金额为 0：只报奖级，不编金额
            label = if (status.amountYuan > 0) {
                "${status.topTier?.label} · %,d 元".format(status.amountYuan)
            } else {
                status.topTier?.label ?: "中奖"
            },
            seed = "st-won-${status.topTier}",
        )
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
                    NumberBall(
                        number = n,
                        isBlue = isBlue,
                        size = SsqDimens.ballSizeCompact,
                        selected = n in selected,
                        bold = n in bold,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggle(n) },
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TicketRow(
    ticket: com.ssqpick.model.Ticket,
    history: HistoryRecord?,
    status: TicketStatus?,
    onDelete: () -> Unit,
) {
    SketchCard(seed = "ticket-${ticket.id}", tilt = if (ticket.id % 2L == 0L) 0.25f else -0.25f) {
        // 存票可能是 20 红大复式：一行摆不下会顶出屏幕，换行显示
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ticket.draft.redPool.sorted().forEach {
                NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall)
            }
        }
        Spacer(Modifier.height(2.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ticket.draft.bluePool.sorted().forEach {
                NumberBall(it, isBlue = true, size = SsqDimens.ballSizeSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${TicketRules.betCount(ticket.draft)} 注 · ${ticket.draft.kind.label}",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            StatusChip(status)
        }
        Spacer(Modifier.height(8.dp))
        history?.let { h ->
            Text(
                if (h.hasEverWon) {
                    "历史最佳：${h.highestTier?.label} · 第${h.highestTierDrawCode}期"
                } else {
                    "${h.checkedDraws} 期里未中过奖"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            Spacer(Modifier.height(8.dp))
        }
        SketchButton(
            label = "删除",
            onClick = onDelete,
            filled = false,
            seed = "ticket-del-${ticket.id}",
            tilt = 0.3f,
        )
    }
}
