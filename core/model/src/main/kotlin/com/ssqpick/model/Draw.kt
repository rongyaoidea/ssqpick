package com.ssqpick.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** 自包含的日期序列化器，避免依赖 kotlinx-datetime 的序列化插件细节。 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateIso8601", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

/**
 * 一注。
 *
 * 蓝球是**列表**而不是单个号码：蓝球胆拖的一注同时含"胆码 + 拖码"两个蓝号，
 * 任一个对上开奖蓝球都算中。单式/复式只有 [blues] 一个元素。
 */
@Serializable
data class Bet(
    val red: List<Int>,
    val blues: List<Int>,
)

/**
 * 一期开奖结果。
 *
 * [sales] 为当期销售额（元）；[prizeGrades] 含各奖级的中奖注数与单注奖金。
 * 一、二等奖为浮动奖金，[prizeGrades] 中的金额才是真实派奖额。
 */
@Serializable
data class Draw(
    val code: String,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val red: List<Int>,
    val blue: Int,
    val sales: Long = 0,
    val poolAmount: Long = 0,
    val prizeGrades: List<DrawPrizeGrade> = emptyList(),
) {
    val salesBets: Long get() = sales / 2L

    fun gradeOf(tier: PrizeTier): DrawPrizeGrade? = prizeGrades.firstOrNull { it.tier == tier }
}

@Serializable
data class DrawPrizeGrade(
    val tier: PrizeTier,
    val winnerCount: Int,
    val amountPerTicket: Long,
)

/** 奖级。一、二等奖浮动，其余为固定奖金。 */
enum class PrizeTier(val label: String, val fixedAmountYuan: Long) {
    FIRST("一等奖", 0),
    SECOND("二等奖", 0),
    THIRD("三等奖", 3_000),
    FOURTH("四等奖", 200),
    FIFTH("五等奖", 10),
    SIXTH("六等奖", 5),
    ;

    val isFloating: Boolean get() = this == FIRST || this == SECOND

    /** 数值越大奖级越高。 */
    val rank: Int get() = entries.size - ordinal
}
