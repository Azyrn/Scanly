import java.util.Properties

// Google's public test IDs — safe for development, never serve real ads.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    // alias(libs.plugins.detekt) // Incompatible with AGP 8.13 - using CLI in CI
    // alias(libs.plugins.google.services) // Disabled - no google-services.json
    // alias(libs.plugins.firebase.crashlytics) // Disabled - no google-services.json
}


android {
    namespace = "com.skeler.scanely"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.skeler.scanely"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "3.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject API key from local.properties (gitignored)
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${localProperties.getProperty("OPENROUTER_API_KEY") ?: ""}\""
        )
        // Bundled default provider key so AI scans work without user setup.
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY") ?: ""}\""
        )
        // Bundled free-tier Mistral OCR key.
        buildConfigField(
            "String",
            "MISTRAL_API_KEY",
            "\"${localProperties.getProperty("MISTRAL_API_KEY") ?: ""}\""
        )
        // Bundled free-tier Hugging Face token for LightOnOCR.
        buildConfigField(
            "String",
            "HUGGINGFACE_API_KEY",
            "\"${localProperties.getProperty("HUGGINGFACE_API_KEY") ?: ""}\""
        )
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // ABI splits - reduces APK size by ~50%
    // Most devices are arm64-v8a (modern phones)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true // Also build universal APK for fallback
        }
    }

    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { localProperties.load(it) }
            }

            storeFile = file(localProperties.getProperty("storeFile") ?: "keystore.jks")
            storePassword = localProperties.getProperty("storePassword")
            keyAlias = localProperties.getProperty("keyAlias")
            keyPassword = localProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // Own id so debug installs alongside the release-signed production app.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = admobTestAppId
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$admobTestRewardedUnitId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Real AdMob IDs live in local.properties (gitignored); test IDs as fallback.
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { localProperties.load(it) }
            }
            manifestPlaceholders["admobAppId"] =
                localProperties.getProperty("ADMOB_APP_ID") ?: admobTestAppId
            buildConfigField(
                "String",
                "ADMOB_REWARDED_AD_UNIT_ID",
                "\"${localProperties.getProperty("ADMOB_REWARDED_AD_UNIT_ID") ?: admobTestRewardedUnitId}\""
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase (disabled - add google-services.json to re-enable)
    // implementation(platform(libs.firebase.bom))
    // implementation(libs.firebase.analytics)
    // implementation(libs.firebase.crashlytics)

    // Material Color Utilities (CorePalette for tonal palette generation)
    implementation(libs.material.color.utilities)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)

    implementation(libs.serialization.json)
    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    
    // Coil for image loading
    implementation(libs.coil.compose)
    
    // Accompanist Permissions
    implementation(libs.accompanist.permissions)
    
    // Google Mobile Ads (rewarded ads for extra AI scans)
    implementation(libs.play.services.ads)

    // Google ML Kit Barcode Scanning
    implementation(libs.mlkit.barcode)
    
    // Google ML Kit Text Recognition (On-Device)
    implementation(libs.mlkit.text.recognition)

    // Google ML Kit Document Scanner (edge detection, perspective correction, auto-capture)
    implementation(libs.mlkit.document.scanner)
    
    // Retrofit for network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)
    
    // Baseline Profile
    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Leak detection in debug builds only; never ships in release.
    debugImplementation(libs.leakcanary)
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}