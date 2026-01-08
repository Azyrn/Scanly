# Changelog

All notable changes to this project will be documented in this file.

## [1.6.0-beta.1] - 2026-01-08

### Changed
- **Navigation Transitions**: Enhanced animations using standard Google Gallery transitions (EaseOutExpo, 500ms) for consistent, buttery smoothness.
- **Settings UI**: Updated "About" section header for improved clarity.

### Fixed
- **Theme Flicker**: Resolved a startup race condition where the default system theme would flash before the user's saved theme loaded.
- **Navigation Lag**: Aligned transition directions (Push/Pull) and removed artificial delays on back navigation for instant responsiveness.

## [1.4.0-beta.1] - 2026-01-04

### Added
- **Smart Actions**: Automatic detection of URLs, emails, phone numbers, and WiFi from both live barcode scans and OCR results with Material 3 action chips.
- **Enhanced OCR Engine**: Implemented advanced preprocessing pipeline including CLAHE (local contrast), Sauvola (local binarization), and morphological repair for low-quality images.
- **Scanning Robustness**: Added ML Kit structured data extraction and intelligent frame analysis to improve recognition of difficult barcodes.

### Changed
- **Scanner UX**: Introduced a 1.5s scan cooldown to prevent redundant triggers and duplicate history entries.
- **UI Architecture**: Decoupled smart actions into a reusable `ScanActionsRow` component for consistency across screens.

### Fixed
- **System Audit Fixes**: Resolved critical race condition in `SeedColorProvider`, eliminated edit-in-read anti-pattern in `SettingsDataStore`, and synchronized theme updates.
- **Barcode Formatting**: Fixed an issue where "Copy" in the live scanner didn't always capture the latest result.
- **Icon Deprecations**: Updated to the latest Material 3 AutoMirrored icons.

## [1.3.0] - 2025-12-29

### Added
- **OCR Accuracy**: Integrated adaptive thresholding, quality detection, and noise reduction for superior text extraction.
- **Visual Settings**: Added tappable theme cards (Light, Dark, OLED) with mini-previews.
- **Language Search**: Added search bar and "Pin Selected" sorting to the Language settings.
- **PDF Reliability**: Implemented sequential page processing and memory tracking for multi-page documents.

### Changed
- **Results UI**: Optimized screen layout to prioritize text visibility; added tap-to-expand for source images.
- **Engine Architecture**: Enforced strict separation where ML Kit handles ONLY barcodes and Tesseract handles ONLY text.
- **Arabic Handling**: Improved Right-to-Left (RTL) text rendering and line spacing.

### Fixed
- **Data Corruption**: Fixed a critical issue where "0" was incorrectly post-processed as "O".
- **Memory Leaks**: Added proper resource cleanup and job cancellation in `ScanViewModel`.

## [1.2.0] - 2025-12-28

### Changed
- **Settings UI**: Refactored Settings from a dialog to a dedicated full-screen experience with smooth transitions.
- **Default Configuration**: All supported OCR languages are now enabled by default for new installations.
- **Navigation**: Improved back navigation logic across Camera, History, and Barcode Scanner screens.

### Fixed
- **History Flow**: Resolved an issue where opening a history item would trigger an automatic re-scan.
- **Build Configuration**: Enabled resource shrinking (`isShrinkResources`) and code minification for optimized release builds.
- **Git Repository**: Fixed push errors by removing accidental large file commits (APKs).
