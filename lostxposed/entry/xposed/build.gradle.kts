plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.lostxposed.entry.xposed"
    compileSdk = 36
    defaultConfig { minSdk = 33 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    api(project(":core:engine"))
    api(project(":core:safety"))
    implementation(project(":features:noop"))
    implementation(project(":features:displayprofiles"))
    implementation(project(":features:textengine"))
    implementation(project(":features:powerinspector"))
    implementation(project(":features:notificationrules"))
    implementation(project(":features:hardwarekeys"))
    implementation(project(":features:smartstatusbar"))
    compileOnly(libs.libxposed.api)
}


