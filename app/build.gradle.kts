import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
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

// local.properties (gitignored): bundled keys, signing config, real AdMob ids.
val secretsProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val SECRET_PROPERTIES = listOf(
    "OPENROUTER_API_KEY", "GEMINI_API_KEY", "MISTRAL_API_KEY", "HUGGINGFACE_API_KEY",
    "NVIDIA_API_KEY", "CLOUDFLARE_API_KEY", "CLOUDFLARE_ACCOUNT_ID", "GROQ_API_KEY", "CEREBRAS_API_KEY"
)

// Must stay byte-identical to core.security.Secrets so the runtime can decode.
private val SECRET_PEPPER: ByteArray = intArrayOf(
    0x53, 0x63, 0x6E, 0x6C, 0x79, 0x9A, 0x17, 0x42,
    0xC3, 0x08, 0xEE, 0x5B, 0x71, 0x2D, 0xF0, 0x64
).map { it.toByte() }.toByteArray()

private fun secretsKeystream(seed: ByteArray, len: Int): ByteArray {
    val out = ByteArray(len)
    var pos = 0
    var counter = 0
    while (pos < len) {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(seed)
        md.update(
            byteArrayOf(
                (counter ushr 24).toByte(), (counter ushr 16).toByte(),
                (counter ushr 8).toByte(), counter.toByte()
            )
        )
        val block = md.digest()
        val n = minOf(block.size, len - pos)
        System.arraycopy(block, 0, out, pos, n)
        pos += n
        counter++
    }
    return out
}

// XOR-obfuscate + Base64 a bundled secret so it never ships as a plaintext string.
fun encodeSecret(propertyKey: String): String {
    val plain = secretsProps.getProperty(propertyKey).orEmpty()
    if (plain.isEmpty()) return ""
    val bytes = plain.toByteArray(Charsets.UTF_8)
    val ks = secretsKeystream(SECRET_PEPPER, bytes.size)
    val xored = ByteArray(bytes.size) { (bytes[it].toInt() xor ks[it].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(xored)
}

// SHA-256 of the release signing cert; pins bundled keys to this build's signature.
// Empty when the keystore is unavailable (e.g. CI without secrets) — pin then off.
fun releaseCertSha256(): String {
    return try {
        val path = secretsProps.getProperty("storeFile") ?: return ""
        val f = File(path)
        if (!f.exists()) return ""
        val ks = KeyStore.getInstance("PKCS12")
        f.inputStream().use { stream ->
            ks.load(stream, secretsProps.getProperty("storePassword")?.toCharArray())
        }
        val alias = secretsProps.getProperty("keyAlias") ?: ks.aliases().nextElement()
        val cert = ks.getCertificate(alias)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        digest.joinToString("") { b -> "%02X".format(b) }
    } catch (e: Exception) {
        ""
    }
}


android {
    namespace = "com.skeler.scanely"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.skeler.scanely"
        minSdk = 24
        targetSdk = 36
        versionCode = 16
        versionName = "3.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bundled free-tier keys, XOR+Base64 obfuscated and pinned to the release
        // signature. Never plaintext in the APK; decoded at runtime by core.security.Secrets.
        for (name in SECRET_PROPERTIES) {
            buildConfigField("String", name, "\"${encodeSecret(name)}\"")
        }
        // Expected release-signing SHA-256; runtime refuses bundled keys on a mismatch.
        buildConfigField("String", "EXPECTED_SIGNATURE", "\"${releaseCertSha256()}\"")
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
            storeFile = file(secretsProps.getProperty("storeFile") ?: "keystore.jks")
            storePassword = secretsProps.getProperty("storePassword")
            keyAlias = secretsProps.getProperty("keyAlias")
            keyPassword = secretsProps.getProperty("keyPassword")
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

            // Real AdMob ids from local.properties; test ids as fallback.
            manifestPlaceholders["admobAppId"] =
                secretsProps.getProperty("ADMOB_APP_ID") ?: admobTestAppId
            buildConfigField(
                "String",
                "ADMOB_REWARDED_AD_UNIT_ID",
                "\"${secretsProps.getProperty("ADMOB_REWARDED_AD_UNIT_ID") ?: admobTestRewardedUnitId}\""
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