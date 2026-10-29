package com.aa.launcher.core

import java.text.Normalizer
import java.util.Locale

/** 検索・並べ替え用の文字列正規化 (全角/半角・大小文字・カタカナ/ひらがなを同一視)。 */
object SearchText {

    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            // カタカナ → ひらがな
            sb.append(if (ch in 'ァ'..'ヶ') (ch - 0x60) else ch)
        }
        return sb.toString()
    }

    /** 一致度。0 は不一致、大きいほど上位。 */
    fun score(normalizedLabel: String, normalizedQuery: String): Int {
        if (normalizedQuery.isEmpty()) return 0
        if (normalizedLabel == normalizedQuery) return 100
        if (normalizedLabel.startsWith(normalizedQuery)) return 80
        val words = normalizedLabel.split(' ', '-', '_', '.', '・')
        if (words.any { it.startsWith(normalizedQuery) }) return 60
        if (normalizedLabel.contains(normalizedQuery)) return 40
        // 頭文字 (例: "gm" → "Google Maps")
        val initials = words.filter { it.isNotEmpty() }.joinToString("") { it.first().toString() }
        if (initials.length > 1 && initials.startsWith(normalizedQuery)) return 30
        return 0
    }

    private val kanaRows = listOf(
        'あ' to "あいうえおぁぃぅぇぉゔ",
        'か' to "かきくけこがぎぐげごゕゖ",
        'さ' to "さしすせそざじずぜぞ",
        'た' to "たちつてとだぢづでどっ",
        'な' to "なにぬねの",
        'は' to "はひふへほばびぶべぼぱぴぷぺぽ",
        'ま' to "まみむめも",
        'や' to "やゆよゃゅょ",
        'ら' to "らりるれろ",
        'わ' to "わをんゎゐゑ",
    )

    /** App ライブラリの一覧表示用のセクション見出し。 */
    fun sectionOf(label: String): String {
        val first = normalize(label.trim()).firstOrNull() ?: return "#"
        if (first in 'a'..'z') return first.uppercaseChar().toString()
        for ((head, chars) in kanaRows) if (chars.indexOf(first) >= 0) return head.toString()
        return "#"
    }

    /** セクションの並び順 (A–Z → かな → #)。 */
    fun sectionOrder(section: String): Int {
        val c = section.firstOrNull() ?: return Int.MAX_VALUE
        return when {
            c in 'A'..'Z' -> c - 'A'
            c == '#' -> 1000
            else -> 100 + kanaRows.indexOfFirst { it.first == c }
        }
    }
}
