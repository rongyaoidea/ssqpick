package com.ssqpick.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssqpick.designsystem.component.BallRow
import com.ssqpick.designsystem.component.InfoChip
import com.ssqpick.designsystem.theme.SsqTheme
import com.ssqpick.rules.OddsInfo

/**
 * 摇号机主界面：3D 机器 + 模式选择 + 结果与保存。
 *
 * 说明：屏幕上的"开始摇号"是 UI 触点，实际选号由服务端无关的本地
 * CSPRNG 完成，3D 机器只负责把结果演出来（见 [MachineController] 的注释）。
 */
@Composable
fun MachineScreen(
    viewModel: MachineViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    SsqTheme {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("双色球选号机", style = MaterialTheme.typography.headlineMedium)
            Text(
                "号码由本地 CSPRNG 生成，3D 机器只负责把结果摇出来",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            MachineCanvas(viewModel, Modifier.fillMaxWidth().height(320.dp))

            Spacer(Modifier.height(12.dp))
            PhaseHeader(ui.machine)

            Spacer(Modifier.height(8.dp))
            ModeSelector(
                selected = ui.machine.mode,
                enabled = !ui.machine.isRunning,
                onSelect = viewModel::selectMode,
            )

            Spacer(Modifier.height(12.dp))
            ResultCard(ui, onSave = viewModel::save, onReset = viewModel::reset)

            Spacer(Modifier.height(12.dp))
            HistoryCard(ui.history)
        }
    }
}

@Composable
private fun MachineCanvas(viewModel: MachineViewModel, modifier: Modifier = Modifier) {
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PhaseHeader(machine: MachineUiState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(machine.phaseLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            InfoChip("${machine.mode.label} · ${machine.betCount} 注 / ${machine.priceYuan} 元")
        }
        if (machine.isRunning) {
            val progress = when (machine.phase) {
                MachineUiState.Phase.RED_MIXING, MachineUiState.Phase.BLUE_MIXING -> 0.5f
                MachineUiState.Phase.RED_DRAWING ->
                    if (machine.mode.redCount == 0) 0f else machine.redEmitted.toFloat() / machine.mode.redCount
                MachineUiState.Phase.BLUE_DRAWING -> machine.blueEmitted.toFloat() / machine.mode.blueCount
                else -> 1f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ModeSelector(
    selected: DrawMode,
    enabled: Boolean,
    onSelect: (DrawMode) -> Unit,
) {
    Column {
        Text("投注方式", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrawMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    enabled = enabled,
                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Composable
private fun ResultCard(ui: DrawUiState, onSave: () -> Unit, onReset: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            val m = ui.machine
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("开奖结果", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${m.redEmitted}/6 红 · ${m.blueEmitted}/${m.mode.blueCount} 蓝",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("红球", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            BallRow(
                numbers = m.redNumbers,
                isBlue = false,
                placeholderCount = m.mode.redCount,
            )
            Spacer(Modifier.height(10.dp))
            Text("蓝球", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            BallRow(numbers = m.blueNumbers, isBlue = true, placeholderCount = m.mode.blueCount)

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    enabled = m.isComplete && !ui.saved,
                    modifier = Modifier.weight(1f),
                ) { Text(if (ui.saved) "已保存" else "保存这注") }
                OutlinedButton(
                    onClick = { if (m.isRunning) Unit else onReset() },
                    modifier = Modifier.weight(1f),
                ) { Text("重新摇") }
            }
            ui.saveMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(record: com.ssqpick.rules.HistoryRecord?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("历史战绩", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (record == null) {
                Text(
                    "摇一注之后，这里会告诉你这组号码在全部历史开奖里最好中过什么。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(OddsInfo.describe(record), style = MaterialTheme.typography.bodyMedium)
                if (record.hasEverWon) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "第 ${record.highestTierDrawCode} 期（${record.highestTierDate}）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "当期单注奖金 ${record.highestTierAmountYuan} 元",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        OddsInfo.logProbabilityText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "一注在 ${record.checkedDraws} 期里出现的概率只有 " +
                            "%.4f%%——没中过任何奖才是正常结果。".format(
                                OddsInfo.appearProbability(record.checkedDraws) * 100,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
