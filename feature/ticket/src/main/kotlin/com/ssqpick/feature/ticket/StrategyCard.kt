package com.ssqpick.feature.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.ssqpick.rules.TicketRules
import com.ssqpick.rules.strategy.StrategyKind
import kotlin.math.roundToInt

/**
 * 策略选号卡。
 *
 * 只做“历史统计打分 + 选号”，不预测下一期。生成后直接替换选号草稿，
 * 下面的“保存这注”仍然走原有的校验/保存路径。
 */
@Composable
fun StrategyCard(
    strategy: StrategyUiState,
    drawCount: Long,
    onKind: (StrategyKind) -> Unit,
    onLookback: (Int?) -> Unit,
    onRedCount: (Int) -> Unit,
    onBlueCount: (Int) -> Unit,
    onGenerate: () -> Unit,
) {
    val enabled = !strategy.busy
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(strategy.pick != null) {
        if (strategy.pick != null) expanded = false
    }
    SketchCard(seed = "ticket-strategy", tilt = 0.2f) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("策略选号", modifier = Modifier.weight(1f))
            SketchChip(label = "历史统计", seed = "strategy-tag", fill = null)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "用过去 N 期的统计给号码打分；不是预测，单注中奖概率不变。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))

        if (expanded) {
            Text("策略", style = MaterialTheme.typography.labelMedium, color = SketchPalette.InkSoft)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                StrategyKind.entries.forEach { kind ->
                    StrategyChoice(
                        label = kind.shortLabel,
                        selected = kind == strategy.kind,
                        enabled = enabled,
                        onClick = { onKind(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                strategy.kind.description,
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )

            Spacer(Modifier.height(10.dp))
            CountSlider(
                label = "红球数量",
                value = strategy.redCount,
                range = TicketRules.RED_PICK..TicketRules.MAX_RED_COMPOUND,
                enabled = enabled,
                color = SketchPalette.RedInk,
                onChange = onRedCount,
            )
            CountSlider(
                label = "蓝球数量",
                value = strategy.blueCount,
                range = 1..TicketRules.BLUE_TOTAL,
                enabled = enabled,
                color = SketchPalette.BlueInk,
                onChange = onBlueCount,
            )
            Text(
                "${strategy.redCount} 红 + ${strategy.blueCount} 蓝 → ${strategy.betCount} 注 / ${strategy.priceYuan} 元",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )

            Spacer(Modifier.height(10.dp))
            Text("回看窗口", style = MaterialTheme.typography.labelMedium, color = SketchPalette.InkSoft)
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                LOOKBACKS.forEach { window ->
                    StrategyChoice(
                        label = window.second,
                        selected = strategy.lookback == window.first,
                        enabled = enabled,
                        onClick = { onLookback(window.first) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "研究：2019–2026 的 1089 期 walk-forward 未发现稳定最优窗口；默认 100 期只是产品选择。",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkFaint,
            )
            Spacer(Modifier.height(6.dp))
            SketchMiniButton(
                label = "收起参数",
                seed = "strategy-collapse",
                onClick = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${strategy.kind.shortLabel} · ${strategy.lookbackLabel} · " +
                        "${strategy.redCount} 红 ${strategy.blueCount} 蓝",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchPalette.InkSoft,
                    modifier = Modifier.weight(1f),
                )
                SketchMiniButton(
                    label = "调整参数",
                    seed = "strategy-expand",
                    onClick = { expanded = true },
                    enabled = enabled,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${strategy.redCount} 红 + ${strategy.blueCount} 蓝 → ${strategy.betCount} 注 / ${strategy.priceYuan} 元",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
        }

        if (drawCount <= 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "本地还没有开奖数据，先在页面顶部同步。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.RedInk,
            )
        }
        strategy.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = SketchPalette.InkSoft)
        }
        Spacer(Modifier.height(10.dp))
        SketchButton(
            label = when {
                strategy.busy -> "计算中…"
                strategy.pick != null -> "重新生成并填入"
                else -> "生成推荐并填入"
            },
            onClick = onGenerate,
            enabled = enabled && drawCount > 0,
            seed = "strategy-generate",
            tilt = -0.3f,
        )

        strategy.pick?.let { pick ->
            Spacer(Modifier.height(10.dp))
            SketchDivider(seed = "strategy-divider")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("推荐号码", style = MaterialTheme.typography.titleSmall, color = SketchPalette.Ink)
                Text(
                    "${pick.kind.shortLabel} · ${pick.usedDraws} 期",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkSoft,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pick.reds.forEach { NumberBall(number = it, isBlue = false, size = SsqDimens.ballSizeCompact) }
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pick.blues.forEach { NumberBall(number = it, isBlue = true, size = SsqDimens.ballSizeCompact) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                pick.note + " 已填入下方选号草稿，可手改或直接保存。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
            Spacer(Modifier.height(4.dp))
            SketchMetaRow("注数 / 金额", "${strategy.betCount} 注 / ${strategy.priceYuan} 元")
        }
    }
}

private val LOOKBACKS: List<Pair<Int?, String>> = listOf(
    null to "全部",
    30 to "30",
    50 to "50",
    100 to "100",
    200 to "200",
    500 to "500",
    1000 to "1000",
)

@Composable
private fun StrategyChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .rotate(if (selected) -0.8f else 0.6f)
            .sketchBox(
                seed = "strategy-$label",
                filled = selected,
                enabled = enabled,
                corner = 8.dp,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                selected -> SketchPalette.Paper
                enabled -> SketchPalette.InkSoft
                else -> SketchPalette.InkFaint
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

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
