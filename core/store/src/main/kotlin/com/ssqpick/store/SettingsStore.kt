package com.ssqpick.store

import android.content.Context

/** 设置存储抽象：便于 ViewModel 单测注入假实现。 */
interface SettingsStorage {
    /** 订阅"新一期开奖"通知。 */
    var drawResultNotify: Boolean

    /** 首次使用引导是否已看过。 */
    var onboardingDismissed: Boolean
}

/**
 * 轻量设置存储（SharedPreferences）。
 *
 * 目前只有一项：开奖结果通知订阅（默认关）。
 * 刻意不引 DataStore——一项布尔值不值得多一个依赖与协程管道。
 */
class SettingsStore(context: Context) : SettingsStorage {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var drawResultNotify: Boolean
        get() = prefs.getBoolean(KEY_DRAW_NOTIFY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DRAW_NOTIFY, value).apply()
        }

    override var onboardingDismissed: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()
        }

    private companion object {
        const val PREFS = "ssq_settings"
        const val KEY_DRAW_NOTIFY = "draw_result_notify"
        const val KEY_ONBOARDING_DONE = "onboarding_dismissed"
    }
}
