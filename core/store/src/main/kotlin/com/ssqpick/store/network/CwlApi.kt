package com.ssqpick.store.network

import com.ssqpick.model.Draw
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
 * 两点都是实测出来的：
 * 1. 必须带 `Referer`，否则服务端直接拒绝请求；
 * 2. 官方只在近几年内提供开奖数据（实测 2013-01-01 起，约 2000+ 期），
 *    更早的期号区间查询返回空数组。这是服务端的限制，不是客户端的 bug。
 */
class CwlApi(
    private val client: HttpClient = defaultClient(),
) {
    companion object {
        private const val ENDPOINT = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice"
        private const val GAME = "ssq"
        private const val REFERER = "https://www.cwl.gov.cn/ygkj/wqkjgg/ssq/"
    }

    /** 抓取最近若干期，按时间倒序返回（新→旧）。 */
    suspend fun fetchRecent(issueCount: Int = 100): List<Draw> = request {
        parameter("name", GAME)
        parameter("issueCount", issueCount)
        parameter("pageNo", 1)
        parameter("pageSize", issueCount)
    }

    /** 按期号区间抓取。 */
    suspend fun fetchRange(startCode: String, endCode: String): List<Draw> = request {
        parameter("name", GAME)
        parameter("issueStart", startCode)
        parameter("issueEnd", endCode)
    }

    private suspend fun request(block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): List<Draw> {
        val raw = client.get(ENDPOINT) {
            header("Referer", REFERER)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
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
