plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.cultureguide.kids"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.cultureguide.kids"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.5.1"
    }
    signingConfigs {
        // Постоянный debug-ключ из репозитория: каждая сборка CI ставится поверх предыдущей без удаления
        // приложения. Ключ не секретный — только для debug.
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildFeatures { compose = true }
    androidResources { noCompress += "ogg" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    // Вариант на OpenGL ES: основной артефакт требует Vulkan и не работает на части телефонов.
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.compose.ui:ui:1.8.3")
    implementation("androidx.compose.foundation:foundation:1.8.3")
    implementation("androidx.compose.runtime:runtime:1.8.3")
    implementation("androidx.compose.material3:material3:1.3.2")

    testImplementation("junit:junit:4.13.2")
}
