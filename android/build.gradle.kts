// Top-level build file. Plugin versions declared here; applied in :app.
// NOTE: SDK on this machine is very new (API 36/37). If Gradle sync reports an
// AGP/Gradle/Kotlin mismatch, accept Android Studio's Upgrade Assistant suggestion —
// only these version numbers change, not the code.
plugins {
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}
