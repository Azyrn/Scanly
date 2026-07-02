# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers and hide the original source file name so crash stack
# traces stay meaningful after obfuscation (retrace-able via mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# ML Kit (text recognition, barcode scanning, document scanner)
#
# ML Kit registers its internal components through
# `MlKitComponentDiscoveryService`, whose ComponentRegistrar implementations
# are referenced ONLY as string values in the merged AndroidManifest
# meta-data. R8 full mode (android.enableR8.fullMode=true) cannot see those
# string references, strips the registrars, and the component container then
# returns null -- causing a NullPointerException inside
# TextRecognition.getClient(...) the first time an ML Kit client is built.
# Keep the registrars and ML Kit internals so on-device vision works in
# release builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { *; }
-keep class com.google.mlkit.vision.barcode.internal.BarcodeRegistrar { *; }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { *; }
-keep class com.google.mlkit.vision.text.internal.TextRegistrar { *; }
-dontwarn com.google.mlkit.**

# ---------------------------------------------------------------------------
# kotlinx.serialization (Retrofit APIs: Gemini / OpenAI-compat / Claude, plus
# the product-lookup engines). Under R8 full mode the generated $$serializer
# classes and @Serializable members can be stripped/renamed, causing runtime
# SerializationExceptions when an AI request or product lookup runs.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
# Keep enum values referenced by generated serializers (enum serialization
# uses valueOf/values reflectively in some code paths).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}