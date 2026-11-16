package com.ssqpick.feature.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ssqpick.designsystem.component.NumberBall
import com.ssqpick.designsystem.sketch.SketchButton
import com.ssqpick.designsystem.sketch.SketchCard
import com.ssqpick.designsystem.sketch.SketchChip
import com.ssqpick.designsystem.sketch.SketchMiniButton
import com.ssqpick.designsystem.sketch.SketchPalette
import com.ssqpick.designsystem.sketch.SketchSectionTitle
import com.ssqpick.designsystem.sketch.SketchSheet
import com.ssqpick.designsystem.theme.SsqDimens
import com.ssqpick.rules.CoveragePlan
import com.ssqpick.rules.CoveragePlanner
import java.util.Locale

/** 选号页上的入口卡：预算覆盖是“多买覆盖”，不是预测。 */
@Composable
fun CoverageEntryCard(onOpen: () -> Unit) {
    SketchCard(seed = "coverage-entry", tilt = 0.2f) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SketchSectionTitle("预算覆盖", modifier = Modifier.weight(1f))
            SketchChip(label = "覆盖", seed = "coverage-tag", fill = null)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "把预算拆成多注：蓝球先铺满 16 个，红球按使用次数均衡分配。" +
                "提高的是“至少中一个奖”的频率，不是每元期望。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(8.dp))
        SketchMiniButton(
            label = "打开预算助手",
            seed = "coverage-open",
            onClick = onOpen,
            filled = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 预算覆盖助手。
 *
 * 所有号码仍由 CSPRNG 生成；覆盖策略只决定“买几注、蓝球怎么分布”，
 * 不改变单注中奖概率。保存后不自动记账，用户买完仍需点「记录购买」。
 */
@Composable
fun CoverageSheet(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (CoveragePlan) -> Unit,
    initialBudgetYuan: Long = 10L,
) {
    var budgetText by rememberSaveable { mutableStateOf(initialBudgetYuan.toString()) }
    var plan by remember { mutableStateOf<CoveragePlan?>(CoveragePlanner.plan(initialBudgetYuan)) }

    fun regenerate(value: Long) {
        val capped = value.coerceIn(0L, CoveragePlanner.MAX_BUDGET_YUAN)
        budgetText = capped.toString()
        plan = if (capped >= CoveragePlanner.TICKET_PRICE_YUAN) CoveragePlanner.plan(capped) else null
    }

    SketchSheet(onDismiss = { if (!saving) onDismiss() }) { _ ->
        Text("预算覆盖", style = MaterialTheme.typography.titleMedium, color = SketchPalette.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "每注 2 元。蓝球按随机顺序铺满 16 个再循环；红球优先分给还没被用过的号。",
            style = MaterialTheme.typography.bodySmall,
            color = SketchPalette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = budgetText,
            onValueChange = { text ->
                budgetText = text.filter { it.isDigit() }.take(3)
                plan = null
            },
            singleLine = true,
            label = { Text("预算（元）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(10L, 20L, 30L, 50L, 100L).forEach { preset ->
                SketchMiniButton(
                    label = "${preset}元",
                    seed = "coverage-preset-$preset",
                    onClick = { regenerate(preset) },
                    filled = budgetText == preset.toString(),
                    enabled = !saving,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SketchMiniButton(
                label = "生成方案",
                seed = "coverage-generate",
                onClick = {
                    val value = budgetText.toLongOrNull() ?: 0L
                    regenerate(value)
                },
                enabled = !saving,
                filled = true,
                modifier = Modifier.weight(1f),
            )
            SketchMiniButton(
                label = "换一组",
                seed = "coverage-reroll",
                onClick = {
                    val value = plan?.budgetYuan ?: (budgetText.toLongOrNull() ?: 0L)
                    if (value >= CoveragePlanner.TICKET_PRICE_YUAN) {
                        plan = CoveragePlanner.plan(value.coerceAtMost(CoveragePlanner.MAX_BUDGET_YUAN))
                    }
                },
                enabled = !saving && plan != null,
                modifier = Modifier.weight(1f),
            )
        }

        val current = plan
        if (current == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "至少 2 元；单次上限 100 元（50 注）。",
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.RedInk,
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                "${current.tickets.size} 注 · ${current.costYuan} 元 · 任意奖约 " +
                    String.format(Locale.US, "%.1f%%", current.anyPrizeProbability * 100),
                style = MaterialTheme.typography.titleSmall,
                color = SketchPalette.Ink,
            )
            Text(
                if (current.fullBlueCoverage) {
                    "蓝球已铺满 16 个；至少中一个奖的理论概率为 100%"
                } else {
                    "蓝球覆盖 ${current.coveredBlues}/16；再覆盖 ${16 - current.coveredBlues} 个蓝球可到 100%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = SketchPalette.InkSoft,
            )
            if (current.leftoverYuan > 0) {
                Text(
                    "预算剩余 ${current.leftoverYuan} 元（不足一注）",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkFaint,
                )
            }
            Spacer(Modifier.height(8.dp))
            current.tickets.take(8).forEachIndexed { index, ticket ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SketchPalette.InkFaint,
                        modifier = Modifier.width(18.dp),
                    )
                    ticket.reds.forEach { NumberBall(it, isBlue = false, size = SsqDimens.ballSizeSmall) }
                    Text("+", style = MaterialTheme.typography.labelSmall, color = SketchPalette.InkSoft)
                    NumberBall(ticket.blue, isBlue = true, size = SsqDimens.ballSizeSmall)
                }
            }
            if (current.tickets.size > 8) {
                Text(
                    "还有 ${current.tickets.size - 8} 注未预览，保存时会全部写入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = SketchPalette.InkFaint,
                )
            }
            Spacer(Modifier.height(10.dp))
            SketchButton(
                label = if (saving) {
                    "保存中…"
                } else {
                    "保存全部（${current.tickets.size} 注 / ${current.costYuan} 元）"
                },
                onClick = { onSave(current) },
                enabled = !saving,
                seed = "coverage-save",
                tilt = -0.3f,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "任意奖概率是理论估算，开奖每期独立。覆盖只提高中奖频率，不能提高每元期望；" +
                "保存后仍需在彩票店购买并手动记录。",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "覆盖不等于预测 · 请勿超出预算",
            style = MaterialTheme.typography.labelSmall,
            color = SketchPalette.InkFaint,
        )
    }
}
