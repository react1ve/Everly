plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firebase config without the google-services plugin: if androidApp/google-services.json exists,
 * generate the same string resources the plugin would (Firebase auto-initialises from them).
 * No file = the app still builds; cloud sync and Firebase Analytics are simply off.
 */
val appId = "com.example.recurring" // placeholder on purpose: Play rejects com.example.*, /replica-brand sets the real id
val firebaseResources: Map<String, String> = file("google-services.json").takeIf { it.exists() }?.let { f ->
    @Suppress("UNCHECKED_CAST")
    val root = groovy.json.JsonSlurper().parse(f) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    val clients = root["client"] as List<Map<String, Any?>>
    val client = clients.firstOrNull {
        ((it["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>)["package_name"] == appId
    } ?: error("google-services.json has no Android app with package $appId — add it in the Firebase console")
    val clientInfo = client["client_info"] as Map<String, Any?>
    val apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String
    val webClientId = (client["oauth_client"] as? List<Map<String, Any?>>).orEmpty()
        .firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }?.get("client_id") as? String
    buildMap {
        put("google_app_id", clientInfo["mobilesdk_app_id"] as String)
        put("google_api_key", apiKey)
        put("google_crash_reporting_api_key", apiKey)
        put("gcm_defaultSenderId", project["project_number"].toString())
        put("project_id", project["project_id"] as String)
        (project["storage_bucket"] as? String)?.let { put("google_storage_bucket", it) }
        webClientId?.let { put("default_web_client_id", it) }
    }
}.orEmpty()


android {
    namespace = "app.android"
    compileSdk = 37

    defaultConfig {
        // Placeholder on purpose: Play rejects com.example.*, so /replica-brand has to set the real id.
        applicationId = appId
        firebaseResources.forEach { (name, value) -> resValue("string", name, value) }
        buildConfigField("boolean", "FIREBASE_CONFIGURED", (firebaseResources["google_app_id"] != null).toString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${firebaseResources["default_web_client_id"].orEmpty()}\"")
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing config is added in /replica-deploy (keystore stays out of git).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            optIn.add("kotlin.time.ExperimentalTime")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase: Auth (Google + Apple), Firestore (sync), Analytics (opt-in)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.kotlinx.coroutines.play.services)
    // Google sign-in through Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
}
