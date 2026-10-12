package com.ssqpick.designsystem.sketch

import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ssqpick.designsystem.theme.SsqMotion
import kotlinx.coroutines.launch

/**
 * Dialog 窗口默认不随键盘调整；显式设 ADJUST_RESIZE，保证底部 inset 含键盘高度。
 *
 * `SOFT_INPUT_ADJUST_RESIZE` 在 API 30+ 标记弃用，但这正是 Material3
 * `ModalBottomSheet` 的既有做法，且没有等价的公开替代（decorFitsSystemWindows
 * 已在 [DialogProperties] 里关掉），所以保留并局部压掉弃用告警。
 */
@Suppress("DEPRECATION")
private fun Window.enableImeResize() {
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

/**
 * 手绘底部抽屉：从屏幕下方升起的纸面，顶部一条手绘边线 + 短横把手。
 *
 * 与 Material3 `ModalBottomSheet` 的取舍：动效和外观都走本项目的语言——
 * 弹簧沿用 [SsqMotion]（阻尼 0.86，过冲极小，禁止 bounce），纸面 + 墨线描边
 * 与 [SketchCard] 同源。遮罩点击、返回键、以及内容里调 [dismiss] 都先播
 * 退场动画、再真正关闭，不会"啪"地消失。
 *
 * 内容超过窗口高度 85% 时内部可滚动；键盘弹出时整块上移（IME 内边距）。
 */
@Composable
fun SketchSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    // 1 = 完全收起（屏幕外），0 = 展开到位；入场/退场都动画化这一个值
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(1f, tween(SsqMotion.QUICK_MS))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = SsqMotion.SPRING_DAMPING,
                stiffness = SsqMotion.SPRING_STIFFNESS,
            ),
        )
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Dialog 窗口默认不随键盘调整；显式设 ADJUST_RESIZE，
        // 保证 safeDrawing 的底部 inset 含键盘高度（imePadding 才有效）
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.enableImeResize()
        }

        val cache = remember { SketchPathCache() }
        val seed = SketchGeometry.seedOf("sketch-sheet")
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxSheetHeight = maxHeight * 0.85f
            Box(Modifier.fillMaxSize()) {
                // 遮罩：和抽屉共用同一条进度淡入淡出，点击即收起
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - progress.value }
                        .background(SketchPalette.Ink.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dismiss() },
                        ),
                )
                Column(
                    modifier = modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { translationY = progress.value * size.height }
                        .drawBehind {
                            // 纸面多画 28dp 到屏幕下方：底部圆角与描边落在屏幕外，
                            // 可见的只有顶边和两侧——像一张从下面抽出来的纸
                            val path = cache.roundRect(
                                size = Size(size.width, size.height + 28.dp.toPx()),
                                corner = 18.dp.toPx(),
                                jitter = 1.6.dp.toPx(),
                                seed = seed,
                            )
                            drawPath(path, SketchPalette.Paper)
                            drawPath(
                                path = path,
                                color = SketchPalette.Ink.copy(alpha = 0.85f),
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                        )
                        .heightIn(max = maxSheetHeight)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 短把手：固定不滚动，暗示"从下面升起来"
                    Box(
                        Modifier
                            .padding(top = 8.dp, bottom = 10.dp)
                            .size(width = 44.dp, height = 4.dp)
                            .background(SketchPalette.InkFaint, RoundedCornerShape(2.dp)),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                    ) {
                        content(::dismiss)
                    }
                }
            }
        }
    }
}
