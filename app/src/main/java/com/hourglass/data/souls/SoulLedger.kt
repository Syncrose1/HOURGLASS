package com.hourglass.data.souls

import android.content.Context
import android.content.SharedPreferences
import com.hourglass.core.world.Deed
import com.hourglass.core.world.Souls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The running totals for the ten souls, kept for good.
 *
 * Worlds hand over what their crews did every few seconds; this adds it on. Stored as
 * one small counter per soul, role and deed, so nothing is ever lost to a world
 * ending — the mine is gone when the timer is, but what Jeb dug out of it is not.
 */
object SoulLedger {

    private var prefs: SharedPreferences? = null
    private val totals = MutableStateFlow<List<Deed>>(emptyList())

    /** Every soul's totals, per role and deed. */
    val deeds: StateFlow<List<Deed>> = totals.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        totals.value = read()
    }

    /** Adds [deeds] to the totals. */
    fun credit(deeds: List<Deed>) {
        val store = prefs ?: return
        if (deeds.isEmpty()) return
        val editor = store.edit()
        deeds.forEach { deed ->
            val key = key(deed)
            editor.putLong(key, store.getLong(key, 0L) + deed.count)
        }
        editor.apply()
        totals.value = read()
    }

    private fun read(): List<Deed> {
        val store = prefs ?: return emptyList()
        return store.all.mapNotNull { (key, value) ->
            val parts = key.split(SEPARATOR)
            val soul = parts.getOrNull(0)?.toIntOrNull()
            val count = (value as? Long)?.toInt()
            if (parts.size != 3 || soul == null || soul !in 0 until Souls.count || count == null) null
            else Deed(soul, parts[1], parts[2], count)
        }
    }

    private fun key(deed: Deed) = listOf(deed.soul, deed.role, deed.deed).joinToString(SEPARATOR)

    private const val FILE = "souls"
    private const val SEPARATOR = "|"
}
