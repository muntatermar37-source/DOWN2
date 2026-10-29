plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vcode = (project.findProperty("vcode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.vdl.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vdl.app"
        minSdk = 29
        targetSdk = 34
        versionCode = vcode
        versionName = "1.0.$vcode"
        buildConfigField("String", "REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: "OWNER/REPO"}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "vdl123456"
            keyAlias = "vdl"
            keyPassword = "vdl123456"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
}
