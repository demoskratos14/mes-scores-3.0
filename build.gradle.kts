plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    // Depuis Kotlin 2.0, le compilateur Compose est un plugin Kotlin (plus de kotlinCompilerExtensionVersion).
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
