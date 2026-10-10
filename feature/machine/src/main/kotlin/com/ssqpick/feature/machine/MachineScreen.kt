package com.ssqpick.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchDivider
import com.ssqpick.designsystem.sketch.SketchMetaRow
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.OddsInfo

/**
 * 摇号机主屏（手绘风格）。
 *
 * 手绘只作用在外壳：卡片、边框、按钮、标签。**号码球保持规整实心**——
 * 读清号码是这个应用的底线，抖动字形或歪圆会直接损害可用性。
 */
@Composable
fun MachineScreen(
    viewModel: MachineViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val m = ui.machine

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SketchPalette.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        SketchCard(seed = "machine", tilt = -0.3f) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SketchSectionTitle("摇号机")
                SketchChip(label = m.phaseLabel, seed = m.phaseLabel)
            }

            Spacer(Modifier.height(12.dp))

            DrumStage(
                controller = viewModel.controller,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SsqDimens.drumStageHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SketchPalette.Paper),
            )

            Spacer(Modifier.height(14.dp))
            SketchDivider(seed = "machine-divider")
            Spacer(Modifier.height(12.dp))

            ResultStrip(m)

            Spacer(Modifier.height(16.dp))
            ActionBar(
                machine = m,
                saved = ui.saved,
                onStart = viewModel::start,
                onReset = viewModel::reset,
                onSave = viewModel::save,
            )

            ui.saveMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.InkSoft,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        ModeSelector(
            selected = m.mode,
            enabled = !m.isRunning,
            onSelect = viewModel::selectMode,
        )

        Spacer(Modifier.height(14.dp))
        HistoryPanel(ui.history)

        Spacer(Modifier.height(24.dp))
    }
}

/** 结果条：6 红 + 1 蓝，一行呈现，全屏唯一需要读清号码的地方。 */
@Composable
private fun ResultStrip(m: MachineUiState) {
    // 复式红 10 蓝 4 一行摆不下（单式 6+1 刚好）：用 FlowRow 自动换行，
    // 否则蓝球会被顶到屏幕外面去。
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        BallSlots(
            numbers = m.redNumbers,
            isBlue = false,
            placeholderCount = m.mode.redCount,
        )
        Text(
            "+",
            style = MaterialTheme.typography.titleLarge,
            color = SketchPalette.Ink,
        )
        BallSlots(
            numbers = m.blueNumbers,
            isBlue = true,
            placeholderCount = m.mode.blueCount,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${m.mode.label} · ${m.betCount} 注 · ${m.priceYuan} 元",
        style = MaterialTheme.typography.bodySmall,
        color = SketchPalette.InkSoft,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** 一组球槽：已出画球、未出画空圈（沿用 BallRow 的"位置比隐藏清楚"）。 */
@Composable
private fun FlowRowScope.BallSlots(
    numbers: List<Int>,
    isBlue: Boolean,
    placeholderCount: Int,
) {
    repeat(placeholderCount) { index ->
        val n = numbers.getOrNull(index)
        if (n == null) {
            Box(
                Modifier
                    .size(SsqDimens.ballSize)
                    .clip(CircleShape)
                    .border(1.dp, SketchPalette.InkFaint, CircleShape),
            )
        } else {
            NumberBall(number = n, isBlue = isBlue, size = SsqDimens.ballSize)
        }
    }
}

/** 主操作区。按钮文案随状态变化，而不是一个按钮配三种不可见的禁用理由。 */
@Composable
private fun ActionBar(
    machine: MachineUiState,
    saved: Boolean,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when {
            machine.isComplete -> {
                SketchButton(
                    label = if (saved) "已保存" else "保存这注",
                    onClick = onSave,
                    enabled = !saved,
                    seed = "save",
                    tilt = -0.3f,
                )
                SketchButton(
                    label = "重新摇",
                    onClick = onReset,
                    filled = false,
                    seed = "reset",
                    tilt = 0.4f,
                )
            }

            machine.isRunning -> {
                SketchButton(
                    label = machine.phaseLabel,
                    onClick = {},
                    enabled = false,
                    seed = "running",
                )
            }

            else -> {
                SketchButton(
                    label = "开始摇号",
                    onClick = onStart,
                    seed = "start",
                    tilt = -0.5f,
                )
            }
        }
    }
}

/** 投注方式：手绘墨线框，选中的是实心。 */
@Composable
private fun ModeSelector(
    selected: DrawMode,
    enabled: Boolean,
    onSelect: (DrawMode) -> Unit,
) {
    Column {
        SketchSectionTitle("投注方式")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrawMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .rotate(if (isSelected) -0.8f else 0.6f)
                        .sketchBox(
                            seed = mode.name,
                            filled = isSelected,
                            enabled = enabled,
                            onClick = { onSelect(mode) },
                        )
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) SketchPalette.Paper else SketchPalette.InkSoft,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(record: HistoryRecord?) {
    SketchCard(seed = "history", tilt = 0.25f) {
        SketchSectionTitle("历史战绩")
        Spacer(Modifier.height(8.dp))
        if (record == null) {
            Text(
                "摇一注之后，这里会告诉你这组号码在全部历史开奖里中过什么。",
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.InkSoft,
            )
        } else {
            Text(
                OddsInfo.describe(record),
                style = MaterialTheme.typography.bodyMedium,
                color = SketchPalette.Ink,
            )
            Spacer(Modifier.height(8.dp))
            if (record.hasEverWon) {
                SketchMetaRow("中过", record.highestTier!!.label, valueColor = SketchPalette.RedInk)
                SketchMetaRow("期号", record.highestTierDrawCode ?: "—")
                SketchMetaRow("单注奖金", "${record.highestTierAmountYuan} 元")
            } else {
                SketchMetaRow("最好命中", "${record.bestRedMatches} 个红球")
                Spacer(Modifier.height(4.dp))
                Text(
                    OddsInfo.logProbabilityText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.InkSoft,
                )
                Text(
                    "一注在 ${record.checkedDraws} 期里出现过的概率只有 " +
                        "%.4f%%，没中过任何奖才是正常结果。".format(
                            OddsInfo.appearProbability(record.checkedDraws) * 100,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.InkSoft,
                )
            }
        }
    }
}
