<div align="center">

# Scanly

**Point your phone at a page and get the text — offline, or through the AI provider of your choice.**

Scanly is an Android scanner for documents, barcodes, and QR codes. It reads a page two ways:
**fully offline with PaddleOCR**, or with a **vision model that re-types the page as Markdown**.
Then it gives you the result as **Markdown, PDF, Word, CSV, or JSON** — and can **translate** it into
18 languages.

**Your AI key, your provider, your account.** Add your own API key and your phone calls the provider
directly. **There is no Scanly server and no proxy** — nothing we run ever sees your documents,
because we run nothing.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF.svg?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?logo=jetpackcompose&logoColor=white)](#)

</div>

---

## The three things to know first

**1. It works with the radio off.** The default engine is **PaddleOCR PP-OCRv6**, running on your
phone through ONNX Runtime. No key, no account, no network. Scanning, layout detection, table
recognition, and every export work in airplane mode. → [Offline OCR](#offline-ocr)

**2. The AI mode transcribes — it does not summarize.** A vision model re-types the page as Markdown
that mirrors it: same words, same headings, same bold, same line breaks, same tables. That is what
lets it read **handwriting, receipts, forms, multi-column pages, and tables** that classical OCR
chokes on — and why its output drops straight into a PDF or Word file with its structure intact.
→ [AI Scan](#ai-scan)

**3. Bring your own key, straight to the provider.** Eleven providers, or any OpenAI-compatible
endpoint including one you host yourself. Your key is encrypted on-device and sent in a request
header to the one provider it belongs to. Your phone talks to `api.openai.com` or `api.groq.com` or
your own URL **directly — no relay, no proxy, no backend of ours in the middle**. Your account, your
billing, your dashboard, revocable by you at any time. → [Bring your own API key](#bring-your-own-api-key)

---

## What you can do with it

### Scan

- **Documents** — multi-page capture through the ML Kit document scanner, with a CameraX fallback on
  devices without Google Play services.
- **PDFs** — imported and extracted page by page.
- **Images** — from the gallery, up to 3 per scan.
- **Barcodes and QR codes** — real-time, on-device, with an action offered per result type.
- **History** — every scan kept locally with its images. Nothing is uploaded.

### Read

- **Markdown view** — the result rendered as a real document: headings, bold/italic/strikethrough,
  lists, task checkboxes, quotes, code blocks, and tables drawn as actual grids. This is what makes a
  receipt or a form readable at a glance instead of a wall of recognized lines.
- **Original view** — the same result as plain text, when you want the raw characters.
- **Translation** — turn the result into any of 18 languages, in place, cached per language.
  → [Translation](#translation)

### Export

Whatever you can see, you can export — and the file matches the view you are looking at.

| Format | What you get |
| --- | --- |
| **PDF** | The rendered document — headings, emphasis, lists, code blocks, bordered tables, paginated with table headers repeated across pages |
| **Word (.docx)** | The same structure as real OOXML: heading runs, bold/italic/underline, monospaced code, indented lists, and real Word tables you can edit |
| **Markdown (.md)** | The Markdown source, unchanged |
| **CSV** | Table data only, pulled from the tables on screen |
| **JSON** | The text plus per-line bounding boxes, confidence scores, and page numbers (offline PaddleOCR scans) — for when you want to feed the scan into something else |

Everything lands in `Downloads/Scanly` and is offered for sharing. Printing goes through the system
print dialog, which also offers Save-as-PDF. → [Export](#export)

---

## AI Scan

AI Scan is optional and explicit. Nothing is sent anywhere until you run a scan with an online
provider. Offline OCR covers the normal case; AI Scan exists for the pages it cannot read.

### What it actually does

It sends your image or file to a **vision model** and asks for a **visual-fidelity transcription in
Markdown** — not a description of the document, not a summary of it. The model is told to reproduce
what it sees:

| It does | It does not |
| --- | --- |
| Re-type every word, number, and symbol as printed | Summarize, shorten, or "clean up" your text |
| Map what it sees to Markdown: `#` headings by visual size, `**bold**`, `*italic*`, `~~strikethrough~~`, `<u>underline</u>` | Translate or correct the source (translation is a separate, explicit action) |
| Keep the layout: line breaks, blank lines, indentation, column alignment | Re-wrap or merge lines |
| Rebuild tables as real Markdown tables, and checkboxes as `[ ]` / `[x]` | Invent structure that isn't on the page |
| Mark genuinely unreadable text `[unclear]` | Silently guess |

That fidelity is the whole point. Because the model returns *structure* and not prose, the result
goes straight into the Markdown view, and from there into a PDF, `.docx`, or CSV with its headings
and tables intact. Multi-column pages are transcribed column by column, left to right. PDFs and
plain-text files use their own extraction prompt under the same rule: every character, nothing
summarized.

Text streams in token by token while the model works, so you watch the page appear rather than
staring at a spinner. Up to 3 images, or a PDF or text file, per scan.

**Its limits are the model's limits.** A vision model can misread a smudged digit where a
purpose-built OCR engine would too, and a shared free-tier key can be rate-limited. It is a different
tool from Offline OCR, not a strictly better one — if the page is clean printed text and you want
determinism and zero network, use [Offline OCR](#offline-ocr).

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

Each provider ships with a sensible default model, and **every one of them can be swapped for a model
ID of your choice** in **Settings → AI Providers**. The defaults are only defaults. With **Custom**,
you supply the base URL, model, and key — so any OpenAI-compatible endpoint works, including a local
or self-hosted one.

### Bring your own API key

**Use your own key, on your own account, with your own provider.** Add one per provider in
**Settings → AI Providers**.

- **Your key stays on your device.** It is stored in DataStore, encrypted with an AES-GCM key held in
  the Android Keystore. It is sent in a request *header* to the one provider it belongs to, and
  nowhere else — never in a URL, never in a log.
- **There is no Scanly server and no proxy.** Your phone calls the provider's API directly:
  `generativelanguage.googleapis.com`, `api.anthropic.com`, `api.openai.com`, `api.groq.com`, and so
  on. Your documents and your key never pass through any machine of ours, because there isn't one.
  Nothing is relayed, mirrored, or logged on the way — the only party that sees the request is the
  provider you chose.
- **Your account, your billing, your rate limits.** Usage shows up on your provider dashboard, and
  you can revoke the key there at any time.

Builds may also ship bundled free-tier keys for some providers (obfuscated in the APK and pinned to
the release signature) so the feature works before you have a key of your own. These are shared, so
they are rate-limited; every result carries a badge saying whether the scan used a built-in key or
yours. When the shared limit is hit, you can wait, add your own key, or watch an optional rewarded ad
for one extra scan.

### Streaming, retries, and fallback

Every provider streams except Mistral OCR, whose endpoint is not a chat API. If a provider accepts a
streaming request but sends nothing, the run is retried without streaming. Failed attempts are
retried with exponential backoff on HTTP 429 and 5xx.

**Provider fallback is off by default** — the provider you selected is the only one used. Enabling
**Settings → AI Providers → fallback** lets other *configured* providers take over when the selected
one is exhausted. If your own key is in use, fallback only considers providers where you also
supplied a key: **the app never silently falls back from your key to a bundled one.**

### What leaves the device

| | Runs on-device | Leaves the device |
| --- | --- | --- |
| Offline OCR (PaddleOCR / ML Kit) | Yes | Nothing |
| Barcode scanning and parsing | Yes | Nothing |
| Product lookup | No | The barcode number only, to the lookup APIs |
| AI Scan | No | The image or file you selected, straight to the provider you chose |
| AI translation | No | The extracted text, straight to the provider you chose |

Every row that leaves the device goes to a third party **you** picked — never through a Scanly
service.

---

## Offline OCR

**If you want local OCR, this is it — and it is already the default.** The engine is **PaddleOCR
PP-OCRv6**, executed on-device with ONNX Runtime: no API key, no account, no network, nothing leaves
the phone. Scanning, layout detection, table recognition, and every export work in airplane mode.
ML Kit text recognition is available as a faster alternative engine.

**Scripts.** Nine packs. Universal (Latin/Chinese/Japanese) and Arabic are bundled in the APK; Korean,
Cyrillic, Devanagari, Thai, Greek, Tamil, and Telugu download on demand — that download is the one
time this feature touches the network, and each pack is verified by size and SHA-256 before it
installs. Downloads continue if you leave the settings screen.

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
- **Fix flipped lines** corrects individual upside-down lines within an otherwise upright page.
- **Flatten curved pages** dewarps books and folded documents. Slower — that is why it is opt-in.
- **Detect layout** classifies regions (headings, paragraphs, tables) and works out the reading order.
  **This is what produces the Markdown view offline**; with it off, you get plain recognized lines.
- **Recognize tables** reconstructs table structure into Markdown grids — which is also what makes CSV
  export possible from an offline scan.

---

## Markdown

Any result with Markdown behind it — an AI transcription, or an offline scan with layout detection on
— is shown in two views you can switch between at any time:

- **Markdown** — the rendered document: headings, bold/italic/strikethrough, inline code, bullet and
  numbered lists, task checkboxes, block quotes, code blocks, horizontal rules, and tables drawn as
  real grids. **This is the view where you actually read the thing** — a receipt's totals line up, a
  form's fields are legible, a table is a table.
- **Original** — the same result as plain text. For an AI result that is the Markdown source with its
  syntax stripped back out; for an offline scan it is the recognized lines.

### WYSIWYE — What You See Is What You Export

Exports read the currently visible view and nothing else. There is no hidden "original" text a file
might silently fall back to: if you are looking at rendered Markdown, that is what your PDF and Word
files contain; if you are looking at plain text, your files contain plain text, with no Markdown
interpretation applied.

---

## Translation

Any result can be translated in place from the result screen into **18 languages** — Arabic, Chinese,
Dutch, English, French, German, Hindi, Italian, Japanese, Korean, Marathi, Polish, Portuguese,
Russian, Spanish, Telugu, Turkish, and Urdu.

Translation is an explicit, separate action: the transcription itself is never translated or
"corrected" behind your back. Each language is cached, so switching back and forth costs nothing.
It runs through your AI provider, so it sends the extracted text — not the image — straight to the
provider you chose.

---

## Export

All exports are written to `Downloads/Scanly` and then offered for sharing. Each format respects the
view you are in:

| Format | Original view | Markdown view |
| --- | --- | --- |
| **PDF** | The plain text exactly as displayed, no Markdown parsing | The rendered document: headings, emphasis, lists, quotes, code blocks, and bordered table grids, paginated with table headers repeated across pages |
| **Word (.docx)** | The plain text, one paragraph per line | The same structure as OOXML: sized heading runs, bold/italic/strikethrough/underline runs, monospaced code, indented lists, bordered quotes, and real Word tables |
| **Markdown (.md)** | The visible plain text, written as-is — no Markdown is inferred or reconstructed | The Markdown source, unchanged |
| **CSV** | Columns detected in the visible text (tab-, pipe-, or multi-space-separated rows); unavailable when there are none | Cells from the visible Markdown tables, with inline formatting removed |
| **JSON** | Offline PaddleOCR scans only: the text plus per-line bounding boxes, confidence, and page number | Same, with the visible text |

When the visible text contains no table at all, CSV is shown as unavailable rather than handing you an
empty or nonsensical file.

Printing is available from the same screen and follows the same rule — the print preview renders
Markdown or plain text to match the view you are in.

---

## Barcode

Real-time scanning through CameraX, using **ML Kit** by default or **ZXing-C++** (selectable in
settings), with pinch-to-zoom and tap-to-focus.

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

**Product lookup** resolves a scanned code against Open Food Facts, Open Beauty Facts, Open Pet Food
Facts, OpenFDA, Google Books, and Open Library, in priority order. Only the barcode number is sent.

---

## Privacy

- **Offline OCR never uploads your documents.** PaddleOCR and ML Kit run entirely on-device. Scanning,
  layout detection, table recognition, and every export work with no network connection at all.
- **AI Scan is the only feature that transmits document content**, and only when you explicitly start
  a scan or a translation with an online provider. Nothing is sent in the background, ever.
- **Barcode decoding is on-device.** Product lookup sends the barcode number — and nothing else.
- **There is no Scanly server.** The app has no backend and no proxy. When you run an AI Scan, your
  phone calls your provider's API directly — your documents and your key never transit any
  infrastructure we control, because we operate none.
- **Your API keys stay on your device.** Stored encrypted (AES-GCM, key held in the Android Keystore),
  transmitted only in request headers to the provider they belong to, never written to logs or URLs.
  Revoke a key on your provider's dashboard and it is dead everywhere.
- **Scan history is local**, kept as a JSON file with its images in the app's private storage.
- **No telemetry on your documents.**

---

## UI

Material 3 Expressive, built entirely in Jetpack Compose — dynamic color plus selectable palettes,
dark theme, and a pure-black OLED mode.

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

**Offline scanning, barcode scanning, and every export work out of the box with no configuration.**

For cloud AI you can either add your key in the app's settings at runtime, or bundle one at build time
by adding it to `local.properties` (gitignored):

```properties
GEMINI_API_KEY=your_key_here
```

Recognized properties: `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `GROQ_API_KEY`, `CEREBRAS_API_KEY`,
`NVIDIA_API_KEY`, `HUGGINGFACE_API_KEY`, `MISTRAL_API_KEY`, `CLOUDFLARE_API_KEY`,
`CLOUDFLARE_ACCOUNT_ID`.

Release builds are signed from `local.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) and use R8 full mode. Debug builds use Google's AdMob test ad units.

```bash
./gradlew testDebugUnitTest         # unit tests
./gradlew connectedDebugAndroidTest # instrumented tests (device required)
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
