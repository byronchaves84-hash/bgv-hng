plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.moorty.voice"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.moorty.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-android-port"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Community connector for public TikTok LIVE rooms (unofficial API).
    implementation("com.github.jwdeveloper.TikTok-Live-Java:Client:1.13.0-Release")
}
