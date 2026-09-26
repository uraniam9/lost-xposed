import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/*
 * Release signing. The keystore and its passwords live in keystore.properties, which is
 * gitignored and never committed -- see README.
 *
 * When that file is absent the release build still succeeds and produces an UNSIGNED apk,
 * because a contributor with no key should still be able to check that R8 has not broken the
 * build. It says so rather than leaving someone to discover it at install time.
 */
val signing = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

/*
 * One version, two places that show it. The framework manager reads module.prop, the
 * launcher and the update check read the apk, and a hand-edited module.prop had already
 * drifted a whole release behind by the time anybody looked -- LSPosed said 0.1.0 while
 * the app said 0.2.0-alpha. So module.prop is generated below rather than committed.
 */
val moduleVersionCode = 4
val moduleVersionName = "0.2.2-beta"

/*
 * module.prop is what the framework manager lists this as: the name in the module list, the
 * version beside it, and the api range it refuses to load outside of.
 *
 * `id` stays dev.lostxposed even though the applicationId is now io.github.uraniam9.lostxposed.
 * That pairing is the one that has actually been seen loading and holding its scope on a
 * device, and some managers key their own storage on the id, so changing it is a change that
 * needs a phone in hand rather than one made on the way to a release.
 */
abstract class GenerateModuleProp : DefaultTask() {

    @get:Input
    abstract val moduleVersion: Property<String>

    @get:Input
    abstract val moduleCode: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.get().asFile.resolve("META-INF/xposed/module.prop")
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("id=dev.lostxposed")
                appendLine("name=Lost Xposed")
                appendLine("version=" + moduleVersion.get())
                appendLine("versionCode=" + moduleCode.get())
                appendLine("author=uraniam9")
                appendLine("description=The settings Android keeps to itself.")
                appendLine("minApiVersion=100")
                appendLine("targetApiVersion=102")
            },
        )
    }
}

val moduleProp = tasks.register<GenerateModuleProp>("generateXposedModuleProp") {
    moduleVersion.set(moduleVersionName)
    moduleCode.set(moduleVersionCode)
}

android {
    namespace = "dev.lostxposed"
    compileSdk = 36

    defaultConfig {
        // Reverse-DNS of a namespace that is actually controlled: github.com/uraniam9.
        // dev.lostxposed claimed a domain nobody owns, and com.soundsoftlab would put
        // this under the label SonoLune ships as, when it is published as uraniam9.
        // libxposed itself uses io.github.libxposed, so the convention is established.
        //
        // Last chance to get this right: an applicationId cannot change after release.
        applicationId = "io.github.uraniam9.lostxposed"
        minSdk = 33
        targetSdk = 36
        versionCode = moduleVersionCode
        // Kept in step with Release.STAGE, which drives the badge in the UI.
        versionName = moduleVersionName
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: logger.warn(
                    "No keystore.properties: :app:assembleRelease will produce an UNSIGNED apk " +
                        "that cannot be installed. See README.",
                ).let { null }

            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(
            moduleProp,
            GenerateModuleProp::outputDir,
        )
    }
}

dependencies {
    implementation(project(":entry:xposed"))
    // The UI process writes config and needs the feature's key names.
    implementation(project(":core:config"))
    implementation(project(":features:displayprofiles"))
    // For the live clock preview: the settings screen renders with exactly the same
    // code the hook does, so a preview cannot be a polite fiction.
    implementation(project(":features:smartstatusbar"))
    // For the feature id only, so its settings screen can special-case the live report.
    implementation(project(":features:powerinspector"))
    compileOnly(libs.libxposed.api)
}
