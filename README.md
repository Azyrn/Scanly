<div align="center">

# Scanly

**A modern Android scanner for barcodes, QR codes, and documents.**

Scan codes, look up products, and extract text from images and PDFs — on-device by default, with optional AI for the hard cases.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0%2B-3DDC84.svg?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF.svg?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](#)

</div>

---

## Features

- **Barcode & QR scanning** — real-time CameraX scanning with smart actions per result: open URL, join Wi-Fi, save contact, add calendar event, send SMS/email, dial, or copy.
- **Product lookup** — scan an EAN/UPC or ISBN to pull product data from Open Food Facts, Open Beauty Facts, Open Pet Food Facts, Google Books, Open Library, and OpenFDA.
- **Offline OCR** — PP-OCRv6 running on-device via ONNX Runtime, with layout and table detection. No network, no accounts, nothing leaves the phone.
- **AI extraction** — optional cloud OCR for handwriting and complex layouts, returning structured Markdown. Bring your own key.
- **Markdown results** — view extracted text as clean prose or as rendered Markdown with real table grids, headings, and lists. Print or save to PDF from the system dialog.
- **Document scanner** — capture multi-page documents, import PDFs, and translate results into other languages.
- **Exports** — PDF, Word (.docx), Markdown, JSON, and CSV, saved to `Downloads/Scanly`.

## AI providers

AI extraction is optional and provider-agnostic:

`Gemini` · `OpenAI` · `Claude` · `OpenRouter` · `Groq` · `Cerebras` · `Cloudflare Workers AI` · `NVIDIA NIM` · `Hugging Face` · `Mistral OCR` · **Custom** (any OpenAI-compatible endpoint)

Keys are encrypted at rest, sent in request headers — never in URLs or logs.

## Tech stack

| Layer | Choices |
| --- | --- |
| Language & UI | Kotlin, Jetpack Compose, Material 3 (Expressive) |
| Architecture | MVVM, Hilt |
| Vision | CameraX, ONNX Runtime (PP-OCRv6), ML Kit, ZXing |
| Networking | Retrofit, OkHttp, Coroutines & Flow |
| Other | Coil, DataStore |

## Build

```bash
git clone https://github.com/Azyrn/Scanly.git
cd Scanly
./gradlew assembleDebug
```

Requires the Android SDK. Minimum Android 7.0 (API 24), targets API 36.

Offline scanning works out of the box. For cloud AI, add a key to `local.properties` — or skip this and add your own key in the app's settings:

```properties
GEMINI_API_KEY=your_key_here
```

Recognized keys: `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `GROQ_API_KEY`, `CEREBRAS_API_KEY`, `NVIDIA_API_KEY`, `HUGGINGFACE_API_KEY`, `MISTRAL_API_KEY`, `CLOUDFLARE_API_KEY`.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
