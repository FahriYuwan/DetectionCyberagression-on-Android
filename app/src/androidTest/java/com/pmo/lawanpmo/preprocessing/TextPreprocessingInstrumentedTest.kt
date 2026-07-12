package com.pmo.lawanpmo.preprocessing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pmo.lawanpmo.tokenizer.WordPieceTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test untuk TC6.2 (Tabel IV.47):
 * "Menerima teks masukan, melakukan praproses sesuai tahapan pada data
 * training, dan melakukan tokenisasi menggunakan tokenizer IndoBERTweet."
 *
 * Dijalankan di perangkat/emulator (bukan JVM biasa) karena TextPreprocessor
 * dan WordPieceTokenizer membaca file dari assets, yang memerlukan Context
 * Android asli.
 *
 * Cara menjalankan:
 *   - Android Studio: klik kanan file ini -> Run 'TextPreprocessingInstrumentedTest'
 *   - Terminal      : ./gradlew connectedAndroidTest
 * Hasil PASS/FAIL dan log Logcat (tag "TC6.2") menjadi bukti Actual Result
 * pada tabel pengujian black-box (Tabel IV.47).
 */
@RunWith(AndroidJUnit4::class)
class TextPreprocessingInstrumentedTest {

    // Kalimat uji yang sama dengan Data Test pada Tabel IV.47 (TC6.2):
    // mengandung emoji, mention, hashtag, tanda baca berulang, dan huruf berulang
    // sehingga seluruh 8 tahap praproses tersentuh.
    private val inputText = "Anjirrrr   parah bgt lu @budi123!!! 😭😭😭 #kesel"

    // Expected Result sesuai Tabel IV.47.
    private val expectedCleanText =
        "anjirr parah bgt lu @user! :loudly_crying_face::loudly_crying_face::loudly_crying_face:"

    @Test
    fun tc6_2_praproses_menghasilkan_teks_bersih_yang_sesuai() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preprocessor = TextPreprocessor.getInstance(context)

        val cleanText = preprocessor.clean(inputText)

        android.util.Log.d("TC6.2", "Input        : $inputText")
        android.util.Log.d("TC6.2", "Clean text   : $cleanText")

        assertEquals(
            "Hasil praproses tidak sesuai Expected Result pada Tabel IV.47",
            expectedCleanText,
            cleanText
        )
    }

    @Test
    fun tc6_2_tokenisasi_dari_teks_bersih_sesuai_format_model() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preprocessor = TextPreprocessor.getInstance(context)
        val tokenizer = WordPieceTokenizer(context)

        val cleanText = preprocessor.clean(inputText)
        val enc = tokenizer.encode(cleanText)

        android.util.Log.d("TC6.2", "Input IDs       : ${enc.inputIds.joinToString()}")
        android.util.Log.d("TC6.2", "Attention mask  : ${enc.attentionMask.joinToString()}")

        // Panjang array harus selalu 128 (maxLength model IndoBERTweet).
        assertEquals(128, enc.inputIds.size)
        assertEquals(128, enc.attentionMask.size)

        // Token pertama harus [CLS] dan token pada posisi token asli terakhir
        // harus [SEP], sesuai algoritma pada Tabel IV.32.
        val clsId = 3L   // sesuaikan bila indeks [CLS] pada vocab.txt berbeda
        val sepId = 4L   // sesuaikan bila indeks [SEP] pada vocab.txt berbeda
        assertEquals(clsId, enc.inputIds[0])

        val realTokenCount = enc.attentionMask.count { it == 1L }
        assertEquals(sepId, enc.inputIds[realTokenCount - 1])

        // Posisi setelah token asli (padding) harus attention mask = 0.
        if (realTokenCount < 128) {
            assertTrue(enc.attentionMask[realTokenCount] == 0L)
        }
    }
}
