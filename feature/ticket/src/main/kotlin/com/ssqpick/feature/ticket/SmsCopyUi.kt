package com.ssqpick.feature.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.sketchBox
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.SmsCommand

/** 复制目标：单票或多注合并（弹层按目标类型算指令）。 */
internal sealed interface SmsCopyTarget {
    data class Single(val ticketId: Long) : SmsCopyTarget

    data object Multi : SmsCopyTarget
}

/** 底部反馈纸条的内容；target 为 null 时是纯提示（无"调整"入口）。 */
internal data class SmsCopyFeedback(
    val title: String,
    val body: String,
    val target: SmsCopyTarget? = null,
    val recordTicketId: Long? = null,
)

/** 复制后的手绘纸条：指令全文上屏（发送前可核对）+ 快捷动作，几秒后自动消失。 */
@Composable
internal fun SmsCopyStrip(
    feedback: SmsCopyFeedback,
    onAdjust: (() -> Unit)?,
    onRecord: (() -> Unit)?,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .sketchBox(seed = "copy-strip-${feedback.body.hashCode()}", filled = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                feedback.title,
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                feedback.body,
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.Ink,
            )
            if (onRecord != null || onAdjust != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onRecord?.let {
                        SketchMiniButton(label = "记一笔购买", seed = "strip-record", onClick = it)
                    }
                    onAdjust?.let {
                        SketchMiniButton(label = "调整倍数/期数", seed = "strip-adjust", onClick = it)
                    }
                }
            }
        }
    }
}

/** 多注勾选栏：出现于勾选了单式票之后。 */
@Composable
internal fun MultiCopyBar(
    count: Int,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .sketchBox(seed = "multi-copy-bar", filled = false),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "已选 $count 注（单式多注）",
                style = MaterialTheme.typography.labelSmall,
                color = SketchPalette.InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SketchMiniButton(
                    label = "复制多注指令",
                    seed = "multi-copy",
                    onClick = onCopy,
                    filled = true,
                )
                SketchMiniButton(label = "清空", seed = "multi-clear", onClick = onClear)
            }
        }
    }
}

/** 指令设置弹层：倍数/期数 + 实时预览。默认 1 倍、不带期数。 */
@Composable
internal fun SmsSettingsDialog(
    drafts: List<TicketDraft>,
    multi: Boolean,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    var times by remember { mutableIntStateOf(1) }
    var periods by remember { mutableIntStateOf(1) }
    val command = remember(drafts, times, periods, multi) {
        if (multi) {
            SmsCommand.multi(drafts, times, periods)
        } else {
            drafts.firstOrNull()?.let { SmsCommand.of(it, times, periods) }
        }
    }
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
            SketchCard(seed = "sms-dialog", tilt = 0f) {
                Text(
                    if (multi) "多注指令设置（${drafts.size} 注单式）" else "投注指令设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = SketchPalette.Ink,
                )
                Spacer(Modifier.height(10.dp))
                StepperRow(label = "倍数", value = times, range = 1..SmsCommand.MAX_TIMES) {
                    times = it
                }
                Spacer(Modifier.height(6.dp))
                StepperRow(label = "期数", value = periods, range = 1..SmsCommand.MAX_PERIODS) {
                    periods = it
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "期数为 1 表示不带（单期）",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkFaint,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    command ?: "该票不支持短信指令",
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchPalette.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "发送后将按指令金额实际扣款；需已开通电话投注并实名。",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkFaint,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SketchMiniButton(
                        label = "取消",
                        seed = "sms-cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    SketchMiniButton(
                        label = "复制",
                        seed = "sms-copy-ok",
                        onClick = { command?.let(onCopy) },
                        filled = true,
                        enabled = command != null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SketchPalette.Ink)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SketchMiniButton(
                label = "−",
                seed = "step-$label-down",
                onClick = { onChange(value - 1) },
                enabled = value > range.first,
            )
            Text("$value", style = MaterialTheme.typography.titleMedium, color = SketchPalette.Ink)
            SketchMiniButton(
                label = "+",
                seed = "step-$label-up",
                onClick = { onChange(value + 1) },
                enabled = value < range.last,
            )
        }
    }
}
