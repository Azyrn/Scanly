# Scanly

<p align="center">
  <img src="app/src/main/res/drawable/ic_scanly.xml" width="120" alt="Scanly Logo"/>
</p>

<p align="center">
  <b>Modern Android app for AI-powered text extraction, document scanning, and barcode recognition.</b>
</p>

<p align="center">
  <a href="https://github.com/Azyrn/Scanly/releases"><img src="https://img.shields.io/github/v/release/Azyrn/Scanly?style=flat-square" alt="Release"/></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen?style=flat-square" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple?style=flat-square" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?style=flat-square" alt="Compose"/>
</p>

---

## ✨ Features

### 📷 AI Text Extraction
Extract text from images with high accuracy using **Google Gemini AI**. Simply capture a photo or select from your gallery, and Scanly will intelligently recognize and extract all text content.

### 📄 PDF Document Support  
Import PDF documents and extract text from all pages. Perfect for digitizing printed documents, contracts, receipts, and more.

### 🌐 Translation
Translate extracted text into **15+ languages** instantly. Supports major languages including Spanish, French, German, Chinese, Japanese, Arabic, and more.

### 📶 Offline OCR
When you don't have internet access, Scanly uses **Google ML Kit** for on-device text recognition. No data leaves your phone.

### 📊 Barcode & QR Scanner
Scan any barcode or QR code with smart action detection:
- 🔗 **URLs** — Open in browser
- 📞 **Phone numbers** — Dial directly  
- 📧 **Email** — Compose message
- 💬 **SMS** — Send text
- 📶 **WiFi** — Connect to network
- 👤 **Contacts** — Add to address book
- 📍 **Location** — Open in maps

### 🍎 Food Product Lookup
Scan product barcodes (EAN-13, UPC-A) to get detailed nutrition information from the **Open Food Facts** database:
- Product name and brand
- Nutri-Score rating
- NOVA food processing group
- Full nutrition facts
- Ingredients list

### 📚 History
All your scans are saved locally with timestamps. Easily revisit, copy, or share previous results.

---

## 🛠 Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Camera | CameraX |
| OCR | Google ML Kit (offline) |
| AI | Google Gemini API |
| Networking | Retrofit + OkHttp |
| Image Loading | Coil |
| Performance | Baseline Profiles |

---

## 📱 Requirements

- Android 8.0 (API 26) or higher
- Camera permission for scanning
- Internet for AI features (offline OCR works without)

---

## 📥 Installation

Download the latest APK from the [Releases](https://github.com/Azyrn/Scanly/releases) page.

---

## 🏗 Building from Source

```bash
git clone https://github.com/Azyrn/Scanly.git
cd Scanly
./gradlew assembleRelease
```

---

## 📄 License

```
MIT License

Copyright (c) 2024 Azyrn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

<p align="center">
  Made with ❤️ using Kotlin & Jetpack Compose
</p>
