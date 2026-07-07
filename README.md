<div align="center">

# Scanly

**A modern Android scanner for barcodes, QR codes, and text.**

Scan codes, look up products, and extract text from images and PDFs — on-device by default, with optional AI for the hard cases.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0%2B-3DDC84.svg?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF.svg?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](#)

</div>

---

## Features

- **Barcode & QR scanning** — real-time scanning with CameraX + ML Kit, with smart actions for each result: open URL, connect to Wi-Fi, save contact, add calendar event, send SMS/email, dial, or copy.
- **Product lookup** — scan a retail barcode (EAN/UPC) or ISBN to fetch product info from Open Food Facts, Open Beauty Facts, Open Pet Food Facts, Google Books, Open Library, and OpenFDA.
- **Text recognition (OCR)** — on-device text extraction with ML Kit. Works fully offline.
- **AI text extraction** — optional cloud extraction for complex layouts and handwriting. Bring your own key across many providers (see below).
- **Translation** — translate scanned text into other languages.
- **Document scanner & PDF** — capture multi-page documents and import PDFs to extract text.

## AI providers

AI extraction is optional and provider-agnostic. Supported out of the box:

`Gemini` · `OpenAI` · `Claude` · `OpenRouter` · `Groq` · `Cerebras` · `Cloudflare Workers AI` · `NVIDIA NIM` · `Hugging Face` · `Mistral OCR` · **Custom** (any OpenAI-compatible endpoint)

Keys are stored on-device and sent in request headers, never in URLs or logs.

## Tech stack

| Layer | Choices |
| --- | --- |
| Language & UI | Kotlin, Jetpack Compose, Material 3 (Expressive) |
| Architecture | MVVM, Hilt |
| Vision | CameraX, ML Kit (barcode, text recognition, document scanner) |
| Networking | Retrofit, OkHttp, Kotlin Coroutines & Flow |
| Other | Coil, DataStore |

## Build

```bash
git clone https://github.com/Azyrn/Scanly.git
cd Scanly

# Optional: add an AI key for cloud extraction
echo "GEMINI_API_KEY=your_key_here" >> local.properties

./gradlew assembleDebug
```

Requires the Android SDK. Minimum Android 7.0 (API 24), targets API 36.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
