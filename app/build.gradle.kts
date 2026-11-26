plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// CI sets the run number and commit; local builds are "local".
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val gitCommit = System.getenv("GITHUB_SHA")?.take(7) ?: "local"

android {
    namespace = "app.lirnyk"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.lirnyk"
        minSdk = 23
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        // Ukrainian, English, Polish only: drops other languages from libraries.
        resourceConfigurations += listOf("en", "uk", "pl")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Minified, size-checked test build. Signed with the debug key until the owner creates a release key
            // (never published to Google Play without the owner's decision).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
    }

    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/**/LICENSE*", "kotlin/**", "DebugProbesKt.bin")
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:media"))
    implementation(project(":core:ui"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:diagnostics"))
}
