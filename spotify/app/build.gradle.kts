plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// TASK 003 — private Spotify App Remote prototype. Installs next to Lirnyk (different applicationId).
// The App Remote SDK (Apache-2.0, github.com/spotify/android-sdk) is fetched by scripts/fetch-spotify-sdk.sh, checksum-pinned.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val gitCommit = System.getenv("GITHUB_SHA")?.take(7) ?: "local"
val testKeystore = System.getenv("LIRNYK_TEST_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
val sdkAar = file("libs/spotify-app-remote-release-0.8.0.aar")

android {
    namespace = "app.lirnyk.remote"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.lirnyk.remote"
        minSdk = 23
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.3.$buildNumber"
        resourceConfigurations += listOf("en", "uk", "pl")
        // Spotify dashboard app "Лірник Дистанційне" (development mode). The client id is public; there is no secret here.
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"c3f21482d91c430d95342f864e41e6ef\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"app.lirnyk.remote://callback\"")
        // SHA-1 of the test key as registered in Spotify (GitHub variable LIRNYK_TEST_CERT_SHA1), compared at runtime.
        buildConfigField("String", "EXPECTED_CERT_SHA1", "\"${System.getenv("LIRNYK_TEST_CERT_SHA1") ?: ""}\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (testKeystore != null) {
            create("test") {
                storeFile = testKeystore
                storeType = "PKCS12"
                storePassword = System.getenv("LIRNYK_TEST_KEYSTORE_PASSWORD")
                keyAlias = "lirnyk-test"
                keyPassword = System.getenv("LIRNYK_TEST_KEYSTORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Not minified: the SDK maps its protocol types by reflection, and size does not matter for a private prototype.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("test") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":spotify:model"))
    if (sdkAar.exists()) implementation(files(sdkAar)) else throw GradleException("Spotify App Remote SDK missing: run scripts/fetch-spotify-sdk.sh")
    implementation(libs.gson)   // required by the App Remote SDK
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
