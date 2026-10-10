package com.ssqpick.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ssqpick.model.Draw
import com.ssqpick.model.PrizeTier
import com.ssqpick.store.mapper.toDraw
import com.ssqpick.store.network.CwlApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端验证："官方响应 → 解析 → syncFromOfficial → 数据库 → 读回"。
 *
 * 用 MockEngine 冒充官方接口 + 真实的 SQLDelight 内存库跑**生产代码路径**
 * （DrawRepositoryImpl 原封不动），所以任何一处绑定/编码出错都会在这里炸。
 */
class DrawSyncPersistenceTest {

    /** 与官方字段一致的样例响应：两期，含一二等奖金额。 */
    private val responseJson = """
        {"state":0,"result":[
          {"code":"2026115","date":"2026-10-08","red":"02,04,13,14,15,30","blue":"08",
           "sales":"333000000","poolmoney":"917243647",
           "prizegrades":[{"type":1,"typenum":"1","typemoney":"10000000"},
                          {"type":3,"typenum":"973","typemoney":"3000"}]},
          {"code":"2026116","date":"2026-10-10","red":"01,05,09,18,22,33","blue":"12",
           "sales":"350000000","poolmoney":"925000000",
           "prizegrades":[{"type":1,"typenum":"5","typemoney":"6000000"},
                          {"type":2,"typenum":"88","typemoney":"180000"}]}
        ]}
    """.trimIndent()

    private fun newDatabase(): SsqDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SsqDatabase.Schema.create(driver)
        return SsqDatabase(driver)
    }

    private fun mockApi(): CwlApi {
        val engine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return CwlApi(HttpClient(engine) { expectSuccess = false })
    }

    @Test
    fun `同步两期写入后逐字段读回一致`() = runBlocking {
        val db = newDatabase()
        val repo = DrawRepositoryImpl(mockApi(), db.drawQueries)

        val result = repo.syncFromOfficial()
        assertTrue("首次同步应成功: $result", result is SyncResult.Success)
        result as SyncResult.Success
        assertEquals(2, result.imported)
        assertEquals(2L, result.totalDraws)
        assertEquals(2L, repo.count())

        // 读回并逐字段核对（走与生产相同的 mapper）
        val rows = db.drawQueries.selectAllDraws().executeAsList().map { it.toDraw() }
        assertEquals(listOf("2026116", "2026115"), rows.map { it.code })

        val newest = rows.first()
        assertEquals(LocalDate.parse("2026-10-10"), newest.date)
        assertEquals(listOf(1, 5, 9, 18, 22, 33), newest.red)
        assertEquals(12, newest.blue)
        assertEquals(350_000_000L, newest.sales)
        assertEquals(925_000_000L, newest.poolAmount)
        assertEquals(
            "一等奖单注金额应落库",
            6_000_000L,
            newest.prizeGrades.first { it.tier == PrizeTier.FIRST }.amountPerTicket,
        )
        assertEquals(
            "二等奖单注金额应落库",
            180_000L,
            newest.prizeGrades.first { it.tier == PrizeTier.SECOND }.amountPerTicket,
        )

        val older = rows.last()
        assertEquals(LocalDate.parse("2026-10-08"), older.date)
        assertEquals(listOf(2, 4, 13, 14, 15, 30), older.red)
        assertEquals(8, older.blue)
        // 三等奖不在库里单列（固定奖由奖级表推导），只保留一二等奖金额
        assertTrue(older.prizeGrades.none { it.tier == PrizeTier.THIRD })
    }

    @Test
    fun `重复同步不重不漏_第二次为已是最新`() = runBlocking {
        val db = newDatabase()
        val repo = DrawRepositoryImpl(mockApi(), db.drawQueries)
        repo.syncFromOfficial()

        val second = repo.syncFromOfficial()
        assertEquals(SyncResult.AlreadyUpToDate, second)
        assertEquals("code 是主键，INSERT OR IGNORE 不得重复", 2L, repo.count())
    }

    @Test
    fun `最新一期查询命中正确`() = runBlocking {
        val db = newDatabase()
        val repo = DrawRepositoryImpl(mockApi(), db.drawQueries)
        repo.syncFromOfficial()

        val newestCode = db.drawQueries.selectNewestDraw().executeAsOneOrNull()?.code
        assertEquals("2026116", newestCode)
    }

    @Test
    fun `手写插入与读回全字段一致`() = runBlocking {
        val db = newDatabase()
        val repo = DrawRepositoryImpl(mockApi(), db.drawQueries)
        val draw = Draw(
            code = "2026999",
            date = LocalDate(2026, 12, 31),
            red = listOf(3, 7, 11, 19, 25, 33),
            blue = 16,
            sales = 123_456_789L,
            poolAmount = 987_654_321L,
            prizeGrades = listOf(
                com.ssqpick.model.DrawPrizeGrade(PrizeTier.FIRST, 0, 5_000_000L),
                com.ssqpick.model.DrawPrizeGrade(PrizeTier.SECOND, 0, 123_456L),
            ),
        )
        repo.insertAll(listOf(draw))

        val back = db.drawQueries.selectAllDraws().executeAsList().map { it.toDraw() }.single()
        assertEquals(draw.code, back.code)
        assertEquals(draw.date, back.date)
        assertEquals(draw.red, back.red)
        assertEquals(draw.blue, back.blue)
        assertEquals(draw.sales, back.sales)
        assertEquals(draw.poolAmount, back.poolAmount)
        assertEquals(
            draw.prizeGrades.map { it.tier to it.amountPerTicket },
            back.prizeGrades.map { it.tier to it.amountPerTicket },
        )
    }

    @Test
    fun `脏数据被解析层跳过_不污染数据库`() = runBlocking {
        // 第二条 red 只有 5 个号：解析时应被整体跳过，而不是写半条进去
        val dirty = """
            {"state":0,"result":[
              {"code":"2026116","date":"2026-10-10","red":"01,05,09,18,22,33","blue":"12"},
              {"code":"2026117","date":"2026-10-11","red":"01,05,09,18,22","blue":"12"}
            ]}
        """.trimIndent()
        val engine = MockEngine {
            respond(
                content = dirty,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val db = newDatabase()
        val repo = DrawRepositoryImpl(CwlApi(HttpClient(engine) { expectSuccess = false }), db.drawQueries)

        val result = repo.syncFromOfficial()
        assertTrue(result is SyncResult.Success)
        assertEquals(1L, repo.count())
        assertEquals("2026116", db.drawQueries.selectNewestDraw().executeAsOneOrNull()?.code)
    }
}
