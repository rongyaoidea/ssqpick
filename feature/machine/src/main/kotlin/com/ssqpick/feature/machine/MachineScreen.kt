package com.ssqpick.feature.machine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.BallRow
import com.ssqpick.designsystem.component.EmptyHint
import com.ssqpick.designsystem.component.InfoChip
import com.ssqpick.designsystem.component.ScreenColumn
import com.ssqpick.designsystem.component.SsqCard
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.OddsInfo

/**
 * 摇号机主屏。
 *
 * 主题由 MainActivity 统一提供，这里不再包一层 SsqTheme。
 */
@Composable
fun MachineScreen(
    viewModel: MachineViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenColumn {
            MachineStage(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SsqDimens.stageHeight),
            )

            ModeSelector(
                selected = ui.machine.mode,
                enabled = !ui.machine.isRunning,
                onSelect = viewModel::selectMode,
            )

            ResultPanel(ui = ui, onSave = viewModel::save, onReset = viewModel::reset)

            HistoryPanel(ui.history)
        }
    }
}

/**
 * 3D 舞台。作为视觉焦点，外层加了一圈内描边和圆角，
 * 让 GL 视图和周边 UI 明确分层而不是裸贴一块黑。
 */
@Composable
private fun MachineStage(
    viewModel: MachineViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(viewModel) { MachineView(context, viewModel.controller) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 投注方式选择。用卡片而不是 Chip，长文案（"大复式 10 红 4 蓝"）不会被截断。 */
@Composable
private fun ModeSelector(
    selected: DrawMode,
    enabled: Boolean,
    onSelect: (DrawMode) -> Unit,
) {
    Column {
        Text("投注方式", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrawMode.entries.forEach { mode ->
                val isSelected = mode == selected
                val border by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "modeBorder",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .border(1.dp, border, RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) { onSelect(mode) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
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
private fun ResultPanel(
    ui: DrawUiState,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    val m = ui.machine
    SsqCard(
        title = "开奖结果",
        trailing = {
            InfoChip("${m.mode.label} · ${m.betCount} 注 / ${m.priceYuan} 元")
        },
    ) {
        AnimatedVisibility(
            visible = m.isRunning,
            enter = fadeIn(tween(160)),
        ) {
            LinearProgressIndicator(
                progress = { progressOf(m) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            m.phaseLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "红球",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BallRow(
            numbers = m.redNumbers,
            isBlue = false,
            ballSize = SsqDimens.ballSize,
            placeholderCount = m.mode.redCount,
        )

        Text(
            "蓝球",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BallRow(
            numbers = m.blueNumbers,
            isBlue = true,
            ballSize = SsqDimens.ballSize,
            placeholderCount = m.mode.blueCount,
        )

        Spacer(Modifier.height(4.dp))

        // 只有摇完才允许保存，主按钮明确表达状态
        Button(
            onClick = onSave,
            enabled = m.isComplete && !ui.saved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    ui.saved -> "已保存"
                    m.isComplete -> "保存这注（${m.betCount} 注）"
                    else -> "摇号中…"
                },
            )
        }
        OutlinedButton(
            onClick = onReset,
            enabled = !m.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("重新摇") }

        ui.saveMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
                // 把"没中过"的概率背景讲清楚，避免用户误以为号"冷"/"旺"
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

private fun progressOf(m: MachineUiState): Float = when (m.phase) {
    MachineUiState.Phase.RED_MIXING, MachineUiState.Phase.BLUE_MIXING -> 0.5f

    MachineUiState.Phase.RED_DRAWING ->
        if (m.mode.redCount == 0) 0f else m.redEmitted.toFloat() / m.mode.redCount

    MachineUiState.Phase.BLUE_DRAWING -> m.blueEmitted.toFloat() / m.mode.blueCount

    else -> 1f
}
