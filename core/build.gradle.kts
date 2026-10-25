// Pure Kotlin domain: models + ScheduleEngine. No Compose, no platform APIs,
// so the iOS widget extension can link it later without pulling in the UI.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "app.core"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm() // fast unit tests: ./gradlew :core:jvmTest
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
