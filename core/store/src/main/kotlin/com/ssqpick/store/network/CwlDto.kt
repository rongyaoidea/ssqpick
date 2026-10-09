package com.ssqpick.store.network

import com.ssqpick.model.Draw
import com.ssqpick.model.DrawPrizeGrade
import com.ssqpick.model.PrizeTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 中国福利彩票发行管理中心官方开奖公告接口的响应结构。
 *
 * 字段名与官方字段保持一致（拼音命名），映射在 [toDraw] 中显式完成，
 * 这样官方接口若改动字段顺序或增加字段不影响领域模型。
 */
@Serializable
data class CwlResponse(
    val state: Int = -1,
    val message: String? = null,
    val result: List<CwlDraw> = emptyList(),
)

@Serializable
data class CwlDraw(
    val code: String? = null,
    val name: String? = null,
    val date: String? = null,
    val week: String? = null,
    val red: String? = null,
    val blue: String? = null,
    val sales: String? = null,
    val poolmoney: String? = null,
    val content: String? = null,
    val prizegrades: List<CwlPrizeGrade> = emptyList(),
)

@Serializable
data class CwlPrizeGrade(
    val type: Int = 0,
    @SerialName("typenum") val count: String? = null,
    @SerialName("typemoney") val amount: String? = null,
)

private val TIER_BY_TYPE = mapOf(
    1 to PrizeTier.FIRST,
    2 to PrizeTier.SECOND,
    3 to PrizeTier.THIRD,
    4 to PrizeTier.FOURTH,
    5 to PrizeTier.FIFTH,
    6 to PrizeTier.SIXTH,
)

/**
 * 转成领域模型。官方数据可能缺字段或含脏数据，因此逐条防御：
 * 任何一条记录解析失败就跳过，绝不让整批同步失败。
 */
fun CwlDraw.toDraw(): Draw? {
    val code = code?.takeIf { it.isNotBlank() } ?: return null
    val reds = red?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: return null
    if (reds.size != 6 || reds.distinct().size != 6 || reds.any { it !in 1..33 }) return null
    val blueValue = blue?.trim()?.toIntOrNull() ?: return null
    if (blueValue !in 1..16) return null
    val dateText = date?.take(10) ?: return null
    val date = runCatching { kotlinx.datetime.LocalDate.parse(dateText) }.getOrNull() ?: return null

    return Draw(
        code = code,
        date = date,
        red = reds.sorted(),
        blue = blueValue,
        sales = sales?.trim()?.toLongOrNull() ?: 0L,
        poolAmount = poolmoney?.trim()?.toLongOrNull() ?: 0L,
        prizeGrades = prizegrades.mapNotNull { g ->
            val tier = TIER_BY_TYPE[g.type] ?: return@mapNotNull null
            DrawPrizeGrade(
                tier = tier,
                winnerCount = g.count?.trim()?.toIntOrNull() ?: 0,
                amountPerTicket = g.amount?.trim()?.toLongOrNull() ?: 0L,
            )
        },
    )
}
