package com.ssqpick.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 本地备份：只备份自己录的东西（号码与购买记录），
 * 开奖数据不进备份——它可以从官方重新同步，且体积大。
 *
 * 购买记录用 [BackupPurchase.ticketIndex] 指向本文件里的票，
 * 避免依赖设备上的自增 id。
 */
@Serializable
data class BackupBundle(
    val schema: Int = SCHEMA,
    val exportedAtEpochMs: Long,
    val tickets: List<BackupTicket> = emptyList(),
    val purchases: List<BackupPurchase> = emptyList(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

@Serializable
data class BackupTicket(
    val createdAtEpochMs: Long,
    val label: String? = null,
    val redBold: List<Int> = emptyList(),
    val redPicked: List<Int> = emptyList(),
    val blueBold: List<Int> = emptyList(),
    val bluePicked: List<Int> = emptyList(),
) {
    fun toDraft(): TicketDraft = TicketDraft(
        redBold = redBold.toSet(),
        redPicked = redPicked.toSet(),
        blueBold = blueBold.toSet(),
        bluePicked = bluePicked.toSet(),
    )
}

@Serializable
data class BackupPurchase(
    /** 在 [BackupBundle.tickets] 中的下标。 */
    val ticketIndex: Int,
    val purchasedAtEpochMs: Long,
    val costYuan: Long,
    /** 后台是否已结算；保留它，恢复后不会把旧中奖再通知一遍。 */
    val settled: Boolean = false,
)

object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(bundle: BackupBundle): String = json.encodeToString(BackupBundle.serializer(), bundle)

    /** 解析并做基本边界检查；坏文件直接抛 [IllegalArgumentException]，由界面转成提示。 */
    fun decode(text: String): BackupBundle {
        val bundle = try {
            json.decodeFromString(BackupBundle.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的备份文件", e)
        }
        require(bundle.schema == BackupBundle.SCHEMA) {
            "备份版本 ${bundle.schema} 不受支持（当前 ${BackupBundle.SCHEMA}）"
        }
        require(bundle.tickets.size <= MAX_TICKETS) { "备份里的号码太多（${bundle.tickets.size}）" }
        require(bundle.purchases.size <= MAX_PURCHASES) { "备份里的购买记录太多（${bundle.purchases.size}）" }
        bundle.tickets.forEachIndexed { i, t ->
            require(t.createdAtEpochMs > 0) { "第 ${i + 1} 张号码缺少创建时间" }
            val draft = t.toDraft()
            require((t.redBold + t.redPicked).all { it in 1..33 }) { "第 ${i + 1} 张号码的红球越界" }
            require((t.blueBold + t.bluePicked).all { it in 1..16 }) { "第 ${i + 1} 张号码的蓝球越界" }
            require(draft.isValid) { "第 ${i + 1} 张号码不是合法投注" }
        }
        bundle.purchases.forEach { p ->
            require(p.ticketIndex in bundle.tickets.indices) { "购买记录指向不存在的号码" }
            require(p.purchasedAtEpochMs > 0) { "购买记录缺少时间" }
            require(p.costYuan in 1..MAX_COST_YUAN) { "购买金额不合法" }
        }
        return bundle
    }

    private const val MAX_TICKETS = 10_000
    private const val MAX_PURCHASES = 100_000
    private const val MAX_COST_YUAN = 1_000_000L
}
