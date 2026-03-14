package com.evac.app.util

import android.content.Context

object Phrases {

    data class Phrase(val key: String, val text: String)

    fun getAll(context: Context): List<Phrase> {
        return listOf(
            Phrase("NEED_WATER",          context.getString(
                com.evac.app.R.string.phrase_need_water)),
            Phrase("CHILD_INJURED",       context.getString(
                com.evac.app.R.string.phrase_child_injured)),
            Phrase("BUILDING_COLLAPSED",  context.getString(
                com.evac.app.R.string.phrase_building_collapsed)),
            Phrase("NEED_MEDICINE",       context.getString(
                com.evac.app.R.string.phrase_need_medicine)),
            Phrase("CANT_MOVE",           context.getString(
                com.evac.app.R.string.phrase_cant_move))
        )
    }
}