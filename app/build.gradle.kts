plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
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
        applicationId = "in.family.maa"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.0-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
