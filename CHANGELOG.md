# Changelog

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
