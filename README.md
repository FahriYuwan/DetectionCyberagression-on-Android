# CyberAggression Experiment Tool
Aplikasi Android untuk pengujian latensi inferensi model IndoBERTweet INT8.

## Persyaratan
- Android Studio Ladybug (2024.2.1) atau lebih baru
- JDK 11+
- Android device atau emulator API 26+ (Android 8.0+)
- RAM device minimal 4 GB (model 177 MB dimuat ke RAM)

## Cara Setup (langkah demi langkah)

### 1. Install Android Studio
Download dari: https://developer.android.com/studio
Ikuti installer — pilih semua komponen default.

### 2. Buka Project
- Buka Android Studio
- Klik "Open" → pilih folder CyberAggressionTool ini
- Tunggu Gradle sync selesai (butuh internet, ~5 menit pertama)

### 3. Tambahkan File Model dan Vocab
Letakkan file berikut di folder:
  app/src/main/assets/

File yang diperlukan:
  ✅ ptq_int8.onnx       ← dari Google Drive hasil Colab (S2_PTQ_INT8_deploy_int8.onnx)
  ✅ qat_int8.onnx       ← dari Google Drive hasil Colab (S5_QAT_INT8_deploy_int8.onnx)
  ✅ vocab.txt           ← download dari HuggingFace:
                           https://huggingface.co/indolem/indobertweet-base-uncased/resolve/main/vocab.txt

Cara download vocab.txt:
  1. Buka link di atas di browser
  2. File akan otomatis terdownload
  3. Rename jika perlu menjadi "vocab.txt"
  4. Copy ke folder assets/

### 4. Rename File Model
Di Google Drive kamu ada file:
  S2_PTQ_INT8_deploy_int8.onnx → rename menjadi → ptq_int8.onnx
  S5_QAT_INT8_deploy_int8.onnx → rename menjadi → qat_int8.onnx

### 5. Jalankan Aplikasi
Pilihan A — Di HP fisik (direkomendasikan untuk penelitian):
  1. Aktifkan "Developer Options" di HP Android kamu
     Caranya: Settings → About Phone → tap "Build Number" 7 kali
  2. Aktifkan "USB Debugging" di Developer Options
  3. Sambungkan HP ke laptop via USB
  4. Klik tombol Run (▶) di Android Studio
  5. Pilih device kamu → OK

Pilihan B — Di emulator:
  1. Android Studio → Device Manager → Create Device
  2. Pilih Pixel 6 → API 34 → Download → Finish
  3. Klik Run (▶)
  ⚠️ Emulator tidak mencerminkan latensi nyata HP — gunakan HP fisik untuk penelitian!

## Struktur Folder Assets (setelah setup)
app/src/main/assets/
├── ptq_int8.onnx    (177 MB)
├── qat_int8.onnx    (177 MB)
└── vocab.txt        (~1 MB)

## Cara Pakai Aplikasi

### Prediksi Sekali
1. Pilih model (PTQ INT8 atau QAT INT8)
2. Ketik kalimat
3. Tap "Prediksi Sekali"
4. Lihat hasil: label, confidence, dan latensi

### Uji Berulang (untuk penelitian)
1. Pilih model
2. Ketik kalimat
3. Tap "Uji 5+25×"
   - 5 run pertama: warm-up (tidak dihitung)
   - 25 run berikutnya: terukur
4. Lihat statistik: avg latensi, std dev, min, max
5. Tap "CSV" atau "Excel" untuk ekspor ke folder Downloads

## Output File
File tersimpan di: Internal Storage / Downloads /
Format nama: cyberexp_[ModelName]_[timestamp].csv / .xlsx

Kolom CSV:
  run, predicted_label, confidence, latency_ms

Baris terakhir:
  SUMMARY, label_mayoritas, avg_confidence, avg_latency ± std
