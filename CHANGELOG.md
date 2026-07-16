# Changelog

## [3.3.1] - 2026-07-16 (versionCode 19)
### Added
- **Longer PDF scans**: AI Scan now reads up to 20 pages of a PDF, up from 3

### Fixed
- **Product lookup**: a product missing from Open Food Facts now reads "not found" instead of
  "Errors: HTTP 404" — v3 answers an absent barcode with a 404, which was being treated as a failure

### Changed
- **One request per scan**: up to 5 images now travel in a single request instead of one request
  each, and a PDF goes to Mistral OCR whole rather than a request per page — several times less
  free-tier quota per scan
- **Per-provider page limits**: a scan is trimmed to what the provider documents (5 on Groq and
  Cerebras, 1 where the cap is undocumented) and the result says so, rather than being split across
  requests or rejected outright
- **AI Scan picks one PDF at a time**; the image picker now takes 5

## [3.3.0] - 2026-07-14 (versionCode 18)
### Added
- **Barcode scanner controls**: pinch-to-zoom and tap-to-focus, with the scan box lifted clear of the controls
- **WYSIWYE exports**: exports now match the preview you see on screen
- **New launcher icon** and a full-resolution splash logo

### Fixed
- **Scan history**: a corrupt `scan_history.json` no longer wipes every saved scan
- **Custom AI endpoints**: no longer crash the app or leak API keys
- **Arabic OCR**: pages are read the right way up and embedded Latin words are preserved
- **AI Scan**: stopped requesting camera permission it never needed
- **Model downloads**: continue when you leave the Settings screen
- **AI providers**: the required fields for each provider are now stated up front

### Changed
- **Open Food Facts**: migrated to API v3
- **Startup and packaging**: single-ABI packaging, lazier startup, LUT tensor normalization

## [3.2.0] - 2026-07-12 (versionCode 17)
### Added
- **Offline OCR engine**: PaddleOCR PP-OCRv6 via ONNX Runtime (detection, recognition, classification,
  layout) — pure Kotlin, no key, no network
- **Markdown results view**: Original/Markdown chips and a Compose renderer for tables, headings,
  emphasis, lists, checkboxes, code and quotes; printing via the system dialog
- **Exports**: Word (`.docx`), Markdown and JSON alongside PDF and CSV, saved to `Downloads/Scanly`
- **ZXing barcode engine** and content parser
- **New launcher branding**, provider logos, and a NOTICE file for attribution

### Fixed
- Provenance-gated markdown detection; copy/export/print/edit now consistent with the visible view
- WebView print leak; print rendering moved off the main thread
- Escaped-pipe and unmatched-marker parsing in the Markdown renderer

## [3.1.3] - 2026-07-09 (versionCode 16)
### Added
- **Real provider logos** in the AI Providers screen — brand wordmarks tinted in each provider's color,
  replacing the generic Material glyphs

### Security
- **Personal API keys are now encrypted at rest.** Keys you type in (OpenRouter, Gemini, OpenAI, Claude,
  Mistral, Hugging Face, NVIDIA, Groq, Cerebras, Cloudflare, custom) were stored as plain text in the
  settings DataStore and were eligible for Android Auto Backup. They are now encrypted with AES-256-GCM
  backed by the hardware Keystore, and the settings file is excluded from cloud backup and device
  transfer. Keys saved before this change still work and are upgraded to ciphertext on next save.

## [3.2.0] - 2026-01-23 (versionCode 13, superseded)
### Added
- **Multi-Source Product Lookup**: Books (Google Books, Open Library), Medicine (OpenFDA), Cosmetics (Open Beauty Facts), Pet Food
- **Empty State Fallbacks**: All product categories now show helpful messages when data is missing
- **Network Resilience**: 10s timeout per engine, exponential backoff retry (2 attempts)
- **Image Preprocessing**: Auto-retry with contrast enhancement for faded documents
- **Unified Error UI**: Consistent error states with retry buttons
- **GitHub Actions CI**: Automated lint, tests, and build on push

### Changed
- **ProductDetailSheet.kt**: Refactored from 540 → 230 lines (57% reduction)
- **ScanViewModel.kt**: Extracted RateLimitManager, reduced from 436 → 180 lines
- **APK Size**: Added ABI splits for ~50% smaller per-device APKs

### Fixed
- **Cosmetics Empty Data**: Products now show allergens, categories, and labels (not just ingredients)
- **Medicine Display**: Added manufacturer, recall warnings, contraindications, FDA approval date
- **Book Display**: Added language and ISBN-10 fields

### Performance
- **Coil Optimization**: 25% heap memory cache, 100MB disk cache
- **Resource Cleanup**: Removed 7 unused drawable resources

### New Files
- `RateLimitManager.kt` — Extracted rate limiting singleton
- `RetryUtils.kt` — Exponential backoff utility
- `ImagePreprocessor.kt` — OCR/barcode enhancement
- `ErrorStateContent.kt` — Unified error component
- `ui/components/product/` — Category-specific content sections

## [3.1.0] - 2026-01-23
### Added
- **Food Product Lookup**: Scan product barcodes (EAN-13, UPC-A) to view product info
- **Open Food Facts Integration**: Free API for nutrition data, Nutri-Score, ingredients
- **FoodProductSheet**: Rich bottom sheet with product image, nutrition facts, and badges
- **Retrofit Networking**: Added Retrofit 2.11 + OkHttp 4.12 + kotlinx-serialization converter

### New Files
- `FoodProduct.kt` — Product data model with API DTOs
- `OpenFoodFactsApi.kt` — Retrofit interface
- `FoodRepository.kt` — API wrapper with barcode validation
- `FoodProductSheet.kt` — Material 3 product display sheet

## [3.0.0] - 2026-01-23
### Major Architecture Refactoring
This release represents a significant codebase modernization, reducing screen file sizes by 56% through component extraction.

### Changed
- **HomeScreen.kt**: 605 → 274 lines (55% reduction)
- **ResultsScreen.kt**: 578 → 254 lines (56% reduction)
- **BarcodeScannerScreen.kt**: 542 → 232 lines (57% reduction)

### Added
- **MainActionButton.kt**: Reusable card-style action button component
- **GamifiedAiFab.kt**: AI FAB with progress ring and haptic feedback
- **AiModeBottomSheet.kt**: AI mode selection bottom sheet
- **LanguageChipRow.kt**: Translation language selection chips
- **TextDisplayComponents.kt**: Shared text display states (Processing, Translating, Empty)
- **BarcodeScannerComponents.kt**: Camera preview, overlay, and action sheets

### Improved
- **Build Performance**: Smaller compilation units for faster incremental builds
- **Testability**: Decoupled state classes like `RateLimitDisplayState`
- **Reusability**: All extracted components can be used across screens

## [2.9.2] - 2026-01-22
### Added
- **Android 14+ Partial Photo Access**: Added `READ_MEDIA_VISUAL_USER_SELECTED` permission support.
- **WiFi QR Support**: Added `CHANGE_WIFI_STATE` permission for WiFi network suggestions.
- **Unified Gallery Scanning**: Single scan flow for both OCR text and QR/barcode detection.
- **Smart Barcode Actions**: Auto-detect URLs, WiFi, contacts, phone numbers, and emails.

### Fixed
- **Critical Lint Error**: Fixed missing permission causing crash on WiFi QR code scans.
- **API 30 Compatibility**: Added version check for `HapticFeedbackConstants.CONFIRM`.
- **Race Condition**: Fixed gallery picker using proper StateFlow collection.
- **Memory Safety**: Bitmap downsampling (max 2048px) to prevent OOM.

### Improved
- **Dependency Updates**: CameraX 1.5.2, Compose BOM 2026.01.00, Material3 1.5.0-alpha12.
- **KTX Extensions**: Migrated `Uri.parse()` to `.toUri()` across 5 files.
- **Composable Naming**: Renamed picker functions to `remember*` pattern per conventions.
- **Version Catalog**: Moved ML Kit and lifecycle deps to centralized catalog.
- **Resource Cleanup**: Removed 7 unused color resources.

## [2.9.0] - 2026-01-14
### Added
- **Multi-File AI Processing**: Select multiple images or PDFs at once for batch analysis.
- **Gemini API Integration**: Replaced legacy AI service with Google Gemini for improved accuracy and speed.
- **Rate Limit Persistence**: Cooldown state now persists across app restarts (DataStore integration).

### Fixed
- **Rate Limit Abuse**: Fixed critical bug where restarting the app bypassed the 60s cooldown.
- **Build System**: Removed dependency on `google-services.json` to allow building without Firebase credentials.
- **UI Progress**: Added detailed progress indicators for multi-file processing.

## [2.7.0] - 2026-01-11
### Added
- **UI Polish**: Added Wikipedia-style Results screen with hero images and clean typography.
- **Haptics**: Added haptic feedback when AI cooldown completes.
