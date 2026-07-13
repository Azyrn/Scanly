<div align="center">

# Scanly

**A modern Android scanner for documents, barcodes, and QR codes.**

Extract text from images and PDFs on-device by default, with optional cloud AI for handwriting and
complex layouts — and export exactly what you see on screen.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0%2B-3DDC84.svg?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF.svg?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](#)

</div>

---

## Features

### AI Scan

- Optional cloud extraction for handwriting and complex layouts, returning structured Markdown.
- Eleven providers, including any OpenAI-compatible endpoint (see [AI Scan](#ai-scan)).
- Token-by-token streaming, so text appears as the model produces it.
- Opt-in cross-provider fallback when the selected provider is rate-limited or failing.
- Translation of a result into 18 languages, cached per language.
- Up to 3 images or PDF/text files per scan.

### Offline OCR

- PaddleOCR PP-OCRv6 running on-device through ONNX Runtime — the default engine.
- ML Kit text recognition as a faster alternative engine.
- Nine script packs (two bundled, seven downloadable): Universal (Latin/Chinese/Japanese) and Arabic
  ship in the APK; Korean, Cyrillic, Devanagari, Thai, Greek, Tamil, and Telugu download on demand.
- Layout detection, table recognition, dewarping, and orientation fixes (see [Offline OCR](#offline-ocr)).

### Document Processing

- Multi-page document capture via the ML Kit document scanner, with a CameraX fallback on devices
  without Google Play services.
- PDF import and page-by-page text extraction.
- Gallery import for single images.
- Scan history, stored locally as JSON with the captured images.

### Export

- PDF, Word (`.docx`), Markdown, CSV, and JSON, written to `Downloads/Scanly` and offered for sharing.
- Every export reflects the view you are currently looking at (see [Export](#export)).
- Printing through the system print dialog, which also offers Save-as-PDF.

### Barcode

- Real-time CameraX scanning with ML Kit (default) or ZXing-C++.
- Content-aware actions per result, plus product lookup for EAN/UPC and ISBN codes
  (see [Barcode](#barcode)).

### Privacy

- Offline OCR never uploads anything; AI Scan is the only path that leaves the device
  (see [Privacy](#privacy)).
- Your API keys are encrypted at rest with AES-GCM in the Android Keystore, and are sent in request
  headers — never in URLs or logs.

### UI/UX

- Material 3 Expressive UI, built entirely in Jetpack Compose.
- Dynamic color plus selectable palettes, dark theme, and a pure-black OLED mode.

---

## AI Scan

AI Scan is optional. Nothing is sent anywhere until you explicitly run a scan with an online
provider.

### Supported providers

| Provider | API kind | Notes |
| --- | --- | --- |
| Gemini | Gemini | Default provider |
| Mistral OCR | Mistral OCR | Dedicated OCR endpoint, not a chat model |
| OpenRouter | OpenAI-compatible | |
| Hugging Face | OpenAI-compatible | Router endpoint |
| NVIDIA NIM | OpenAI-compatible | |
| Groq | OpenAI-compatible | |
| Cerebras | OpenAI-compatible | |
| Cloudflare Workers AI | OpenAI-compatible | Needs an account ID as well as a token |
| OpenAI | OpenAI-compatible | |
| Claude | Anthropic | |
| Custom | OpenAI-compatible | Any endpoint: your own base URL, model, and key |

### Vision and OCR enhancement

Images are sent to a vision model with an extraction prompt that asks for structured Markdown —
headings, lists, and pipe tables — rather than a flat text dump. This is what makes AI Scan useful
for handwriting, receipts, and multi-column layouts that trip up classical OCR. PDFs and plain-text
files use a separate extraction prompt.

### Streaming

Every provider streams except Mistral OCR, whose endpoint is not a chat API. Streamed text is shown
live while the scan runs. If a provider accepts a streaming request but sends nothing, the run is
retried without streaming.

### Provider fallback

Off by default: the selected provider is the only one used. Enabling **Settings → AI Providers →
fallback** lets other configured providers take over automatically when the selected one is
exhausted. If your own API key is in use, fallback only considers other providers where you also
supplied a key — the app never silently falls back from your key to a bundled one.

Within a single provider, failed attempts are retried with exponential backoff on HTTP 429 and 5xx
responses.

### API keys

Add your own key per provider in **Settings → AI Providers**. Keys are stored in DataStore,
encrypted with an AES-GCM key held in the Android Keystore, and are never logged.

Builds may also ship bundled free-tier keys for some providers (obfuscated in the APK and pinned to
the release signature). These are shared, so they are rate-limited; results show a badge saying
whether the scan used a built-in key or yours. When the shared limit is hit, you can wait, add your
own key, or watch an optional rewarded ad for one extra scan.

### Local vs cloud

| | Runs on-device | Sends data to a provider |
| --- | --- | --- |
| Offline OCR (PaddleOCR / ML Kit) | Yes | No |
| Barcode scanning and parsing | Yes | No |
| Product lookup | No | Barcode number only, to the lookup APIs |
| AI Scan | No | The image or file you selected |
| AI translation | No | The extracted text |

---

## Offline OCR

The default engine is **PaddleOCR PP-OCRv6**, executed with ONNX Runtime. It runs fully offline: no
account, no network, nothing leaves the phone. The Universal and Arabic recognition models are
bundled in the APK; the other script packs download on demand and are verified by size and SHA-256
before installing.

Configured in **Settings → Text Recognition**:

| Capability | Model | Availability |
| --- | --- | --- |
| Text detection and recognition | PP-OCRv6 | Bundled |
| Auto-rotate page | Document orientation classifier | Bundled, on by default |
| Fix flipped lines | Line orientation classifier | Bundled, on by default |
| Flatten curved pages | UVDoc dewarping | 30 MB download, off by default |
| Detect layout | PP-DocLayout | Bundled, on by default |
| Recognize tables | SLANet_plus | 8 MB download, off by default |

- **Auto-rotate page** detects sideways and upside-down pages before recognition.
- **Fix flipped lines** corrects individual upside-down text lines within an otherwise upright page.
- **Flatten curved pages** dewarps books and folded documents. Slower — it is opt-in for that reason.
- **Detect layout** classifies regions (headings, paragraphs, tables) and establishes reading order.
  This is what produces the Markdown view; with it off, you get plain recognized lines only.
- **Recognize tables** reconstructs table structure into Markdown grids, which is also what makes CSV
  export possible from an offline scan.

Downloads continue if you leave the settings screen, and the toggle reflects the result when you
return.

---

## Markdown

A result that has Markdown behind it — an AI extraction, or an offline scan with layout detection
enabled — is shown with two views you can switch between:

- **Original** — the text as plain text. For an AI result, that is the Markdown source with its
  syntax stripped back out. For an offline scan, it is the recognized lines.
- **Markdown** — the rendered document: headings, bold/italic/strikethrough, inline code, bullet and
  numbered lists, task checkboxes, block quotes, code blocks, horizontal rules, and tables drawn as
  real grids.

### WYSIWYE — What You See Is What You Export

Exports read the currently visible view and nothing else. There is no hidden "original" text that a
file might silently fall back to: if you are looking at rendered Markdown, that is what your PDF and
Word files contain; if you are looking at plain text, your files contain plain text, with no Markdown
interpretation applied.

### CSV extraction

CSV carries table data only, taken from what is on screen:

- In the **Markdown** view, cells come from the rendered Markdown tables, with inline formatting
  removed.
- In the **Original** view, columns are detected from text that already lines up on screen
  (tab-, pipe-, or multi-space-separated rows).
- When the visible text has no table in it, CSV is shown as unavailable rather than producing an
  empty or nonsensical file.

---

## Export

All exports are written to `Downloads/Scanly` and then offered for sharing.

| Format | Original view | Markdown view |
| --- | --- | --- |
| **PDF** | The plain text exactly as displayed, no Markdown parsing | The rendered document: headings, emphasis, lists, quotes, code blocks, and bordered table grids, paginated with table headers repeated across pages |
| **Word (.docx)** | The plain text, one paragraph per line | The same structure as OOXML: sized heading runs, bold/italic/strikethrough/underline runs, monospaced code, indented lists, bordered quotes, and real Word tables |
| **Markdown (.md)** | The visible plain text, written as-is — no Markdown is inferred or reconstructed | The Markdown source, unchanged |
| **CSV** | Columns detected in the visible text; unavailable when there are none | Cells from the visible Markdown tables |
| **JSON** | Offline PaddleOCR scans only: the text plus per-line bounding boxes, confidence, and page number | Same, with the visible text |

Printing is available from the same screen and follows the same rule — the print preview renders
Markdown or plain text to match the view you are in.

---

## Barcode

Real-time scanning through CameraX, using **ML Kit** by default or **ZXing-C++** (selectable in
settings).

**Formats:** QR Code, Aztec, Data Matrix, PDF417, EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39.

**Actions**, offered according to the decoded content:

| Content | Action |
| --- | --- |
| URL | Open in browser |
| Wi-Fi credentials | Connect to network |
| Phone number | Dial |
| Email | Compose email |
| SMS | Compose message |
| Contact (vCard/MECARD) | Save contact |
| Calendar event | Add to calendar |
| EAN/UPC or ISBN | Look up product |
| Any | Copy, or show raw content |

**Product lookup** resolves a scanned code against Open Food Facts, Open Beauty Facts,
Open Pet Food Facts, OpenFDA, Google Books, and Open Library, trying them in priority order. Only the
barcode number is sent.

---

## Privacy

- **Offline OCR never uploads your documents.** PaddleOCR and ML Kit run entirely on-device. Scanning,
  layout detection, table recognition, and every export work with no network connection at all.
- **AI Scan is the only feature that transmits document content**, and only when you explicitly start
  a scan or a translation with an online provider. The image, file, or text you selected is sent to
  that provider; nothing is sent in the background.
- **Barcode decoding is on-device.** Product lookup sends the barcode number — and nothing else — to
  the lookup APIs listed above.
- **Your API keys stay on your device.** They are stored encrypted (AES-GCM, key held in the Android
  Keystore), transmitted only in request headers to the provider they belong to, and never written to
  logs or URLs.
- **Scan history is local**, kept as a JSON file with its images in the app's private storage.

---

## Technical stack

| Layer | Technologies |
| --- | --- |
| Language | Kotlin 2.3 (JDK 21) |
| UI | Jetpack Compose, Material 3 (Expressive), Material Color Utilities, Coil |
| Architecture | MVVM, Hilt, DataStore, WorkManager, Navigation Compose, Coroutines & Flow |
| On-device vision | ONNX Runtime (PaddleOCR PP-OCRv6), ML Kit (text recognition, barcode, document scanner), ZXing-C++, CameraX |
| Networking | Retrofit, OkHttp, kotlinx.serialization |
| Security | Android Keystore (AES-GCM) |
| Other | AdMob (rewarded ads, optional), Baseline Profiles, LeakCanary (debug) |

---

## Screenshots

<!-- Screenshots pending. Add images to docs/screenshots/ and link them here. -->

| Home | Results | Markdown view |
| --- | --- | --- |
| _Coming soon_ | _Coming soon_ | _Coming soon_ |

---

## Build

```bash
git clone https://github.com/Azyrn/Scanly.git
cd Scanly
./gradlew assembleDebug
```

Requires the Android SDK and JDK 21. Minimum Android 7.0 (API 24), targets API 36.

Offline scanning, barcode scanning, and every export work out of the box with no configuration.

For cloud AI you can either add your key in the app's settings at runtime, or bundle one at build
time by adding it to `local.properties` (gitignored):

```properties
GEMINI_API_KEY=your_key_here
```

Recognized properties: `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `GROQ_API_KEY`, `CEREBRAS_API_KEY`,
`NVIDIA_API_KEY`, `HUGGINGFACE_API_KEY`, `MISTRAL_API_KEY`, `CLOUDFLARE_API_KEY`,
`CLOUDFLARE_ACCOUNT_ID`.

Release builds are signed from `local.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) and use R8 full mode. Debug builds use Google's AdMob test ad units.

```bash
./gradlew testDebugUnitTest        # unit tests
./gradlew connectedDebugAndroidTest # instrumented tests (device required)
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
