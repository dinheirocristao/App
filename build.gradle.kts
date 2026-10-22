// Versões escolhidas por compatibilidade comprovada entre si:
// AGP 8.10.1 (requer Gradle 8.11.1+ e JDK 17; wrapper usa Gradle 8.14.3) · Kotlin 2.0.21 · compileSdk/targetSdk 35
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
