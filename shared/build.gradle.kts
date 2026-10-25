plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "app.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            }
        }
        commonMain.dependencies {
            api(project(":core"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose.kmp)
            implementation(libs.lifecycle.runtime.compose.kmp)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
