# Changelog

## [2.9.2] - 2026-01-21
### Added
- **Unified Gallery Scanning**: Single scan flow for both OCR text and QR/barcode detection from gallery images.
- **Smart Barcode Actions**: Auto-detect URLs, WiFi, contacts, phone numbers, and emails from scanned barcodes.
- **QR-Only Mode**: Gallery picker in barcode scanner screen now performs barcode-only detection (no OCR noise).

### Fixed
- **Race Condition**: Fixed gallery picker using proper StateFlow collection instead of unreliable delay.
- **Memory Safety**: Added bitmap downsampling (max 2048px) to prevent OOM on large screenshots.
- **Concurrent Scan Prevention**: Cancel previous scan job before starting new one.
- **Phone Number Validation**: Stricter E.123 format validation to prevent false positives.

### Improved
- **Barcode Deduplication**: Filter duplicate barcodes by raw value.
- **Clean Actions**: One primary action per barcode type (no redundant ShowRaw + Copy).
- **Dead Code Removal**: Removed unused `getActionKey()` and `async`/`awaitAll` imports.

## [2.9.1] - 2026-01-21
### Fixed
- **Version Bump**: Updated app version to 2.9.1.

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
