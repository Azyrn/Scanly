<div align="center">

# Scanly

**Point your phone at a page and get the text — offline, or through the AI provider of your choice.**

An Android scanner for documents, barcodes, and QR codes. Reads a page fully offline with PaddleOCR,
or with a vision model that re-types it as Markdown. Exports to PDF, Word, Markdown, CSV, or JSON,
and translates into 18 languages.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF.svg?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](#)

</div>

---

## Three things to know first

**It works with the radio off.** The default engine is **PaddleOCR PP-OCRv6**, running on-device
through ONNX Runtime. No key, no account, no network. Scanning, layout detection, table recognition,
and every export work in airplane mode.

**The AI mode transcribes, it doesn't summarize.** A vision model re-types the page as Markdown that
mirrors it — same words, same headings, same bold, same line breaks, same tables. That's what lets it
read handwriting, receipts, forms, multi-column pages, and tables that classical OCR chokes on, and
why its output drops into a PDF or Word file with its structure intact.

**Your key goes straight to the provider.** Eleven providers, or any OpenAI-compatible endpoint
including one you host. Your key is encrypted on-device and sent in a request header to the one
provider it belongs to. **There is no Scanly server and no proxy** — your phone calls `api.openai.com`
or `api.groq.com` or your own URL directly. Your account, your billing, revocable by you.

---

## Features

**Scan** — multi-page documents (ML Kit document scanner, CameraX fallback), PDFs, gallery images (up
to 3 per scan), and barcodes/QR codes in real time. History is kept locally with its images.

**Read** — results open in a **Markdown view**: headings, bold/italic/strikethrough, lists,
checkboxes, quotes, code blocks, and tables drawn as real grids. A receipt's totals line up; a table
is a table. Switch to **Original** for plain text. Translate in place into Arabic, Chinese, Dutch,
English, French, German, Hindi, Italian, Japanese, Korean, Marathi, Polish, Portuguese, Russian,
Spanish, Telugu, Turkish, or Urdu — cached per language, and the transcription itself is never
translated behind your back.

**Export** — **whatever you see is what you get.** Exports read the view you're currently in; there's
no hidden text a file falls back to.

| Format | Contents |
| --- | --- |
| **PDF** | The rendered document — headings, emphasis, lists, code blocks, bordered tables, paginated with table headers repeated across pages |
| **Word (.docx)** | Real OOXML: heading runs, bold/italic/underline, monospaced code, indented lists, editable Word tables |
| **Markdown (.md)** | The Markdown source, unchanged |
| **CSV** | Table data only, from the tables on screen. Unavailable when the text has no table, rather than handing you an empty file |
| **JSON** | Text plus per-line bounding boxes, confidence, and page number (offline scans) — for feeding a scan into something else |

Files land in `Downloads/Scanly` and are offered for sharing. Printing goes through the system dialog,
which also offers Save-as-PDF.

**UI** — Material 3 Expressive, entirely Jetpack Compose. Dynamic color, selectable palettes, dark
theme, pure-black OLED mode.

---

## Offline OCR

**PaddleOCR PP-OCRv6** on ONNX Runtime, and it's already the default. ML Kit text recognition is
available as a faster alternative.

Nine script packs. Universal (Latin/Chinese/Japanese) and Arabic ship in the APK; Korean, Cyrillic,
Devanagari, Thai, Greek, Tamil, and Telugu download on demand — the one time this feature touches the
network — and are verified by size and SHA-256 before installing.

**Settings → Text Recognition:**

| Capability | Model | Availability |
| --- | --- | --- |
| Text detection and recognition | PP-OCRv6 | Bundled |
| Auto-rotate page | Document orientation classifier | Bundled, on by default |
| Fix flipped lines | Line orientation classifier | Bundled, on by default |
| Detect layout | PP-DocLayout | Bundled, on by default |
| Recognize tables | SLANet_plus | 8 MB download, off by default |
| Flatten curved pages | UVDoc dewarping | 30 MB download, off by default |

**Detect layout** works out reading order and is what produces the Markdown view offline — with it
off you get plain recognized lines. **Recognize tables** rebuilds tables into Markdown grids, which is
what makes CSV export possible from an offline scan. **Flatten curved pages** dewarps books and folded
documents; it's slower, hence opt-in.

---

## AI Scan

Optional and explicit — nothing is sent anywhere until you run a scan with an online provider.

The model is instructed to reproduce what it sees: every word as printed, `#` headings by visual size,
`**bold**`, `~~strikethrough~~`, real Markdown tables, checkboxes as `[ ]` / `[x]`, line breaks and
indentation kept, unreadable text marked `[unclear]`. It never summarizes, re-wraps, or corrects.
Multi-column pages go column by column, left to right. Text streams in token by token while it works.

**Its limits are the model's limits.** A vision model can misread a smudged digit, and a shared
free-tier key can be rate-limited. If the page is clean printed text and you want determinism and zero
network, use offline OCR — the two are different tools, not better and worse.

### Providers

| Provider | API kind | Notes |
| --- | --- | --- |
| Gemini | Gemini | Default |
| Mistral OCR | Mistral OCR | Dedicated OCR endpoint, not a chat model |
| OpenRouter | OpenAI-compatible | |
| Hugging Face | OpenAI-compatible | Router endpoint |
| NVIDIA NIM | OpenAI-compatible | |
| Groq | OpenAI-compatible | |
| Cerebras | OpenAI-compatible | |
| Cloudflare Workers AI | OpenAI-compatible | Needs an account ID as well as a token |
| OpenAI | OpenAI-compatible | |
| Claude | Anthropic | |
| Custom | OpenAI-compatible | Your own base URL, model, and key |

Every provider's default model can be swapped for a model ID of your choice in **Settings → AI
Providers**. The defaults are only defaults.

### Your own key

Add one per provider in **Settings → AI Providers**. It's stored in DataStore, encrypted with an
AES-GCM key held in the Android Keystore, and sent in a request header to the one provider it belongs
to — never in a URL, never in a log. Your phone calls that provider's API directly; nothing is
relayed, mirrored, or logged on the way, because there is no machine of ours in the path.

Builds may ship bundled free-tier keys (obfuscated in the APK, pinned to the release signature) so the
feature works before you have your own. They're shared, so they're rate-limited; results carry a badge
saying which kind of key was used. When the shared limit is hit you can wait, add your own key, or
watch an optional rewarded ad for one more scan.

**Fallback is off by default** — the provider you picked is the only one used. Enabled, other
*configured* providers take over when the selected one is exhausted. If your own key is in use,
fallback only considers providers where you also supplied a key: it never silently falls back from
your key to a bundled one. Within a provider, 429 and 5xx are retried with exponential backoff.

---

## Barcode

Real-time CameraX scanning with **ML Kit** (default) or **ZXing-C++**, with pinch-to-zoom and
tap-to-focus. QR Code, Aztec, Data Matrix, PDF417, EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39.

Actions are offered by content type: open a URL, join a Wi-Fi network, dial, email, SMS, save a
contact, add a calendar event, or copy the raw value. EAN/UPC and ISBN codes get a **product lookup**
against Open Food Facts, Open Beauty Facts, Open Pet Food Facts, OpenFDA, Google Books, and Open
Library — only the barcode number is sent.

---

## Privacy

| | On-device | Leaves the device |
| --- | --- | --- |
| Offline OCR (PaddleOCR / ML Kit) | Yes | Nothing |
| Barcode scanning and parsing | Yes | Nothing |
| Product lookup | No | The barcode number only |
| AI Scan | No | The image or file you selected, straight to your provider |
| AI translation | No | The extracted text, straight to your provider |

AI Scan is the only feature that transmits document content, and only when you explicitly start it —
never in the background. Everything that leaves the device goes to a third party **you** picked, never
through a Scanly service, because there is no Scanly service. Scan history stays local. No telemetry
on your documents.

---

## Build

```bash
git clone https://github.com/Azyrn/Scanly.git
cd Scanly
./gradlew assembleDebug
```

Android SDK and JDK 21. Minimum Android 7.0 (API 24), targets API 36. Offline scanning, barcode
scanning, and every export work with no configuration.

For cloud AI, add your key in the app's settings at runtime, or bundle one at build time via
`local.properties` (gitignored):

```properties
GEMINI_API_KEY=your_key_here
```

Also recognized: `OPENROUTER_API_KEY`, `GROQ_API_KEY`, `CEREBRAS_API_KEY`, `NVIDIA_API_KEY`,
`HUGGINGFACE_API_KEY`, `MISTRAL_API_KEY`, `CLOUDFLARE_API_KEY`, `CLOUDFLARE_ACCOUNT_ID`.

Release builds are signed from `local.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) and use R8 full mode. Debug builds use Google's AdMob test ad units.

```bash
./gradlew testDebugUnitTest         # unit tests
./gradlew connectedDebugAndroidTest # instrumented tests (device required)
```

**Stack:** Kotlin 2.3, Jetpack Compose + Material 3 Expressive, MVVM/Hilt/DataStore/WorkManager, ONNX
Runtime, ML Kit, ZXing-C++, CameraX, Retrofit/OkHttp, kotlinx.serialization, Android Keystore.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
