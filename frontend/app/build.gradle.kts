import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    //id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10"
}

// Firebase reads its project config from app/google-services.json (downloaded from
// the Firebase console). The plugin is only applied when the file exists so the app
// still builds without it; push notification registration is skipped in that case.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Machine-specific config (SDK path, backend URL, OAuth client ID) lives in
// local.properties, which is gitignored. See local.properties.example.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.reader().use { localProperties.load(it) }
}

fun localProperty(name: String, default: String = ""): String =
    localProperties.getProperty(name)?.trim()?.removeSurrounding("\"") ?: default

android {
    namespace = "com.example.cpen321application"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.cpen321application"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Baked in at build time from local.properties — never hard-code URLs or
        // OAuth IDs in source. Defaults point at the Caddy HTTPS/WSS proxy on EC2;
        // override in local.properties (e.g. http://10.0.2.2:3000) for local testing.
        buildConfigField("String", "API_BASE_URL",
            "\"${localProperty("API_BASE_URL")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID",
            "\"${localProperty("GOOGLE_CLIENT_ID")}\"")
        buildConfigField("String", "API_WEB_SOCKET",
            "\"${localProperty("API_WEB_SOCKET")}\"")

        // Google Maps / Places key: used by the map (AndroidManifest) and the place search
        val mapsApiKey = localProperty("MAPS_API_KEY")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Pins the JDK used to compile Kotlin and Java so the build does not depend on
// whichever JDK happens to be on the developer's PATH. Gradle downloads this
// JDK if it is missing (see the foojay resolver in settings.gradle.kts).
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    //google authorization dependencies
    implementation("androidx.credentials:credentials:1.7.0-alpha03")
    implementation("androidx.credentials:credentials-play-services-auth:1.7.0-alpha03")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    //retrofit and its converters dependencies
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    //navigation dependency and okhttp for websocket
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    //firebase cloud messaging for push notifications
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    //google maps and places for picking a game's location
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.places)
}