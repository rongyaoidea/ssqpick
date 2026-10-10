package com.ssqpick.feature.machine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
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
import com.ssqpick.designsystem.theme.SsqTheme
import com.ssqpick.rules.HistoryRecord
import com.ssqpick.rules.OddsInfo

/** 舞台底色，与渲染器清屏色保持一致。 */
private val StageInk = Color(0xFF0E1116)

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

    // 3D 舞台**必须**放在滚动容器之外。
    // GLSurfaceView 继承自 SurfaceView，它的 surface 由系统单独合成，
    // 不跟随父视图滚动；放进 verticalScroll / LazyColumn 会渲染在错误位置
    // 甚至完全不显示（实测表现为整块空白）。所以这里固定舞台、只滚动下方内容。
    Column(modifier = modifier.fillMaxSize()) {
        MachineStage(
            viewModel = viewModel,
            ui = ui,
            modifier = Modifier
                .fillMaxWidth()
                .height(SsqDimens.stageHeight),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenColumn {
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
}

/**
 * 3D 舞台。作为视觉焦点，外层加圆角与深色底。
 *
 * 两处容易踩的坑：
 * 1. `onResume()` 只在生命周期**状态切换**时回调。合成开始时通常已经 RESUMED，
 *    监听器不会重放事件，所以必须显式补一次，否则渲染线程永远不会启动。
 * 2. SurfaceView 不能放在可滚动容器里（见 [MachineScreen] 注释）。
 */
@Composable
private fun MachineStage(
    viewModel: MachineViewModel,
    ui: DrawUiState,
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

        // 合成那一刻生命周期往往已处于 RESUMED，observer 不会再收到 ON_RESUME，
        // 这里主动补上，GLSurfaceView 才会真正开始渲染。
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
        }
    }

    // 舞台底色固定用深色：与 GL 的清屏色（0.055, 0.063, 0.086）一致，
    // 浅色主题下也能一眼看出舞台的边界，摇出的球也更醒目。
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(StageInk),
    ) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize(),
        )

        // GL 失败时给出明确提示。最怕的是"一片空白"——用户无法区分是没渲染、
        // 是设备不支持、还是代码错了。
        ui.machine.glError?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "3D 渲染初始化失败：" + msg +
                        "选号功能仍可正常使用，只是缺少摇号动画。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
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
                val border by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "modeBorder",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
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
        trailing = { InfoChip("${m.mode.label} · ${m.betCount} 注 / ${m.priceYuan} 元") },
    ) {
        AnimatedVisibility(visible = m.isRunning, enter = fadeIn(tween(160))) {
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

        // 主按钮文案随状态变化，比"一个按钮配三种不可见的禁用理由"清楚
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
        if (m.mode.redCount == 0) {
            0f
        } else {
            m.redEmitted.toFloat() / m.mode.redCount
        }

    MachineUiState.Phase.BLUE_DRAWING -> m.blueEmitted.toFloat() / m.mode.blueCount

    else -> 1f
}
