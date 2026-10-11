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
 * 选号主屏：只做一件事——把这一注号选出来。
 *
 * 复盘分析（指纹/回测/战绩/日历）搬到了"复盘"页，已保存的号码搬到了
 * "对奖"页：这一页以前塞着三个任务，滚动四屏才能点一次保存。
 */
@Composable
fun TicketScreen(
    viewModel: TicketViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
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
