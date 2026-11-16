package com.ssqpick.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class CoveragePlannerTest {

    @Test
    fun `十元方案是五注且蓝球不重复`() {
        val plan = CoveragePlanner.plan(10, Random(42))
        assertEquals(5, plan.tickets.size)
        assertEquals(10L, plan.costYuan)
        assertEquals(5, plan.tickets.map { it.blue }.distinct().size)
        assertEquals(5, plan.coveredBlues)
        assertEquals(0L, plan.leftoverYuan)
        assertTrue(plan.anyPrizeProbability in 0.32..0.34)
        plan.tickets.forEach {
            assertEquals(6, it.reds.distinct().size)
            assertTrue(it.reds.all { n -> n in 1..33 })
            assertTrue(it.toDraft().isValid)
        }
    }

    @Test
    fun `五注覆盖三十个不同红球`() {
        val plan = CoveragePlanner.plan(10, Random(7))
        val reds = plan.tickets.flatMap { it.reds }
        assertEquals(30, reds.distinct().size)
    }

    @Test
    fun `六注可以覆盖全部红球`() {
        val plan = CoveragePlanner.plan(12, Random(7))
        val reds = plan.tickets.flatMap { it.reds }
        assertEquals(33, reds.distinct().size)
    }

    @Test
    fun `三十二元铺满十六个蓝球且任意奖为百分百`() {
        val plan = CoveragePlanner.plan(32, Random(3))
        assertEquals(16, plan.tickets.size)
        assertEquals((1..16).toSet(), plan.tickets.map { it.blue }.toSet())
        assertTrue(plan.fullBlueCoverage)
        assertEquals(1.0, plan.anyPrizeProbability, 1e-12)
    }

    @Test
    fun `预算不足两元返回空方案`() {
        val plan = CoveragePlanner.plan(1, Random(1))
        assertTrue(plan.tickets.isEmpty())
        assertEquals(0L, plan.costYuan)
        assertEquals(0.0, plan.anyPrizeProbability, 1e-12)
    }

    @Test
    fun `超过单次上限按五十注处理`() {
        val plan = CoveragePlanner.plan(999, Random(1))
        assertEquals(CoveragePlanner.MAX_TICKETS, plan.tickets.size)
        assertEquals(CoveragePlanner.MAX_BUDGET_YUAN, plan.costYuan)
        assertTrue(plan.fullBlueCoverage)
    }

    @Test
    fun `红球集合不重复`() {
        val plan = CoveragePlanner.plan(100, Random(11))
        val sets = plan.tickets.map { it.reds.toSet() }
        assertEquals(sets.size, sets.distinct().size)
    }
}
