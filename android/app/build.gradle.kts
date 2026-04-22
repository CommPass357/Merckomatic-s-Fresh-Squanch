import org.gradle.api.GradleException
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties()
if (signingPropertiesFile.exists()) {
    signingPropertiesFile.inputStream().use(signingProperties::load)
}

fun signingProperty(name: String): String? =
    signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProperty("storeFile")
val releaseKeyAlias = signingProperty("keyAlias")
val releaseStorePassword = signingProperty("storePassword")
val releaseKeyPassword = signingProperty("keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseKeyAlias,
    releaseStorePassword,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

fun String.buildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val githubOwner = providers.gradleProperty("cfs.githubOwner").orElse("CommPass357").get()
val githubRepo = providers.gradleProperty("cfs.githubRepo").orElse("Merckomatic-s-Fresh-Squanch").get()

android {
    namespace = "com.commsfreshsquanch.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.commsfreshsquanch.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["spotifyRedirectScheme"] = "commsfreshsquanch"
        buildConfigField("String", "GITHUB_OWNER", githubOwner.buildConfigString())
        buildConfigField("String", "GITHUB_REPO", githubRepo.buildConfigString())
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                keyAlias = releaseKeyAlias
                storePassword = releaseStorePassword
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

gradle.taskGraph.whenReady {
    val releaseTaskRequested = allTasks.any { task ->
        task.path.endsWith(":app:assembleRelease") ||
            task.path.endsWith(":app:packageRelease") ||
            task.path.endsWith(":app:bundleRelease")
    }
    if (releaseTaskRequested && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not configured. Create android/keystore.properties from " +
                "android/keystore.properties.example before running :app:assembleRelease."
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
