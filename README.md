# Scanly

A simple Android scanner app: scan barcodes and QR codes, look up products, and extract text from images and PDFs.

## Features

- **Barcode & QR scanning** — real-time scanning with CameraX + ML Kit, with smart actions for the content (open URL, connect to WiFi, save contact, add calendar event, send SMS/email, dial, copy).
- **Product lookup** — scan a retail barcode (EAN/UPC) or ISBN to fetch product info from Open Food Facts, Open Beauty Facts, Open Pet Food Facts, Google Books, Open Library, and OpenFDA.
- **Text recognition (OCR)** — on-device text extraction with ML Kit; works offline.
- **AI text extraction** — optional cloud extraction for complex layouts and handwriting. Ships with Gemini; you can supply your own Claude, OpenAI, or OpenRouter key.
- **Translation** — translate scanned text into other languages.
- **Document scanner & PDF** — capture multi-page documents and import PDFs to extract text.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Hilt
- CameraX, ML Kit (barcode, text recognition, document scanner)
- Retrofit / OkHttp, Kotlin Coroutines & Flow
- Coil, DataStore

## Build

```bash
git clone https://github.com/Azyrn/Scanly.git
cd Scanly

# Optional: add an AI key for cloud extraction
echo "GEMINI_API_KEY=your_key_here" >> local.properties

./gradlew assembleDebug
```

Requires Android SDK; minimum Android 7.0 (API 24).

## License

MIT — see [LICENSE](LICENSE).
