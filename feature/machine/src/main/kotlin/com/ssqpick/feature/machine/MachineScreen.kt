package com.ssqpick.feature.machine

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.BallRow
import com.ssqpick.designsystem.component.EmptyHint
import com.ssqpick.designsystem.component.InfoChip
import com.ssqpick.designsystem.component.ScreenColumn
import com.ssqpick.designsystem.component.SsqCard
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.designsystem.theme.SsqMotion
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.OddsInfo

/**
 * 摇号机主屏。
 *
 * 主题由 MainActivity 统一提供，这里不再包一层 SsqTheme。
 *
 * 整屏可滚动：舞台是 Compose `Canvas`，不像 SurfaceView 那样需要独立合成层，
 * 放进滚动容器完全安全——这正是从 3D 换到 2D 顺带解决的问题。
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
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenColumn {
            SsqCard(
                title = "摇号机",
                trailing = { InfoChip(m.phaseLabel) },
            ) {
                DrumStage(
                    controller = viewModel.controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SsqDimens.drumStageHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                ResultStrip(m)

                ActionBar(
                    machine = m,
                    saved = ui.saved,
                    onStart = viewModel::start,
                    onReset = viewModel::reset,
                    onSave = viewModel::save,
                )

                ui.saveMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ModeSelector(
                selected = m.mode,
                enabled = !m.isRunning,
                onSelect = viewModel::selectMode,
            )

            HistoryPanel(ui.history)
        }
    }
}

/**
 * 结果条：6 红 + 1 蓝，一行呈现。
 *
 * 这是全屏唯一需要"读清号码"的地方，所以球最大；
 * 鼓里的球只负责动感，号码在球够大时才显示。
 */
@Composable
private fun ResultStrip(m: MachineUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BallRow(
            numbers = m.redNumbers,
            isBlue = false,
            ballSize = SsqDimens.ballSize,
            placeholderCount = m.mode.redCount,
        )
        Text(
            "+",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        BallRow(
            numbers = m.blueNumbers,
            isBlue = true,
            ballSize = SsqDimens.ballSize,
            placeholderCount = m.mode.blueCount,
        )
    }
}

/** 主操作区。按钮文案随状态变化，比"一个按钮配三种不可见的禁用理由"清楚。 */
@Composable
private fun ActionBar(
    machine: MachineUiState,
    saved: Boolean,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            machine.isComplete -> {
                Button(onClick = onSave, enabled = !saved, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (saved) {
                            "已保存"
                        } else {
                            "保存这注（${machine.betCount} 注 / ${machine.priceYuan} 元）"
                        },
                    )
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("重新摇")
                }
            }

            machine.isRunning -> {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(machine.phaseLabel)
                }
            }

            else -> {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("开始摇号")
                }
            }
        }
    }
}

/** 投注方式。用卡片而不是 Chip：长文案（"大复式 10 红 4 蓝"）不会被截断。 */
@Composable
private fun ModeSelector(
    selected: DrawMode,
    enabled: Boolean,
    onSelect: (DrawMode) -> Unit,
) {
    Column {
        Text(
            "投注方式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrawMode.entries.forEach { mode ->
                val isSelected = mode == selected
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    animationSpec = spring(
                        dampingRatio = SsqMotion.SPRING_DAMPING,
                        stiffness = SsqMotion.SPRING_STIFFNESS,
                    ),
                    label = "modeBorder",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) { onSelect(mode) }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
    SsqCard(title = "历史战绩", container = MaterialTheme.colorScheme.surfaceVariant) {
        if (record == null) {
            EmptyHint("摇一注之后，这里会告诉你这组号码在全部历史开奖里中过什么。")
        } else {
            Text(OddsInfo.describe(record), style = MaterialTheme.typography.bodyMedium)
            if (record.hasEverWon) {
                Text(
                    "第 ${record.highestTierDrawCode} 期（${record.highestTierDate}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "当期单注奖金 ${record.highestTierAmountYuan} 元",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // 把"没中过"的概率背景讲清楚，避免用户误以为某组号"冷"或"旺"
                Text(
                    OddsInfo.logProbabilityText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "一注在 ${record.checkedDraws} 期里出现的概率只有 " +
                        "%.4f%%，没中过任何奖才是正常结果。".format(
                            OddsInfo.appearProbability(record.checkedDraws) * 100,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
