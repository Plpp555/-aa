package com.aa.launcher.core

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp

/** 起動回数と最近の検索を記録し、「Siri からの提案」に使う。 */
class UsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun recordLaunch(appId: String) {
        val count = prefs.getInt("c:$appId", 0)
        prefs.edit {
            putInt("c:$appId", count + 1)
            putLong("t:$appId", System.currentTimeMillis())
        }
        _version.value++
    }

    private fun score(appId: String, now: Long): Double {
        val count = prefs.getInt("c:$appId", 0)
        if (count == 0) return 0.0
        val ageHours = (now - prefs.getLong("t:$appId", 0L)) / 3_600_000.0
        // 頻度 × 新しさ (半減期 およそ 3 日)
        return count * exp(-ageHours / 100.0) + 1.0 / (1.0 + ageHours)
    }

    fun suggestions(apps: List<AppEntry>, count: Int): List<AppEntry> {
        val now = System.currentTimeMillis()
        val scored = apps.map { it to score(it.id, now) }
        val used = scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        if (used.size >= count) return used.take(count)
        val rest = apps.filter { it !in used }.sortedByDescending { it.firstInstallTime }
        return (used + rest).take(count)
    }

    fun recentSearches(): List<String> =
        prefs.getString("recent_searches", null)?.split('\u0001')?.filter { it.isNotBlank() }.orEmpty()

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = (listOf(q) + recentSearches().filter { it != q }).take(8)
        prefs.edit { putString("recent_searches", list.joinToString("\u0001")) }
        _version.value++
    }

    fun clearRecentSearches() {
        prefs.edit { remove("recent_searches") }
        _version.value++
    }

    fun clearAll() {
        prefs.edit { clear() }
        _version.value++
    }
}
