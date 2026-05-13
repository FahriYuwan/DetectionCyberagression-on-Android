package com.pmo.lawanpmo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmo.lawanpmo.export.ExportHelper
import com.pmo.lawanpmo.export.ExportHelper.RunResult
import com.pmo.lawanpmo.ml.OnnxInferenceEngine
import com.pmo.lawanpmo.tokenizer.WordPieceTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    // ── Konstanta eksperimen ───────────────────────────────────────────────────
    val N_WARMUP  = 5
    val N_RUNS    = 25
    val N_TOTAL   = N_WARMUP + N_RUNS

    // ── State UI ──────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedModel = MutableStateFlow(OnnxInferenceEngine.ModelType.PTQ)
    val selectedModel: StateFlow<OnnxInferenceEngine.ModelType> = _selectedModel.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    // ── Engine cache ──────────────────────────────────────────────────────────
    private var enginePtq : OnnxInferenceEngine? = null
    private var engineQat : OnnxInferenceEngine? = null
    private var engineFp32 : OnnxInferenceEngine? = null
    private var tokenizer : WordPieceTokenizer?  = null

    fun selectModel(type: OnnxInferenceEngine.ModelType) {
        _selectedModel.value = type
    }

    // ── Inisialisasi (panggil sekali di LaunchedEffect) ───────────────────────
    fun init(context: Context) {
        if (tokenizer != null) return
        viewModelScope.launch(Dispatchers.Default) {
            tokenizer = WordPieceTokenizer(context)
        }
    }

    // ── Prediksi Sekali ───────────────────────────────────────────────────────
    fun predictOnce(context: Context, text: String) {
        if (text.isBlank()) { _uiState.value = UiState.Error("Masukkan kalimat terlebih dahulu"); return }

        _uiState.value = UiState.Loading("Memproses...")
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val enc    = tokenizer!!.encode(text)
                val engine = getEngine(context, _selectedModel.value)
                engine.predict(enc.inputIds, enc.attentionMask)
            }
            _uiState.value = UiState.SingleResult(
                label      = result.label,
                confidence = result.confidence,
                latencyMs  = result.latencyMs,
                modelName  = _selectedModel.value.displayName,
            )
        }
    }

    // ── Uji Berulang (5 warm-up + 25 run) ────────────────────────────────────
    fun predictMulti(context: Context, text: String) {
        if (text.isBlank()) { _uiState.value = UiState.Error("Masukkan kalimat terlebih dahulu"); return }

        _uiState.value = UiState.Loading("Mempersiapkan model...")
        viewModelScope.launch {
            val runs = mutableListOf<RunResult>()

            withContext(Dispatchers.Default) {
                val enc    = tokenizer!!.encode(text)
                val engine = getEngine(context, _selectedModel.value)

                // ── Warm-up (tidak disimpan) ───────────────────────────────
                repeat(N_WARMUP) { i ->
                    engine.predict(enc.inputIds, enc.attentionMask)
                    withContext(Dispatchers.Main) {
                        _uiState.value = UiState.Loading("Warm-up ${i + 1}/$N_WARMUP...")
                    }
                }

                // ── Run yang diukur ────────────────────────────────────────
                repeat(N_RUNS) { i ->
                    val r = engine.predict(enc.inputIds, enc.attentionMask)
                    runs.add(RunResult(
                        run        = i + 1,
                        label      = r.label,
                        confidence = r.confidence,
                        latencyMs  = r.latencyMs,
                    ))
                    withContext(Dispatchers.Main) {
                        _uiState.value = UiState.Loading("Run ${i + 1}/$N_RUNS...")
                    }
                }
            }

            val summary = ExportHelper.summarize(runs)
            _uiState.value = UiState.MultiResult(
                runs      = runs,
                summary   = summary,
                modelName = _selectedModel.value.displayName,
                inputText = text,
            )
        }
    }

    // ── Ekspor ────────────────────────────────────────────────────────────────
    fun exportCsv(context: Context) {
        val state = _uiState.value as? UiState.MultiResult ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ExportHelper.exportCsv(
                context, state.runs, state.modelName, state.inputText, N_WARMUP
            )
            withContext(Dispatchers.Main) {
                _uiState.value = state.copy(exportMsg = if (ok) "CSV disimpan ke Downloads" else "Gagal ekspor CSV")
            }
        }
    }

    fun exportExcel(context: Context) {
        val state = _uiState.value as? UiState.MultiResult ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ExportHelper.exportExcel(
                context, state.runs, state.modelName, state.inputText, N_WARMUP
            )
            withContext(Dispatchers.Main) {
                _uiState.value = state.copy(exportMsg = if (ok) "Excel disimpan ke Downloads" else "Gagal ekspor Excel")
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private fun getEngine(context: Context, type: OnnxInferenceEngine.ModelType): OnnxInferenceEngine {
        return when (type) {
            OnnxInferenceEngine.ModelType.PTQ -> {
                if (enginePtq == null) enginePtq = OnnxInferenceEngine(context, type)
                enginePtq!!
            }
            OnnxInferenceEngine.ModelType.QAT -> {
                if (engineQat == null) engineQat = OnnxInferenceEngine(context, type)
                engineQat!!
            }

            OnnxInferenceEngine.ModelType.FP32 -> {
                if (engineFp32 == null) engineFp32 = OnnxInferenceEngine(context,type)
                engineFp32!!
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        enginePtq?.close()
        engineQat?.close()
        engineFp32?.close()
    }
}

// ── UI State sealed class ─────────────────────────────────────────────────────
sealed class UiState {
    object Idle : UiState()

    data class Loading(val message: String) : UiState()

    data class SingleResult(
        val label      : String,
        val confidence : Float,
        val latencyMs  : Float,
        val modelName  : String,
    ) : UiState()

    data class MultiResult(
        val runs      : List<RunResult>,
        val summary   : ExportHelper.Summary,
        val modelName : String,
        val inputText : String,
        val exportMsg : String = "",
    ) : UiState()

    data class Error(val message: String) : UiState()
}
