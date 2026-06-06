plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.adblock.tg"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.adblock.tg"
        minSdk = 29          // getDefaultClassLoader() requires Android 10+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // API 101 — compileOnly: provided by the LSPosed framework at runtime
    compileOnly("io.github.libxposed:api:101.0.1")
    compileOnly("androidx.annotation:annotation:1.7.1")
}
