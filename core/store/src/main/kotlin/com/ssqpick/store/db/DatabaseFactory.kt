package com.ssqpick.store.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ssqpick.store.DrawRepositoryImpl
import com.ssqpick.store.PurchaseRepositoryImpl
import com.ssqpick.store.SsqDatabase
import com.ssqpick.store.TicketRepositoryImpl
import com.ssqpick.store.network.CwlApi

/**
 * 建库。
 *
 * `Integer` 在 SQLDelight 里默认映射 Kotlin `Long`，所以实体字段都是 Long，
 * 领域侧的 `Int`/`Long` 转换集中在 Mappers.kt。
 *
 * 版本：schema 由迁移文件推导（1.sqm = v2，购买记录表）；
 * AndroidSqliteDriver 带 schema 时会自动跑升级迁移。
 */
fun createDatabase(context: Context): SsqDatabase {
    val driver = AndroidSqliteDriver(
        schema = SsqDatabase.Schema,
        context = context,
        name = "ssq.db",
    )
    return SsqDatabase(driver = driver)
}

/** 便于在 Application 里一次性取得各 repository。 */
class DatabaseFactory(
    context: Context,
    api: CwlApi,
) {
    private val db: SsqDatabase by lazy { createDatabase(context) }
    val draws: DrawRepositoryImpl by lazy { DrawRepositoryImpl(api, db.drawQueries) }
    val tickets: TicketRepositoryImpl by lazy { TicketRepositoryImpl(db.ticketQueries, db.purchaseQueries) }
    val purchases: PurchaseRepositoryImpl by lazy { PurchaseRepositoryImpl(db.purchaseQueries) }
}
