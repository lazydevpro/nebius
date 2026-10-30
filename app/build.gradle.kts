import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
// Keys stay out of git: copy secrets.properties.example to secrets.properties and fill it in.
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// The public test build holds no Nebius key: AI goes through the gateway with its own revocable token.
val publicBuild = providers.gradleProperty("maaPublicBuild").isPresent
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
        buildConfigField("String", "NEBIUS_API_KEY", "\"${if (publicBuild) "" else secrets.getProperty("NEBIUS_API_KEY", "")}\"")
        buildConfigField("String", "MAA_DEVICE_TOKEN", "\"${secrets.getProperty(if (publicBuild) "MAA_PUBLIC_TOKEN" else "MAA_DEVICE_TOKEN", "")}\"")
        // Keyed builds call Nebius directly. Changing their default would stop matching the endpoint saved on existing installs.
        buildConfigField("String", "MAA_GATEWAY_URL", "\"${if (publicBuild) secrets.getProperty("MAA_GATEWAY_URL", "") else "https://maa-intent-gateway.five-magazine.workers.dev"}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
