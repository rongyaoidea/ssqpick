package com.ssqpick.feature.ticket

import android.content.Context
import com.ssqpick.model.TicketDraft
import com.ssqpick.rules.strategy.StrategyConfig
import com.ssqpick.rules.strategy.StrategyKind
import com.ssqpick.store.mapper.decodeNumbers
import com.ssqpick.store.mapper.encode

/** 草稿存储：进程被杀/退出应用后，选了一半的号还在。 */
interface DraftStorage {
    fun load(): TicketDraft
    fun save(draft: TicketDraft)

    /** 上次使用的策略参数；默认无，便于测试替身只实现草稿读写。 */
    fun loadStrategyConfig(): StrategyConfig? = null
    fun saveStrategyConfig(config: StrategyConfig) {}

    /** 本月预算（元）；0 = 不限。 */
    fun loadMonthlyBudgetYuan(): Long = 0L
    fun saveMonthlyBudgetYuan(value: Long) {}
}

/** SharedPreferences 实现。四项号码集合各存一个逗号分隔串，足够小。 */
class PrefsDraftStore(context: Context) : DraftStorage {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): TicketDraft = TicketDraft(
        redBold = prefs.getString(KEY_RED_BOLD, "").orEmpty().decodeNumbers().toSet(),
        redPicked = prefs.getString(KEY_RED_PICKED, "").orEmpty().decodeNumbers().toSet(),
        blueBold = prefs.getString(KEY_BLUE_BOLD, "").orEmpty().decodeNumbers().toSet(),
        bluePicked = prefs.getString(KEY_BLUE_PICKED, "").orEmpty().decodeNumbers().toSet(),
    )

    override fun save(draft: TicketDraft) {
        prefs.edit()
            .putString(KEY_RED_BOLD, draft.redBold.toList().encode())
            .putString(KEY_RED_PICKED, draft.redPicked.toList().encode())
            .putString(KEY_BLUE_BOLD, draft.blueBold.toList().encode())
            .putString(KEY_BLUE_PICKED, draft.bluePicked.toList().encode())
            .apply()
    }

    override fun loadStrategyConfig(): StrategyConfig? = runCatching {
        val kind = StrategyKind.valueOf(
            prefs.getString(KEY_STRATEGY_KIND, StrategyKind.BAYES.name) ?: StrategyKind.BAYES.name,
        )
        val lookback = prefs.getInt(KEY_STRATEGY_LOOKBACK, 100).let { if (it <= 0) null else it }
        StrategyConfig(
            kind = kind,
            redCount = prefs.getInt(KEY_STRATEGY_RED, 6),
            blueCount = prefs.getInt(KEY_STRATEGY_BLUE, 1),
            lookback = lookback,
        )
    }.getOrNull()

    override fun saveStrategyConfig(config: StrategyConfig) {
        prefs.edit()
            .putString(KEY_STRATEGY_KIND, config.kind.name)
            .putInt(KEY_STRATEGY_RED, config.redCount)
            .putInt(KEY_STRATEGY_BLUE, config.blueCount)
            .putInt(KEY_STRATEGY_LOOKBACK, config.lookback ?: 0)
            .apply()
    }

    override fun loadMonthlyBudgetYuan(): Long = prefs.getLong(KEY_MONTHLY_BUDGET, 0L)

    override fun saveMonthlyBudgetYuan(value: Long) {
        prefs.edit().putLong(KEY_MONTHLY_BUDGET, value.coerceAtLeast(0L)).apply()
    }

    private companion object {
        const val PREFS = "ssq_draft"
        const val KEY_RED_BOLD = "red_bold"
        const val KEY_RED_PICKED = "red_picked"
        const val KEY_BLUE_BOLD = "blue_bold"
        const val KEY_BLUE_PICKED = "blue_picked"
        const val KEY_STRATEGY_KIND = "strategy_kind"
        const val KEY_STRATEGY_RED = "strategy_red"
        const val KEY_STRATEGY_BLUE = "strategy_blue"
        const val KEY_STRATEGY_LOOKBACK = "strategy_lookback"
        const val KEY_MONTHLY_BUDGET = "monthly_budget_yuan"
    }
}
