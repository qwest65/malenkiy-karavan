plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Каталог мест, геолокация, логика прибытия на точку и аудиогид для подробных справок.
android {
    namespace = "ru.cultureguide.core"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.compose.runtime:runtime:1.8.3")

    testImplementation("junit:junit:4.13.2")
}
