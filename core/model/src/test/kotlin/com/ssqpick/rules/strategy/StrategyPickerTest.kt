package com.ssqpick.rules.strategy

import com.ssqpick.model.Draw
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyPickerTest {

    private fun draw(reds: List<Int>, blue: Int = 1, index: Int = 0) = Draw(
        code = "S$index",
        date = LocalDate(2026, 1, 1),
        red = reds,
        blue = blue,
    )

    private fun baseDraw(index: Int, include: Int = 1, blue: Int = 1): Draw {
        val reds = buildList {
            add(include)
            var n = 2
            while (size < 6) {
                val x = if (n == include) n + 1 else n
                if (x !in this && x <= 33) add(x)
                n++
            }
        }
        return draw(reds, blue, index)
    }

    @Test
    fun `推荐数量与范围正确`() {
        val draws = (0 until 60).map { baseDraw(it) }
        val pick = StrategyPicker.recommend(
            StrategyConfig(
                kind = StrategyKind.BAYES,
                redCount = 8,
                blueCount = 3,
                lookback = 60,
            ),
            draws,
        )
        assertEquals(8, pick.reds.size)
        assertEquals(3, pick.blues.size)
        assertEquals(pick.reds.sorted(), pick.reds)
        assertEquals(pick.blues.sorted(), pick.blues)
        assertEquals(8, pick.reds.toSet().size)
        assertEquals(3, pick.blues.toSet().size)
        assertTrue(pick.reds.all { it in 1..33 })
        assertTrue(pick.blues.all { it in 1..16 })
        assertEquals(60, pick.usedDraws)
        assertEquals(60, pick.lookback)
    }

    @Test
    fun `同参数结果可复现`() {
        val draws = (0 until 100).map { i ->
            draw((1..33).shuffled(kotlin.random.Random(i)).take(6), (i % 16) + 1, i)
        }
        val config = StrategyConfig(StrategyKind.ENSEMBLE, 7, 2, lookback = 100)
        assertEquals(
            StrategyPicker.recommend(config, draws),
            StrategyPicker.recommend(config, draws),
        )
    }

    @Test
    fun `贝叶斯会把一直出现的号排进推荐`() {
        val draws = (0 until 50).map { baseDraw(it, include = 7) }
        val pick = StrategyPicker.recommend(
            StrategyConfig(StrategyKind.BAYES, 6, 1, lookback = 50),
            draws,
        )
        assertTrue("热号 7 应在推荐里：${pick.reds}", 7 in pick.reds)
    }

    @Test
    fun `遗漏回归会把久未出现的号排进推荐`() {
        val draws = (0 until 40).map { i ->
            // 1..32 轮转出现；33 从不出
            val start = (i % 32) + 1
            val reds = (0 until 6).map { ((start - 1 + it) % 32) + 1 }
            draw(reds, (i % 16) + 1, i)
        }
        val pick = StrategyPicker.recommend(
            StrategyConfig(StrategyKind.COLD, 6, 1, lookback = 40),
            draws,
        )
        assertTrue("从未出现的 33 应在遗漏推荐里：${pick.reds}", 33 in pick.reds)
    }

    @Test
    fun `马尔可夫使用上一期作为条件`() {
        val draws = listOf(
            draw(listOf(1, 10, 11, 12, 13, 14), blue = 1, index = 0),
            draw(listOf(2, 20, 21, 22, 23, 24), blue = 2, index = 1),
            draw(listOf(1, 10, 11, 12, 13, 14), blue = 1, index = 2),
        )
        val pick = StrategyPicker.recommend(
            StrategyConfig(StrategyKind.MARKOV, 6, 1, lookback = 3),
            draws,
        )
        assertTrue("上一期含 1，转移目标 2 应进推荐：${pick.reds}", 2 in pick.reds)
        assertEquals("蓝转移 1→2 应推荐 2", listOf(2), pick.blues)
    }

    @Test
    fun `综合排序和衰减也能给出合法推荐`() {
        val draws = (0 until 80).map { i ->
            draw((1..33).shuffled(kotlin.random.Random(i + 7)).take(6), (i % 16) + 1, i)
        }
        for (kind in StrategyKind.entries) {
            val pick = StrategyPicker.recommend(StrategyConfig(kind, 6, 1, 80), draws)
            assertEquals(kind, pick.kind)
            assertEquals(6, pick.reds.size)
            assertEquals(1, pick.blues.size)
            assertTrue(pick.note.isNotBlank())
        }
    }

    @Test
    fun `全部历史与固定窗口的 usedDraws 不同`() {
        val draws = (0 until 120).map { baseDraw(it) }
        assertEquals(
            120,
            StrategyPicker.recommend(StrategyConfig(lookback = null), draws).usedDraws,
        )
        assertEquals(
            100,
            StrategyPicker.recommend(StrategyConfig(lookback = 100), draws).usedDraws,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `红球少于 6 个被拒绝`() {
        StrategyPicker.recommend(StrategyConfig(redCount = 5), listOf(baseDraw(0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `注数超过上限被拒绝`() {
        StrategyPicker.recommend(StrategyConfig(redCount = 20, blueCount = 16), listOf(baseDraw(0)))
    }
}
