package com.ssqpick.rules

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * 双色球开奖节奏：每周二、四、日 21:15。
 *
 * 这是**历法规则**，不是承诺：春节等休市会顺延，以官方公告为准。
 * UI 必须带上这句话。
 */
object DrawSchedule {

    val DRAW_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
    val DRAW_TIME = LocalTime(21, 15)

    /** [now] 之后最近的一次开奖时刻。 */
    fun nextDrawAfter(now: LocalDateTime): LocalDateTime {
        var day = now.date
        repeat(8) {
            if (day.dayOfWeek in DRAW_DAYS) {
                val candidate = LocalDateTime(day, DRAW_TIME)
                if (candidate > now) return candidate
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }
        // 8 天内必有开奖日，走不到这里；兜底保证函数全返回
        return LocalDateTime(day, DRAW_TIME)
    }
}
