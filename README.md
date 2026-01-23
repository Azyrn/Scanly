<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="120" alt="Scanly Logo"/>
</p>

<h1 align="center">Scanly</h1>

<p align="center">
  <b>The modern AI scanner for Android</b><br/>
  Barcode lookup • OCR • AI text extraction • Translation
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-24%2B-green?logo=android" alt="Android 24+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.2-purple?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/github/v/release/Azyrn/Scanly?include_prereleases" alt="Release"/>
</p>

---

## Features

### 📦 Product Lookup
Scan any barcode (EAN-13, UPC-A, ISBN) to get instant product information:

| Category | Data Source | Information |
|----------|-------------|-------------|
| **Food** | Open Food Facts | Nutri-Score, NOVA, ingredients, allergens |
| **Books** | Google Books, Open Library | Authors, publisher, ISBN, categories |
| **Medicine** | OpenFDA | Active ingredients, warnings, recalls |
| **Cosmetics** | Open Beauty Facts | Ingredients, allergens, labels |
| **Pet Food** | Open Pet Food Facts | Nutrition, ingredients |

### 🧠 AI Text Extraction
Go beyond OCR. **Google Gemini AI** understands context, preserves handwriting, and captures layouts from complex documents.

### ⚡ Offline Recognition
No internet? **ML Kit** provides instant, on-device text recognition that never leaves your phone.

### 📱 Smart Actions
Barcodes automatically trigger helpful actions:
- **WiFi QR** → Connect instantly
- **vCard** → Save contact
- **URL** → Open in browser
- **Calendar** → Add event

### 🌐 Translation
Translate scanned text into 15+ languages.

### 📄 PDF Extraction
Import multi-page PDFs and extract editable text.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose, Material 3 |
| **Architecture** | MVVM, Hilt DI |
| **Networking** | Retrofit, OkHttp |
| **ML** | ML Kit (OCR, Barcode), Gemini AI |
| **Async** | Kotlin Coroutines, Flow |
| **Image** | Coil, CameraX |
| **Storage** | DataStore, Room |

---

## Download

Get the latest APK from [**GitHub Releases**](https://github.com/Azyrn/Scanly/releases).

---

## Build

```bash
# Clone
git clone https://github.com/Azyrn/Scanly.git
cd Scanly

# Add API key (optional, for AI features)
echo "GEMINI_API_KEY=your_key_here" >> local.properties

# Build
./gradlew assembleDebug
```

---

## License

MIT License - see [LICENSE](LICENSE) for details.
