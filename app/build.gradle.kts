import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
// Keys stay out of git: copy secrets.properties.example to secrets.properties and fill it in.
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
android {
    namespace = "in.family.maa"
    compileSdk = 36
    // Ship both languages so the in-app switch also works in bundle installs.
    bundle { language { enableSplit = false } }
    defaultConfig {
        applicationId = "in.family.maa"; minSdk = 26; targetSdk = 35; versionCode = 12; versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "NEBIUS_API_KEY", "\"${secrets.getProperty("NEBIUS_API_KEY", "")}\"")
        buildConfigField("String", "MAA_DEVICE_TOKEN", "\"${secrets.getProperty("MAA_DEVICE_TOKEN", "")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
