package com.ssqpick.store.network

import com.ssqpick.model.Draw
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 官方开奖接口客户端。
 *
 * 三点都是实测出来的：
 * 1. 必须带 `Referer`，否则服务端直接拒绝请求；
 * 2. 官方只提供 2013-01-01 起的数据（约 2000+ 期），更早的期号/日期查询
 *    一律返回空（state=1）。这是服务端的限制，不是客户端的 bug；
 * 3. `issueCount` 放大到 3000 时一次即可取回全部历史（约 1.7MB），
 *    不存在分页截断——所以"全量回填"是一个请求的事。
 */
class CwlApi(
    private val client: HttpClient = defaultClient(),
) {
    companion object {
        private const val ENDPOINT = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice"
        private const val GAME = "ssq"
        private const val REFERER = "https://www.cwl.gov.cn/ygkj/wqkjgg/ssq/"

        /** 实测：3000 覆盖 2013 至今全部（现约 2073 期）且无截断。 */
        private const val FULL_HISTORY_COUNT = 3000
    }

    /** 抓取最近若干期，按时间倒序返回（新→旧）。 */
    suspend fun fetchRecent(issueCount: Int = 100): List<Draw> = request {
        parameter("name", GAME)
        parameter("issueCount", issueCount)
        parameter("pageNo", 1)
        parameter("pageSize", issueCount)
    }

    /**
     * 全量历史（2013 至今，2000+ 期）。约 1.7MB，下载慢时要用长超时，
     * 只在首次同步或本地历史明显不完整时调用。
     */
    suspend fun fetchFullHistory(): List<Draw> = request(longTimeout = true) {
        parameter("name", GAME)
        parameter("issueCount", FULL_HISTORY_COUNT)
        parameter("pageNo", 1)
        parameter("pageSize", FULL_HISTORY_COUNT)
    }

    /** 按期号区间抓取。 */
    suspend fun fetchRange(startCode: String, endCode: String): List<Draw> = request {
        parameter("name", GAME)
        parameter("issueStart", startCode)
        parameter("issueEnd", endCode)
    }

    private suspend fun request(
        longTimeout: Boolean = false,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): List<Draw> {
        val raw = client.get(ENDPOINT) {
            header("Referer", REFERER)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            if (longTimeout) {
                // 1.7MB 在慢网下可能超过全局 30s，单独放宽
                timeout { requestTimeoutMillis = 120_000 }
            }
            block()
        }.let { resp ->
            if (!resp.status.isSuccess()) throw IllegalStateException("HTTP ${resp.status}")
            resp.bodyAsText()
        }
        return parse(raw)
    }

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun parse(raw: String): List<Draw> {
        val parsed = jsonConfig.decodeFromString<CwlResponse>(raw)
        if (parsed.state != 0) {
            throw IllegalStateException("接口返回 state=${parsed.state}: ${parsed.message}")
        }
        return parsed.result.mapNotNull { it.toDraw() }
    }
}

fun defaultClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
}
