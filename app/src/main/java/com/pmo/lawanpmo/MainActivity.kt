package com.pmo.lawanpmo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pmo.lawanpmo.export.ExportHelper
import com.pmo.lawanpmo.ml.OnnxInferenceEngine
import com.pmo.lawanpmo.ui.MainViewModel
import com.pmo.lawanpmo.ui.UiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyberAggressionTheme {
                MainScreen()
            }
        }
    }
}

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun CyberAggressionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary        = Color(0xFF5C6BC0),
            onPrimary      = Color.White,
            secondary      = Color(0xFF7986CB),
            surface        = Color.White,
            background     = Color(0xFFF5F5F5),
            onBackground   = Color(0xFF212121),
        ),
        content = content
    )
}

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context       = LocalContext.current
    val uiState       by vm.uiState.collectAsState()
    val selectedModel by vm.selectedModel.collectAsState()
    var inputText     by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.init(context) }

    // Tampilkan toast untuk pesan ekspor
    LaunchedEffect(uiState) {
        if (uiState is UiState.MultiResult) {
            val msg = (uiState as UiState.MultiResult).exportMsg
            if (msg.isNotEmpty()) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
        if (uiState is UiState.Error) {
            Toast.makeText(context, (uiState as UiState.Error).message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CyberAggression Experiment Tool",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("IndoBERTweet INT8 — ONNX Runtime Mobile",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Pemilihan Model ───────────────────────────────────────────────
            ModelSelector(
                selected = selectedModel,
                onSelect = { vm.selectModel(it) },
                enabled  = uiState !is UiState.Loading,
            )

            // ── Input Kalimat ─────────────────────────────────────────────────
            InputCard(
                text     = inputText,
                onChange = { inputText = it },
                enabled  = uiState !is UiState.Loading,
            )

            // ── Tombol Aksi ───────────────────────────────────────────────────
            ActionButtons(
                enabled      = uiState !is UiState.Loading,
                onPredictOne = { vm.predictOnce(context, inputText) },
                onPredictMulti = { vm.predictMulti(context, inputText) },
                nWarmup      = vm.N_WARMUP,
                nRuns        = vm.N_RUNS,
            )

            // ── Status Loading ────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState is UiState.Loading) {
                LoadingCard(message = (uiState as? UiState.Loading)?.message ?: "")
            }

            // ── Hasil Prediksi Sekali ─────────────────────────────────────────
            AnimatedVisibility(visible = uiState is UiState.SingleResult) {
                (uiState as? UiState.SingleResult)?.let { SingleResultCard(it) }
            }

            // ── Hasil Uji Berulang ────────────────────────────────────────────
            AnimatedVisibility(visible = uiState is UiState.MultiResult) {
                (uiState as? UiState.MultiResult)?.let {
                    MultiResultCard(
                        state       = it,
                        onExportCsv   = { vm.exportCsv(context) },
                        onExportExcel = { vm.exportExcel(context) },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Komponen UI ───────────────────────────────────────────────────────────────

@Composable
fun ModelSelector(
    selected : OnnxInferenceEngine.ModelType,
    onSelect : (OnnxInferenceEngine.ModelType) -> Unit,
    enabled  : Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape  = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Pilih Model", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7280))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnnxInferenceEngine.ModelType.entries.forEach { type ->
                    val isSelected = selected == type
                    Button(
                        onClick  = { onSelect(type) },
                        enabled  = enabled,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                             else Color.White,
                            contentColor   = if (isSelected) Color.White
                                             else MaterialTheme.colorScheme.primary,
                        ),
                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                 else null,
                        shape  = RoundedCornerShape(8.dp),
                    ) {
                        Text(type.displayName, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InputCard(text: String, onChange: (String) -> Unit, enabled: Boolean) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Masukkan Kalimat", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7280))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value          = text,
                onValueChange  = onChange,
                enabled        = enabled,
                modifier       = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder    = { Text("Tulis kalimat bahasa Indonesia di sini...") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Done,
                ),
                shape = RoundedCornerShape(8.dp),
                maxLines = 5,
            )
        }
    }
}

@Composable
fun ActionButtons(
    enabled        : Boolean,
    onPredictOne   : () -> Unit,
    onPredictMulti : () -> Unit,
    nWarmup        : Int,
    nRuns          : Int,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick  = onPredictOne,
            enabled  = enabled,
            modifier = Modifier.weight(1f).height(50.dp),
            shape    = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Prediksi Sekali", fontSize = 13.sp)
        }
        OutlinedButton(
            onClick  = onPredictMulti,
            enabled  = enabled,
            modifier = Modifier.weight(1f).height(50.dp),
            shape    = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Uji ${nWarmup}+${nRuns}×", fontSize = 13.sp)
        }
    }
    Text(
        "Uji berulang: $nWarmup warm-up (tidak dihitung) + $nRuns run terukur",
        fontSize = 11.sp, color = Color(0xFF9CA3AF),
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
    )
}

@Composable
fun LoadingCard(message: String) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(message, fontSize = 13.sp, color = Color(0xFF6B7280))
        }
    }
}

@Composable
fun SingleResultCard(state: UiState.SingleResult) {
    val isAggression = state.label.contains("Non", ignoreCase = true).not()
    val bgColor      = if (isAggression) Color(0xFFFFF3F3) else Color(0xFFF0FDF4)
    val textColor    = if (isAggression) Color(0xFF991B1B) else Color(0xFF166534)
    val borderColor  = if (isAggression) Color(0xFFFCA5A5) else Color(0xFF86EFAC)

    Card(
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        shape     = RoundedCornerShape(12.dp),
        border    = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isAggression) Icons.Default.Warning else Icons.Default.CheckCircle,
                    null, tint = textColor, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Hasil Prediksi — ${state.modelName}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(state.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("Confidence", "%.1f%%".format(state.confidence * 100))
                StatChip("Latensi",    "%.1f ms".format(state.latencyMs))
            }
        }
    }
}

@Composable
fun MultiResultCard(
    state         : UiState.MultiResult,
    onExportCsv   : () -> Unit,
    onExportExcel : () -> Unit,
) {
    val s = state.summary
    Card(
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("Hasil Uji Berulang — ${state.modelName}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold)

            // Grid statistik latensi
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Avg Latensi", "%.1f ms".format(s.avgLatencyMs), Modifier.weight(1f))
                StatBox("Std Dev",     "± %.1f ms".format(s.stdLatencyMs), Modifier.weight(1f))
                StatBox("Min",         "%.1f ms".format(s.minLatencyMs), Modifier.weight(1f))
                StatBox("Max",         "%.1f ms".format(s.maxLatencyMs), Modifier.weight(1f))
            }

            // Label mayoritas
            val isAggression = s.majorityLabel.contains("Non", ignoreCase = true).not()
            val bgColor  = if (isAggression) Color(0xFFFFF3F3) else Color(0xFFF0FDF4)
            val txtColor = if (isAggression) Color(0xFF991B1B) else Color(0xFF166534)
            Surface(color = bgColor, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Label Mayoritas (${state.runs.size} run terukur)",
                        fontSize = 11.sp, color = txtColor, fontWeight = FontWeight.Bold)
                    Text(s.majorityLabel, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = txtColor)
                    Text("Konsistensi: ${s.consistencyPct}%   " +
                         "Avg confidence: %.1f%%".format(s.avgConfidence * 100),
                        fontSize = 11.sp, color = Color(0xFF6B7280))
                }
            }

            // Info runs
            Text(
                "Warm-up: 5 run (tidak dihitung)  •  Terukur: ${state.runs.size} run",
                fontSize = 11.sp, color = Color(0xFF9CA3AF),
            )

            // Tombol ekspor
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = onExportCsv,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CSV", fontSize = 13.sp)
                }
                Button(
                    onClick  = onExportExcel,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Done, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excel", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String) {
    Surface(
        color  = Color(0xFFF3F4F6),
        shape  = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, fontSize = 10.sp, color = Color(0xFF6B7280))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color    = Color(0xFFEEF2FF),
        shape    = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(label, fontSize = 9.sp, color = Color(0xFF4338CA))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3730A3))
        }
    }
}
