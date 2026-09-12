import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.buswatch.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Inject OneSignal REST API Key from local.properties
        // If not found, it defaults to an empty string to avoid accidental exposure
        buildConfigField("String", "ONESIGNAL_REST_API_KEY", "\"${localProperties.getProperty("onesignal.rest.api.key") ?: ""}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Networking for OneSignal REST API
    implementation(libs.okhttp)

    // OneSignal SDK for user identification (api to share with :driver and :app)
    api(libs.onesignal)

    // Map Utilities Dependencies exposed to all modules
    api(libs.osmdroid.android)
    api(libs.osmbonuspack)
    api(libs.maplibre)
}
