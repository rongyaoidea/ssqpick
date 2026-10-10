package com.ssqpick.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonteCarloTest {

    @Test
    fun `同种子结果可复现`() {
        val a = simulateMonteCarlo(paths = 40, drawsPerPath = 200, seed = 42)
        val b = simulateMonteCarlo(paths = 40, drawsPerPath = 200, seed = 42)
        assertEquals(a, b)
    }

    @Test
    fun `成本与统计口径`() {
        val r = simulateMonteCarlo(paths = 40, drawsPerPath = 200, seed = 7)
        assertEquals(40, r.paths)
        assertEquals(200, r.drawsPerPath)
        assertEquals(40L * 200L * 2L, r.totalCostYuan)
        assertTrue(r.worstRoi <= r.medianRoi)
        assertTrue(r.medianRoi <= r.bestRoi)
        assertTrue(r.profitableShare in 0.0..1.0)
        assertTrue(r.totalPrizeYuan >= 0)
        assertTrue(r.tierHits.keys.all { it in com.ssqpick.model.PrizeTier.entries })
    }

    @Test
    fun `长期期望为负`() {
        // 8000 注的总样本：中位回报率必然显著小于 1
        val r = simulateMonteCarlo(paths = 40, drawsPerPath = 200, seed = 2026)
        assertTrue("medianRoi=${r.medianRoi}", r.medianRoi < 1.0)
        assertTrue("profitableShare=${r.profitableShare}", r.profitableShare < 0.5)
    }
}
