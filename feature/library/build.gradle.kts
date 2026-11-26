plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android { namespace = "app.lirnyk.feature.library" }

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
}
