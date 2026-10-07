plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Depuis Kotlin 2.0, le compilateur Compose est un plugin Kotlin (plus de kotlinCompilerExtensionVersion).
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
