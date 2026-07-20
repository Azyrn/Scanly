import com.android.build.api.variant.FilterConfiguration
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

// Google test AdMob IDs — never serve real ads.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    // detekt/google-services/crashlytics disabled (AGP 8.13 / no google-services.json)
}

// local.properties (gitignored): keys, signing, real AdMob ids.
val secretsProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val SECRET_PROPERTIES = listOf(
    "OPENROUTER_API_KEY", "GEMINI_API_KEY", "MISTRAL_API_KEY", "HUGGINGFACE_API_KEY",
    "NVIDIA_API_KEY", "CLOUDFLARE_API_KEY", "CLOUDFLARE_ACCOUNT_ID", "GROQ_API_KEY", "CEREBRAS_API_KEY"
)

// Must match core.security.Secrets pepper/keystream byte-for-byte.
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

// XOR + Base64 so secrets never ship as plaintext strings.
fun encodeSecret(propertyKey: String): String {
    val plain = secretsProps.getProperty(propertyKey).orEmpty()
    if (plain.isEmpty()) return ""
    val bytes = plain.toByteArray(Charsets.UTF_8)
    val ks = secretsKeystream(SECRET_PEPPER, bytes.size)
    val xored = ByteArray(bytes.size) { (bytes[it].toInt() xor ks[it].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(xored)
}

// Release cert SHA-256 for key pin; empty if keystore missing (pin off).
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
    compileSdk = 37

    defaultConfig {
        applicationId = "com.skeler.scanely"
        minSdk = 24
        targetSdk = 37
        versionCode = 19
        versionName = "3.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Obfuscated free-tier keys; runtime decode in Secrets (signature-pinned).
        for (name in SECRET_PROPERTIES) {
            buildConfigField("String", name, "\"${encodeSecret(name)}\"")
        }
        buildConfigField("String", "EXPECTED_SIGNATURE", "\"${releaseCertSha256()}\"")
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // Per-ABI outputs for every variant; debug ones are pruned to a single ABI
    // in the androidComponents block below (the splits DSL is not variant-aware).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
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
            // Side-by-side with release-signed production install.
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

            // Real AdMob from local.properties; test ids as fallback.
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

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Packaging four APKs (universal alone is ~250 MB of mostly ONNX assets) is the
// slow tail of a build, so variants can be pruned to a single ABI's APK.
// Variant-scoped, so it holds for any task from any launcher (Studio, CLI, CI).
//   debug:   always one ABI; default arm64-v8a, -PdebugAbi=x86_64 for an emulator.
//   release: all four by default; releaseAbi=arm64-v8a (gradle.properties or -P)
//            for fast local signed builds, "all" or blank restores the full set.
androidComponents {
    onVariants { variant ->
        val prop = if (variant.buildType == "release") "releaseAbi" else "debugAbi"
        val only = (project.findProperty(prop) as String?)
            ?.takeUnless { it.isBlank() || it == "all" }
            ?: ("arm64-v8a".takeIf { variant.buildType != "release" })
            ?: return@onVariants
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            output.enabled.set(abi == only)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.material.color.utilities)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)

    implementation(libs.serialization.json)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.play.services.ads)
    implementation(libs.mlkit.barcode)
    implementation(libs.zxing.cpp)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.onnxruntime.android)

    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)

    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    testImplementation(libs.junit)
    // android.jar's org.json is a throwing stub; the real one lets us unit-test JSON export.
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.litertlm.android)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}
