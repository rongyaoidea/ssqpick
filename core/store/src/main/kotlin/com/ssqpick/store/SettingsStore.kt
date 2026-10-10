package com.ssqpick.store

import android.content.Context

/**
 * 轻量设置存储（SharedPreferences）。
 *
 * 目前只有一项：开奖结果通知订阅（默认关）。
 * 刻意不引 DataStore——一项布尔值不值得多一个依赖与协程管道。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 订阅"新一期开奖"通知。 */
    var drawResultNotify: Boolean
        get() = prefs.getBoolean(KEY_DRAW_NOTIFY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DRAW_NOTIFY, value).apply()
        }

    private companion object {
        const val PREFS = "ssq_settings"
        const val KEY_DRAW_NOTIFY = "draw_result_notify"
    }
}
