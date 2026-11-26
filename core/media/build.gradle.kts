plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.lirnyk.core.media"
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
