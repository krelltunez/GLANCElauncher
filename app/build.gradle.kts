import com.android.build.api.variant.BuildConfigField
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The build time and commit for the Settings footer. Value sources, evaluated when BuildConfig
// is generated rather than when the build is configured: the configuration cache is on, and a
// value read at configuration would be replayed from the cache on the next build.
abstract class BuildTimeSource : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = System.currentTimeMillis()
}
abstract class GitShaSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject abstract val exec: ExecOperations
    override fun obtain(): String {
        val out = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = out
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) out.toString().trim() else "unknown"
    }
}

// The release key, from a git-ignored keystore.properties at the repo root or from the environment
// (docs/building.md). A clone or CI without it still configures, unit-tests and builds
// debug; only packaging a release refuses, at the end of this file.
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(StringReader(it)) }
}
fun keystoreValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: providers.environmentVariable(env).orNull
val keystoreFile = keystoreValue("storeFile", "GLANCE_KEYSTORE_FILE")
val keystorePassword = keystoreValue("storePassword", "GLANCE_KEYSTORE_PASSWORD")
val releaseKeyAlias = keystoreValue("keyAlias", "GLANCE_KEY_ALIAS")
val releaseKeyPassword = keystoreValue("keyPassword", "GLANCE_KEY_PASSWORD")
val hasReleaseKey = listOf(keystoreFile, keystorePassword, releaseKeyAlias, releaseKeyPassword).none { it.isNullOrBlank() }

android {
    namespace = "app.glance.launcher"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.glance.launcher"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // ./build-release.sh asks for the versionCode and passes -PversionCode=N, so an upload
        // needs no commit. The literal is the fallback for builds without it (dev builds), kept at
        // the last code uploaded to Play (3), so a fresh checkout's script suggests the next one.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 3
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Where the key is present, dev builds carry it too: they install over the release
            // build, and the apps, which trust only the release key, keep answering them.
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
        release {
            // Thin shell over Compose; nothing to gain from shrinking yet, and the
            // reflection-free code keeps it simple.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The notices the app shows in Settings (OpenSourceNotices) are these files.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("licenses"))

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

androidComponents {
    onVariants { variant ->
        val time = providers.of(BuildTimeSource::class) {}
        val sha = providers.of(GitShaSource::class) {}
        variant.buildConfigFields?.put("BUILD_TIME", time.map { BuildConfigField("long", "${it}L", "When this APK was built, epoch ms") })
        variant.buildConfigFields?.put("GIT_SHA", sha.map { BuildConfigField("String", "\"$it\"", "The commit it was built from") })
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":daydial"))

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    // The real org.json for JVM unit tests: android.jar's copy is a stub that throws.
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
}

// Never an unsigned or debug-signed release: this key is the launcher's identity, and both apps
// admit the launcher by it. At packaging, not configuration, so `:app:test` still runs without it.
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    val missingKey = !hasReleaseKey
    doFirst {
        if (missingKey) {
            throw GradleException(
                "No release key. Create a git-ignored keystore.properties at the repo root with storeFile, " +
                    "storePassword, keyAlias and keyPassword (or set GLANCE_KEYSTORE_FILE, " +
                    "GLANCE_KEYSTORE_PASSWORD, GLANCE_KEY_ALIAS and GLANCE_KEY_PASSWORD). See docs/building.md.",
            )
        }
    }
}
