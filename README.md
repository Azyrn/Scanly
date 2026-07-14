<div align="center">

# Scanly

**Point your phone at a page and get the text — offline, or through the AI provider of your choice.**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF.svg?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](#)

</div>

An Android scanner for documents, barcodes, and QR codes.

**Works with the radio off.** The default engine is **PaddleOCR PP-OCRv6**, on-device via ONNX
Runtime. No key, no account, no network — scanning, layout detection, table recognition, and every
export work in airplane mode. Nine script packs; Latin/Chinese/Japanese and Arabic ship in the APK,
the rest download on demand.

**The AI mode transcribes, it doesn't summarize.** A vision model re-types the page as Markdown that
mirrors it: same words, same headings, same bold, same line breaks, same tables. That's what lets it
read handwriting, receipts, forms, and multi-column pages that classical OCR chokes on — and why the
result drops into a PDF or Word file with its structure intact. It streams in as the model works.

**Your key goes straight to the provider.** Gemini, OpenAI, Claude, Groq, OpenRouter, Mistral OCR,
Cerebras, NVIDIA NIM, Hugging Face, Cloudflare Workers AI, or any OpenAI-compatible endpoint —
including one you host. Pick the model yourself. Your key is encrypted on-device (AES-GCM, Android
Keystore) and sent in a request header to the one provider it belongs to. **There is no Scanly server
and no proxy:** your phone calls `api.openai.com` or your own URL directly. Your account, your
billing, revocable by you. Bundled free-tier keys exist so the feature works before you have your own;
the app never silently falls back from your key to one of those.

**Read it, then take it with you.** Results open in a rendered **Markdown view** — headings, emphasis,
lists, checkboxes, code blocks, and tables as real grids, so a receipt's totals line up and a table is
a table. Switch to plain text at any time. **Translate** in place into 18 languages, cached per
language. Then export — and what you see is what you get, with no hidden text a file falls back to:

- **PDF** — the rendered document, paginated, with table headers repeated across pages
- **Word (.docx)** — real OOXML you can edit: heading runs, emphasis, lists, Word tables
- **Markdown** — the source, unchanged
- **CSV** — the tables on screen, and nothing when there are none
- **JSON** — text plus per-line boxes, confidence, and page number, for feeding into something else

Files land in `Downloads/Scanly`. Printing goes through the system dialog, which also offers
Save-as-PDF.

**Barcodes** scan in real time (ML Kit or ZXing-C++) with pinch-zoom and tap-to-focus, and offer an
action per content type — open a URL, join a Wi-Fi network, dial, save a contact, add an event. EAN,
UPC, and ISBN get a product lookup; only the number is sent.

**Material 3 Expressive**, entirely Jetpack Compose — dynamic color, dark theme, pure-black OLED mode.

## Privacy

| | On-device | Leaves the device |
| --- | --- | --- |
| Offline OCR, barcode scanning | Yes | Nothing |
| Product lookup | No | The barcode number only |
| AI Scan | No | The image or file you picked, straight to your provider |
| AI translation | No | The extracted text, straight to your provider |

AI Scan is the only feature that transmits document content, and only when you start it — never in the
background. Everything that leaves the device goes to a third party **you** picked, never through a
Scanly service, because there is no Scanly service. History stays local. No telemetry on your
documents.

## Build

```bash
git clone https://github.com/Azyrn/Scanly.git && cd Scanly && ./gradlew assembleDebug
```

Android SDK, JDK 21. Min API 24, targets API 36. Offline scanning, barcodes, and exports need no
configuration. For cloud AI, add a key in the app's settings, or bundle one at build time in
`local.properties` (gitignored) as `GEMINI_API_KEY`, `GROQ_API_KEY`, `OPENROUTER_API_KEY`, and so on.
Release builds are signed from `local.properties` and use R8 full mode.

Kotlin 2.3 · Compose · Material 3 Expressive · MVVM/Hilt/DataStore · ONNX Runtime · ML Kit ·
ZXing-C++ · CameraX · Retrofit · Android Keystore.

## License

Apache 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
