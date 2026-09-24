plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.lostxposed.core.api"
    compileSdk = 36
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    // compileOnly: supplied by the framework at runtime, so it costs nothing in
    // system_server. NOTHING ELSE belongs here — see the module KDoc.
    compileOnly(libs.libxposed.api)
}
