package com.pmo.lawanpmo.preprocessing

import android.content.Context
import org.json.JSONObject

/**
 * Preprocessing teks untuk inferensi, mereplikasi PERSIS urutan proses pada
 * skrip training `preprocessing_v4.py` agar tidak terjadi train-serving skew
 * (perbedaan preprocessing antara data training dan input real-time di app):
 *
 *   1. Konversi emoji (demojize)      -> emoji.demojize(text)
 *   2. Lowercasing                    -> text.lower()
 *   3. <username> -> @USER
 *   4. Mention (@xxx) -> @USER
 *   5. Hapus hashtag (#xxx)
 *   6. Normalisasi huruf berulang (3+ huruf sama -> 2 huruf)
 *   7. Hapus spasi berlebih
 *   8. Hapus tanda kutip di awal/akhir kalimat
 *
 * Peta emoji -> deskripsi (":smiling_face:") diambil dari asset "emoji_map.json",
 * yang di-generate dari pustaka Python `emoji` (versi yang sama dipakai di
 * preprocessing_v4.py) supaya hasil demojize identik dengan proses training.
 */
class TextPreprocessor private constructor(
    // Daftar (emoji, deskripsi) terurut menurun berdasarkan panjang string,
    // supaya pencocokan emoji multi-codepoint (mis. flag, ZWJ sequence, skin tone)
    // tetap greedy/longest-match seperti perilaku emoji.demojize() di Python.
    private val emojiEntries: List<Pair<String, String>>
) {

    private val reUsernameTag   = Regex("<username>", RegexOption.IGNORE_CASE)
    private val reMention       = Regex("@\\w+")
    private val reHashtag       = Regex("#\\w+")
    private val reRepeatedChar  = Regex("(.)\\1{2,}")
    private val reExtraSpace    = Regex("\\s+")
    private val reEdgeQuotes    = Regex("^[\"']+|[\"']+$")

    /**
     * Membersihkan satu kalimat input sebelum dikirim ke tokenizer/model.
     * Setara dengan fungsi clean_text() pada preprocessing_v4.py (tanpa
     * tahapan penghapusan duplikat karena itu hanya relevan untuk dataset).
     */
    fun clean(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        var t: String = text

        // 1. Konversi emoji (demojize)
        t = demojize(t)

        // 2. Lowercasing
        t = t.lowercase()

        // 3. <username> -> @USER
        t = reUsernameTag.replace(t, "@USER")

        // 4. Mention -> @USER
        t = reMention.replace(t, "@USER")

        // 5. Hapus hashtag
        t = reHashtag.replace(t, "")

        // 6. Normalisasi huruf berulang
        t = reRepeatedChar.replace(t) { m -> m.groupValues[1].repeat(2) }

        // 7. Hapus spasi berlebih
        t = reExtraSpace.replace(t, " ").trim()

        // 8. Hapus kutip awal/akhir
        t = reEdgeQuotes.replace(t, "")

        return t
    }

    /** Setara emoji.demojize(text) default (bahasa Inggris, format :description:). */
    private fun demojize(text: String): String {
        if (emojiEntries.isEmpty() || text.isEmpty()) return text

        val sb = StringBuilder(text.length)
        var i = 0
        val len = text.length

        while (i < len) {
            val cp = text.codePointAt(i)

            // Fast path: karakter ASCII/umum tidak mungkin bagian dari emoji,
            // langsung ditulis ulang tanpa dicek ke daftar emoji.
            if (cp < 0x2000) {
                sb.appendCodePoint(cp)
                i += Character.charCount(cp)
                continue
            }

            var matched = false
            for ((emojiStr, desc) in emojiEntries) {
                val eLen = emojiStr.length
                if (eLen > 0 && i + eLen <= len && text.regionMatches(i, emojiStr, 0, eLen)) {
                    sb.append(desc)
                    i += eLen
                    matched = true
                    break
                }
            }

            if (!matched) {
                sb.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }

        return sb.toString()
    }

    companion object {
        @Volatile
        private var instance: TextPreprocessor? = null

        /** Singleton — peta emoji hanya di-parse sekali dari assets. */
        fun getInstance(context: Context): TextPreprocessor {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): TextPreprocessor {
            val jsonText = context.assets.open("emoji_map.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val obj = JSONObject(jsonText)
            val entries = ArrayList<Pair<String, String>>(obj.length())

            val keys = obj.keys()
            while (keys.hasNext()) {
                val emojiStr = keys.next()
                entries.add(emojiStr to obj.getString(emojiStr))
            }

            // Urutkan dari yang terpanjang agar longest-match tercapai.
            entries.sortByDescending { it.first.length }

            return TextPreprocessor(entries)
        }
    }
}
