package com.pmo.lawanpmo.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekspor hasil uji berulang ke CSV dan Excel.
 *
 * Format CSV/Excel:
 *   Kolom 1 : run           — nomor pengulangan (1–25, tidak termasuk warm-up)
 *   Kolom 2 : predicted_label
 *   Kolom 3 : confidence    — probabilitas [0.0–1.0]
 *   Kolom 4 : latency_ms    — waktu inferensi (ms)
 *   Baris terakhir: SUMMARY — rata-rata & std deviasi
 *
 * File disimpan ke folder Downloads/.
 */
object ExportHelper {

    data class RunResult(
        val run        : Int,
        val label      : String,
        val confidence : Float,
        val latencyMs  : Float,
    )

    data class Summary(
        val avgLatencyMs  : Float,
        val stdLatencyMs  : Float,
        val minLatencyMs  : Float,
        val maxLatencyMs  : Float,
        val majorityLabel : String,
        val consistencyPct: Int,
        val avgConfidence : Float,
    )

    fun summarize(results: List<RunResult>): Summary {
        val latencies  = results.map { it.latencyMs }
        val avg        = latencies.average().toFloat()
        val std        = Math.sqrt(
            latencies.map { (it - avg).toDouble().let { d -> d * d } }.average()
        ).toFloat()

        val labelCounts = results.groupBy { it.label }.mapValues { it.value.size }
        val majority    = labelCounts.maxByOrNull { it.value }?.key ?: "-"
        val consistency = (labelCounts[majority] ?: 0) * 100 / results.size
        val avgConf     = results.filter { it.label == majority }
            .map { it.confidence }.average().toFloat()

        return Summary(
            avgLatencyMs   = avg,
            stdLatencyMs   = std,
            minLatencyMs   = latencies.min(),
            maxLatencyMs   = latencies.max(),
            majorityLabel  = majority,
            consistencyPct = consistency,
            avgConfidence  = avgConf,
        )
    }

    // ── Ekspor CSV ────────────────────────────────────────────────────────────

    fun exportCsv(
        context    : Context,
        results    : List<RunResult>,
        modelName  : String,
        inputText  : String,
        nWarmup    : Int,
    ): Boolean {
        val summary   = summarize(results)
        val ts        = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename  = "cyberexp_${modelName.replace(" ", "_")}_$ts.csv"
        val content   = buildCsv(results, summary, modelName, inputText, nWarmup)

        return try {
            saveText(context, filename, "text/csv", content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── Ekspor Excel ──────────────────────────────────────────────────────────

    fun exportExcel(
        context    : Context,
        results    : List<RunResult>,
        modelName  : String,
        inputText  : String,
        nWarmup    : Int,
    ): Boolean {
        val summary  = summarize(results)
        val ts       = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "cyberexp_${modelName.replace(" ", "_")}_$ts.xlsx"

        return try {
            val wb = XSSFWorkbook()

            // ── Sheet 1: Data per run ──────────────────────────────────────
            val s1 = wb.createSheet("Hasil Per Run")
            s1.createRow(0).apply {
                listOf("run", "predicted_label", "confidence", "latency_ms")
                    .forEachIndexed { i, h -> createCell(i).setCellValue(h) }
            }
            results.forEachIndexed { idx, r ->
                s1.createRow(idx + 1).apply {
                    createCell(0).setCellValue(r.run.toDouble())
                    createCell(1).setCellValue(r.label)
                    createCell(2).setCellValue(r.confidence.toDouble())
                    createCell(3).setCellValue(r.latencyMs.toDouble())
                }
            }
            // Baris summary di akhir
            s1.createRow(results.size + 2).apply {
                createCell(0).setCellValue("SUMMARY")
                createCell(1).setCellValue(summary.majorityLabel)
                createCell(2).setCellValue("%.4f".format(summary.avgConfidence))
                createCell(3).setCellValue(
                    "%.2f ± %.2f".format(summary.avgLatencyMs, summary.stdLatencyMs)
                )
            }

            // ── Sheet 2: Summary statistik ────────────────────────────────
            val s2 = wb.createSheet("Summary")
            listOf(
                "Input teks"          to inputText,
                "Model"               to modelName,
                "Warm-up runs"        to "$nWarmup (tidak dihitung)",
                "Measured runs"       to "${results.size}",
                "Total runs"          to "${results.size + nWarmup}",
                "Label mayoritas"     to summary.majorityLabel,
                "Konsistensi"         to "${summary.consistencyPct}%",
                "Avg confidence"      to "%.4f".format(summary.avgConfidence),
                "Avg latency (ms)"    to "%.2f".format(summary.avgLatencyMs),
                "Std latency (ms)"    to "%.2f".format(summary.stdLatencyMs),
                "Min latency (ms)"    to "%.2f".format(summary.minLatencyMs),
                "Max latency (ms)"    to "%.2f".format(summary.maxLatencyMs),
            ).forEachIndexed { i, (k, v) ->
                s2.createRow(i).apply {
                    createCell(0).setCellValue(k)
                    createCell(1).setCellValue(v)
                }
            }

            // Simpan file
            val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { wb.write(it) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    filename
                )
                FileOutputStream(file).use { wb.write(it) }
            }
            wb.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildCsv(
        results   : List<RunResult>,
        summary   : Summary,
        modelName : String,
        inputText : String,
        nWarmup   : Int,
    ): String = buildString {
        appendLine("# CyberAggression Experiment Tool")
        appendLine("# Model: $modelName")
        appendLine("# Input: $inputText")
        appendLine("# Warm-up runs: $nWarmup (tidak dihitung)")
        appendLine("# Measured runs: ${results.size}")
        appendLine()
        appendLine("run,predicted_label,confidence,latency_ms")
        results.forEach { r ->
            appendLine("${r.run},${r.label},%.4f,%.2f".format(r.confidence, r.latencyMs))
        }
        appendLine()
        appendLine("SUMMARY,${summary.majorityLabel},%.4f,%.2f ± %.2f".format(
            summary.avgConfidence, summary.avgLatencyMs, summary.stdLatencyMs
        ))
    }

    private fun saveText(context: Context, filename: String, mimeType: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw Exception("MediaStore insert gagal")
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray())
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                filename
            ).writeText(content)
        }
    }
}
