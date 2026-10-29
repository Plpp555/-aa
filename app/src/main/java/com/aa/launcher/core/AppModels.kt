package com.aa.launcher.core

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserHandle
import androidx.annotation.StringRes
import com.aa.launcher.R
import kotlinx.serialization.json.Json

val LauncherJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
    classDiscriminator = "type"
}

/** アプリ (アクティビティ + ユーザー) を一意に識別するキー。文字列化して保存に使う。 */
data class ComponentKey(val packageName: String, val className: String, val userSerial: Long) {
    val componentName: ComponentName get() = ComponentName(packageName, className)

    /** 通知バッジ集計用 (パッケージ単位)。 */
    val packageKey: String get() = packageKey(packageName, userSerial)

    override fun toString(): String = "$packageName/$className#$userSerial"

    companion object {
        fun packageKey(packageName: String, userSerial: Long) = "$packageName#$userSerial"

        fun parse(value: String): ComponentKey? {
            val slash = value.indexOf('/')
            val hash = value.lastIndexOf('#')
            if (slash <= 0 || hash <= slash + 1) return null
            val serial = value.substring(hash + 1).toLongOrNull() ?: return null
            return ComponentKey(value.substring(0, slash), value.substring(slash + 1, hash), serial)
        }
    }
}

enum class AppCategory(@StringRes val label: Int) {
    SOCIAL(R.string.category_social),
    PRODUCTIVITY(R.string.category_productivity),
    ENTERTAINMENT(R.string.category_entertainment),
    CREATIVITY(R.string.category_creativity),
    INFORMATION(R.string.category_information),
    TRAVEL(R.string.category_travel),
    SHOPPING(R.string.category_shopping),
    HEALTH(R.string.category_health),
    GAMES(R.string.category_games),
    UTILITIES(R.string.category_utilities),
    OTHER(R.string.category_other),
}

data class AppEntry(
    val key: ComponentKey,
    val label: String,
    val user: UserHandle,
    val category: AppCategory,
    val firstInstallTime: Long,
    val isSystem: Boolean,
    val isWorkProfile: Boolean,
) {
    val id: String = key.toString()
    val searchLabel: String = SearchText.normalize(label)
}

object AppCategories {
    private val keywordMap: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.GAMES to listOf("game", "games", "play.games", "puzzle", "supercell", "nintendo", "mihoyo", "hoyoverse", "squareenix", "bandai", "konami", "sega", "cygames"),
        AppCategory.SOCIAL to listOf("social", "chat", "messeng", "messag", "twitter", "instagram", "facebook", "whatsapp", "telegram", "discord", "line.android", "jp.naver.line", "snapchat", "tiktok", "musically", "threads", "reddit", "linkedin", "kakao", "wechat", "signal", "mms", "sms", "dialer", "contacts"),
        AppCategory.ENTERTAINMENT to listOf("music", "video", "youtube", "netflix", "spotify", "player", "tv", "podcast", "radio", "primevideo", "hulu", "abema", "disney", "twitch", "niconico", "amazon.mp3", "audible"),
        AppCategory.CREATIVITY to listOf("camera", "photo", "gallery", "editor", "draw", "paint", "canva", "lightroom", "snapseed", "picsart", "capcut", "studio", "sketch"),
        AppCategory.PRODUCTIVITY to listOf("office", "docs", "sheets", "slides", "drive", "mail", "gmail", "outlook", "calendar", "notes", "keep", "todo", "task", "notion", "evernote", "slack", "teams", "zoom", "bank", "pay", "wallet", "finance", "money", "invest", "dropbox", "onedrive", "scanner", "translate"),
        AppCategory.INFORMATION to listOf("news", "reader", "book", "kindle", "wiki", "weather", "manga", "magazine", "dictionary", "smartnews", "gunosy", "flipboard"),
        AppCategory.TRAVEL to listOf("map", "maps", "travel", "transit", "uber", "taxi", "airline", "airbnb", "booking", "trip", "navitime", "jorudan", "train", "hotel", "waze"),
        AppCategory.SHOPPING to listOf("shop", "store", "amazon", "rakuten", "mercari", "ebay", "aliexpress", "shein", "temu", "yahoo.shopping", "zozo", "uniqlo", "coupon"),
        AppCategory.HEALTH to listOf("health", "fit", "fitness", "workout", "sleep", "step", "medit", "calm", "headspace", "strava", "nike", "wear"),
        AppCategory.UTILITIES to listOf("settings", "clock", "calculator", "files", "filemanager", "launcher", "tool", "compass", "recorder", "flashlight", "vpn", "security", "cleaner", "backup", "keyboard", "inputmethod", "documentsui", "deskclock", "calc"),
    )

    fun categorize(info: ApplicationInfo): AppCategory {
        // App が申告するカテゴリは Android 8.0 以降。それより前はパッケージ名から推定する
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.CREATIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.INFORMATION
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
        }
        val pkg = info.packageName.lowercase()
        for ((category, words) in keywordMap) {
            if (words.any { pkg.contains(it) }) return category
        }
        return if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) AppCategory.UTILITIES else AppCategory.OTHER
    }
}
