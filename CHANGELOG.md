# Changelog

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
