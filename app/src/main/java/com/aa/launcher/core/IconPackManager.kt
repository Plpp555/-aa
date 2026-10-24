package com.aa.launcher.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** ADW / Nova 互換のアイコンパック (appfilter.xml) に対応する。 */
class IconPackManager(private val context: Context) {

    data class IconPackInfo(val packageName: String, val label: String)

    private class LoadedPack(val packageName: String, val res: Resources, val map: Map<String, String>)

    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
        "com.dlto.atom.launcher.THEME",
    )

    @Volatile
    private var loaded: LoadedPack? = null

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, IconPackInfo>()
        for (action in actions) {
            val list = runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg !in result) result[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    @Synchronized
    private fun pack(packageName: String): LoadedPack? {
        loaded?.let { if (it.packageName == packageName) return it }
        val pack = runCatching { load(packageName) }.getOrNull()
        loaded = pack
        return pack
    }

    fun iconFor(packageName: String, component: ComponentName): Drawable? {
        val pack = pack(packageName) ?: return null
        val name = pack.map[component.flattenToString()] ?: pack.map[component.packageName] ?: return null
        @Suppress("DiscouragedApi")
        val id = pack.res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { pack.res.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null) }.getOrNull()
    }

    private fun load(packageName: String): LoadedPack {
        val res = context.packageManager.getResourcesForApplication(packageName)
        @Suppress("DiscouragedApi")
        val xmlId = res.getIdentifier("appfilter", "xml", packageName)
        val parser: XmlPullParser = if (xmlId != 0) {
            res.getXml(xmlId)
        } else {
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(res.assets.open("appfilter.xml"), "UTF-8")
            }
        }
        val map = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) {
                    parseComponent(component)?.let { cn ->
                        map[cn.flattenToString()] = drawable
                        map.putIfAbsent(cn.packageName, drawable)
                    }
                }
            }
            event = parser.next()
        }
        return LoadedPack(packageName, res, map)
    }

    private fun parseComponent(value: String): ComponentName? {
        val inner = value.substringAfter("ComponentInfo{", "").substringBefore("}")
        val pkg = inner.substringBefore('/', "")
        var cls = inner.substringAfter('/', "")
        if (pkg.isEmpty() || cls.isEmpty()) return null
        if (cls.startsWith(".")) cls = pkg + cls
        return ComponentName(pkg, cls)
    }

    fun clear() {
        loaded = null
    }
}
