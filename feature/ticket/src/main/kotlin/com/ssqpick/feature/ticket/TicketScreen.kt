package com.ssqpick.feature.ticket

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.ssqpick.rules.DraftBacktest
import com.ssqpick.rules.DraftFingerprint
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.PurchaseLedger
import com.ssqpick.rules.TicketPurchaseSummary
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
    val bt by viewModel.backtest.collectAsStateWithLifecycle()
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val summaries by viewModel.purchaseSummaries.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val fingerprint by viewModel.fingerprint.collectAsStateWithLifecycle()

    // 第一次记录购买时顺手申请通知权限：后台自动对奖的中奖通知靠它
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestNotifyPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var renameTarget by remember { mutableStateOf<Ticket?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
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
                    FingerprintCard(fingerprint)
                    Spacer(Modifier.height(14.dp))
                    BacktestCard(
                        bt = bt,
                        draft = ui.draft,
                        range = ui.backtestRange,
                        onRange = viewModel::setBacktestRange,
                    )
                    Spacer(Modifier.height(14.dp))
                    PnlCard(ledger)
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (tickets.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SketchSectionTitle("已保存的号码")
                            SketchChip(label = "${tickets.size} 张", seed = "ticket-count")
                        }
                        ui.actionMessage?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.Ink)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                items(tickets, key = { it.id }) { ticket ->
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        TicketRow(
                            ticket = ticket,
                            history = histories[ticket.id],
                            status = statuses[ticket.id],
                            summary = summaries[ticket.id],
                            onRecord = {
                                viewModel.recordPurchase(ticket)
                                requestNotifyPermission()
                            },
                            onRename = { renameTarget = ticket },
                            onUndo = { viewModel.undoLastPurchase(ticket) },
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

        renameTarget?.let { target ->
            SketchInputDialog(
                title = "备注号码 #${target.id}",
                initial = target.label.orEmpty(),
                onDismiss = { renameTarget = null },
                onConfirm = { label ->
                    viewModel.rename(target, label)
                    renameTarget = null
                },
            )
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
    ticket: Ticket,
    history: HistoryRecord?,
    status: TicketStatus?,
    summary: TicketPurchaseSummary?,
    onRecord: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
) {
    SketchCard(seed = "ticket-${ticket.id}", tilt = if (ticket.id % 2L == 0L) 0.25f else -0.25f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ticket.label ?: "号码 #${ticket.id}",
                style = MaterialTheme.typography.titleMedium,
                color = SketchPalette.Ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            StatusChip(status)
        }
        Spacer(Modifier.height(6.dp))
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
        Text(
            "${TicketRules.betCount(ticket.draft)} 注 · ${ticket.draft.kind.label}",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkSoft,
        )
        summary?.takeIf { it.count > 0 }?.let { s ->
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("已购 ${s.count} 笔")
                    if (s.pendingCount > 0) append("（待开奖 ${s.pendingCount}）")
                    append(" · 投入 ${s.costYuan} 元")
                    if (s.prizeYuan > 0) append(" · 中奖 ${s.prizeYuan} 元")
                },
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.Ink,
            )
        }
        history?.let { h ->
            Spacer(Modifier.height(2.dp))
            Text(
                if (h.hasEverWon) {
                    "历史最佳：${h.highestTier?.label} · 第${h.highestTierDrawCode}期"
                } else {
                    "${h.checkedDraws} 期里未中过奖"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SketchMiniButton(
                label = "记录购买",
                seed = "ticket-buy-${ticket.id}",
                onClick = onRecord,
                filled = true,
            )
            SketchMiniButton(label = "备注", seed = "ticket-label-${ticket.id}", onClick = onRename)
            if ((summary?.count ?: 0) > 0) {
                SketchMiniButton(label = "撤销购买", seed = "ticket-undo-${ticket.id}", onClick = onUndo)
            }
            SketchMiniButton(label = "删除", seed = "ticket-del-${ticket.id}", onClick = onDelete)
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

/** 手绘备注弹层：全屏遮罩 + 居中纸片。 */
@Composable
private fun SketchInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            SketchCard(seed = "rename-dialog", tilt = 0f) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = SketchPalette.Ink)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("例如：守号-周三") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SketchMiniButton(
                        label = "取消",
                        seed = "dlg-cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    SketchMiniButton(
                        label = "保存",
                        seed = "dlg-ok",
                        onClick = { onConfirm(text) },
                        filled = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
