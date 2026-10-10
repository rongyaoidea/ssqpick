package com.ssqpick.feature.ticket

import android.content.Context
import com.ssqpick.model.TicketDraft
import com.ssqpick.store.mapper.decodeNumbers
import com.ssqpick.store.mapper.encode

/** 草稿存储：进程被杀/退出应用后，选了一半的号还在。 */
interface DraftStorage {
    fun load(): TicketDraft
    fun save(draft: TicketDraft)
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

    private companion object {
        const val PREFS = "ssq_draft"
        const val KEY_RED_BOLD = "red_bold"
        const val KEY_RED_PICKED = "red_picked"
        const val KEY_BLUE_BOLD = "blue_bold"
        const val KEY_BLUE_PICKED = "blue_picked"
    }
}
