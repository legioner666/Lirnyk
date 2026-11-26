plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android { namespace = "app.lirnyk.core.ui" }

dependencies {
    api(project(":core:media"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
