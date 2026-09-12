import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.buswatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.buswatch"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Security: Define build config fields for sensitive credentials
        // These are pulled from local.properties (not tracked in Git)
        
        // Admin Credentials
        buildConfigField("String", "ADMIN_EMAIL", "\"${localProperties.getProperty("admin.email") ?: ""}\"")
        buildConfigField("String", "ADMIN_PASSWORD", "\"${localProperties.getProperty("admin.password") ?: ""}\"")
        buildConfigField("String", "DRIVER_EMAIL", "\"${localProperties.getProperty("driver.email") ?: ""}\"")
        buildConfigField("String", "DRIVER_PASSWORD", "\"${localProperties.getProperty("driver.password") ?: ""}\"")
        
        // Demo Credentials
        buildConfigField("String", "DEMO_PARENT_EMAIL", "\"${localProperties.getProperty("demo.parent.email") ?: ""}\"")
        buildConfigField("String", "DEMO_PARENT_PASS", "\"${localProperties.getProperty("demo.parent.pass") ?: ""}\"")
        buildConfigField("String", "DEMO_DRIVER_EMAIL", "\"${localProperties.getProperty("demo.driver.email") ?: ""}\"")
        buildConfigField("String", "DEMO_DRIVER_PASS", "\"${localProperties.getProperty("demo.driver.pass") ?: ""}\"")
        buildConfigField("String", "DEMO_CONDUCTOR_EMAIL", "\"${localProperties.getProperty("demo.conductor.email") ?: ""}\"")
        buildConfigField("String", "DEMO_CONDUCTOR_PASS", "\"${localProperties.getProperty("demo.conductor.pass") ?: ""}\"")

        // External Service Credentials
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${localProperties.getProperty("onesignal.app_id") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${localProperties.getProperty("cloudinary.cloud_name") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${localProperties.getProperty("cloudinary.api_key") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${localProperties.getProperty("cloudinary.api_secret") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":driver"))
    implementation(project(":admin"))

    // OneSignal
    implementation(libs.onesignal)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    platform(libs.androidx.compose.bom).let { implementation(it) }
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.location)
    
    // OpenStreetMap
    implementation(libs.osmdroid.android)
    implementation(libs.osmbonuspack)
    implementation(libs.androidx.preference)

    // MapLibre
    implementation(libs.maplibre)

    // Cloudinary
    implementation(libs.cloudinary.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    platform(libs.androidx.compose.bom).let { androidTestImplementation(it) }
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    // Glide for image loading
    implementation(libs.glide)
    annotationProcessor("com.github.bumptech.glide:compiler:${libs.versions.glide.get()}")
}
