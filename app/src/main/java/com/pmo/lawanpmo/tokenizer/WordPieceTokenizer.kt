package com.pmo.lawanpmo.tokenizer

import android.content.Context

/**
 * WordPiece Tokenizer untuk IndoBERTweet.
 *
 * Mengimplementasikan tokenisasi yang sama dengan HuggingFace
 * AutoTokenizer("indolem/indobertweet-base-uncased"):
 *   1. Lowercase + trim
 *   2. Split per spasi
 *   3. WordPiece greedy longest-match dari vocab.txt
 *   4. Tambah [CLS] di awal, [SEP] di akhir
 *   5. Padding/truncation ke maxLength = 128
 *
 * File vocab.txt harus ada di: app/src/main/assets/vocab.txt
 * Download dari: https://huggingface.co/indolem/indobertweet-base-uncased
 */
class WordPieceTokenizer(context: Context) {

    private val vocab      : Map<String, Int>
    private val unkId      : Int
    private val clsId      : Int
    private val sepId      : Int
    private val padId      : Int
    val maxLength          = 128

    init {
        val map = mutableMapOf<String, Int>()
        context.assets.open("vocab.txt").bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, token -> map[token.trim()] = i }
        }
        vocab = map
        unkId = map["[UNK]"] ?: 100
        clsId = map["[CLS]"] ?: 101
        sepId = map["[SEP]"] ?: 102
        padId = map["[PAD]"] ?: 0
    }

    data class Encoding(
        val inputIds      : LongArray,
        val attentionMask : LongArray,
    )

    fun encode(text: String): Encoding {
        val tokens    = wordpieceTokenize(text.lowercase().trim())
        val truncated = tokens.take(maxLength - 2)   // sisakan slot [CLS] dan [SEP]

        val ids = mutableListOf<Long>().apply {
            add(clsId.toLong())
            addAll(truncated.map { (vocab[it] ?: unkId).toLong() })
            add(sepId.toLong())
        }

        val inputIds      = LongArray(maxLength) { padId.toLong() }
        val attentionMask = LongArray(maxLength) { 0L }

        ids.forEachIndexed { i, id ->
            if (i < maxLength) {
                inputIds[i]      = id
                attentionMask[i] = 1L
            }
        }

        return Encoding(inputIds, attentionMask)
    }

    // ── Implementasi WordPiece ────────────────────────────────────────────────

    private fun wordpieceTokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            result.addAll(wordpieceWord(word))
        }
        return result
    }

    private fun wordpieceWord(word: String): List<String> {
        if (word in vocab) return listOf(word)

        val subTokens  = mutableListOf<String>()
        var start      = 0
        var isBad      = false

        while (start < word.length) {
            var end      = word.length
            var found    = ""
            var matched  = false

            while (start < end) {
                val sub = if (start == 0) word.substring(start, end)
                          else "##" + word.substring(start, end)
                if (sub in vocab) { found = sub; matched = true; break }
                end--
            }

            if (!matched) { isBad = true; break }
            subTokens.add(found)
            start = end
        }

        return if (isBad) listOf("[UNK]") else subTokens
    }
}
