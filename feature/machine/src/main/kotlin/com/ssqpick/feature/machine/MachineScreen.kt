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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchDivider
import com.ssqpick.designsystem.sketch.SketchMetaRow
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.model.Bet
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.OddsInfo
import kotlin.math.roundToInt

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
                ui = ui,
                onStart = viewModel::start,
                onReset = viewModel::reset,
                onSave = viewModel::save,
                onTogglePicker = viewModel::togglePicker,
                onToggleBet = viewModel::toggleBet,
                onSelectAllBets = viewModel::selectAllBets,
                onClearBetSelection = viewModel::clearBetSelection,
                onSaveSelected = viewModel::saveSelected,
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
            machine = m,
            enabled = !m.isRunning,
            onSelect = viewModel::selectMode,
            onRedCount = viewModel::setRedCount,
            onBlueCount = viewModel::setBlueCount,
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
            placeholderCount = m.redCount,
        )
        Text(
            "+",
            style = MaterialTheme.typography.titleLarge,
            color = SketchPalette.Ink,
        )
        BallSlots(
            numbers = m.blueNumbers,
            isBlue = true,
            placeholderCount = m.blueCount,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${countLabel(m)} · ${m.plannedBets} 注 · ${m.plannedPriceYuan} 元",
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

/** 逐注挑选：勾选单注保存，每注存成独立单式票。 */
@Composable
private fun PickerPanel(
    ui: DrawUiState,
    onToggleBet: (Bet) -> Unit,
    onSelectAllBets: () -> Unit,
    onClearBetSelection: () -> Unit,
    onSaveSelected: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "逐注保存（共 ${ui.totalBets} 注）",
            style = MaterialTheme.typography.titleSmall,
            color = SketchPalette.Ink,
        )
        if (ui.totalBets > ui.pickedBets.size) {
            Text(
                "注数太多，只展示前 ${ui.pickedBets.size} 注；要全存用上面的“保存这注”。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SketchMiniButton("全选", seed = "pick-all", onClick = onSelectAllBets, modifier = Modifier.weight(1f))
            SketchMiniButton("清空", seed = "pick-none", onClick = onClearBetSelection, modifier = Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.pickedBets.forEachIndexed { index, bet ->
                BetPickRow(
                    bet = bet,
                    selected = bet in ui.selectedBets,
                    seed = "pick-$index",
                    onToggle = { onToggleBet(bet) },
                )
            }
        }
        SketchButton(
            label = "保存选中（${ui.selectedBets.size}）",
            onClick = onSaveSelected,
            enabled = ui.selectedBets.isNotEmpty(),
            seed = "save-selected",
            tilt = -0.3f,
        )
    }
}

@Composable
private fun BetPickRow(bet: Bet, selected: Boolean, seed: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "已选" else "未选"
            }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .sketchBox(seed = seed, filled = selected, corner = 8.dp, onClick = null),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("✓", style = MaterialTheme.typography.labelMedium, color = SketchPalette.Paper)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bet.red.forEach { NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall) }
        }
        Text("+", style = MaterialTheme.typography.titleSmall, color = SketchPalette.InkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bet.blues.forEach { NumberBall(it, isBlue = true, size = SsqDimens.ballSizeSmall) }
        }
    }
}

/** 主操作区。按钮文案随状态变化，而不是一个按钮配三种不可见的禁用理由。 */
@Composable
private fun ActionBar(
    machine: MachineUiState,
    ui: DrawUiState,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onTogglePicker: () -> Unit,
    onToggleBet: (Bet) -> Unit,
    onSelectAllBets: () -> Unit,
    onClearBetSelection: () -> Unit,
    onSaveSelected: () -> Unit,
) {
    val saved = ui.saved
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
                // 复式一次多注：整单太粗时拆成单注勾选
                if (machine.betCount > 1) {
                    SketchButton(
                        label = if (ui.pickerOpen) "收起挑选" else "逐注挑选",
                        onClick = onTogglePicker,
                        enabled = !saved,
                        filled = false,
                        seed = "picker",
                        tilt = 0.3f,
                    )
                }
                SketchButton(
                    label = "重新摇",
                    onClick = onReset,
                    filled = false,
                    seed = "reset",
                    tilt = 0.4f,
                )
                if (ui.pickerOpen) {
                    PickerPanel(
                        ui = ui,
                        onToggleBet = onToggleBet,
                        onSelectAllBets = onSelectAllBets,
                        onClearBetSelection = onClearBetSelection,
                        onSaveSelected = onSaveSelected,
                    )
                }
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

/** 投注方式：预设一排做"一键填数"，自定义两个滑条才是真正的输入。 */
@Composable
private fun ModeSelector(
    machine: MachineUiState,
    enabled: Boolean,
    onSelect: (DrawMode) -> Unit,
    onRedCount: (Int) -> Unit,
    onBlueCount: (Int) -> Unit,
) {
    val preset = presetOf(machine)
    Column {
        SketchSectionTitle("投注方式")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrawMode.entries.forEach { mode ->
                val isSelected = mode == preset
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

        Spacer(Modifier.height(14.dp))
        SketchSectionTitle("自定义数量")
        Spacer(Modifier.height(6.dp))
        CountSlider(
            label = "红球",
            value = machine.redCount,
            range = DrawLimits.RED_MIN..DrawLimits.RED_MAX,
            enabled = enabled,
            color = SketchPalette.RedInk,
            onChange = onRedCount,
        )
        CountSlider(
            label = "蓝球",
            value = machine.blueCount,
            range = DrawLimits.BLUE_MIN..DrawLimits.BLUE_MAX,
            enabled = enabled,
            color = SketchPalette.BlueInk,
            onChange = onBlueCount,
        )
        Text(
            "红 ${machine.redCount} · 蓝 ${machine.blueCount} → ${machine.plannedBets} 注 · " +
                "${machine.plannedPriceYuan} 元",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
    }
}

/** 当前数量命中的预设；自定义时返回 null。 */
private fun presetOf(m: MachineUiState): DrawMode? = DrawMode.entries.firstOrNull { it.redCount == m.redCount && it.blueCount == m.blueCount }

/** 结果条上的一行说明：命中预设用预设名，否则标"自定义"。 */
private fun countLabel(m: MachineUiState): String = presetOf(m)?.label ?: "自定义 ${m.redCount} 红 ${m.blueCount} 蓝"

/** 数量滑条：红蓝各一条，值一定是整数，拖动时联动夹取在 ViewModel 里做。 */
@Composable
private fun CountSlider(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    color: Color,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SketchPalette.Ink)
            Text(
                "$value 个",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) color else SketchPalette.InkFaint,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) - 1,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = SketchPalette.InkFaint,
                disabledThumbColor = SketchPalette.InkFaint,
                disabledActiveTrackColor = SketchPalette.InkFaint,
                disabledInactiveTrackColor = SketchPalette.InkFaint,
            ),
        )
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
