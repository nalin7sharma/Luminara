plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.luminara.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.luminara.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // The deployed backend. Override without editing this file:
    //   ./gradlew assembleRelease -PluminaraApiBase=https://your-backend
    // This is a public URL, not a secret — no key ever reaches the APK.
    val prodApiBase = (project.findProperty("luminaraApiBase") as String?)
        ?: "https://REPLACE-WITH-DEPLOYED-BACKEND"

    signingConfigs {
        create("release") {
            // Supplied via ~/.gradle/gradle.properties or -P flags, never committed.
            val storePath = project.findProperty("luminaraKeystore") as String?
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = project.findProperty("luminaraKeystorePassword") as String?
                keyAlias = project.findProperty("luminaraKeyAlias") as String?
                keyPassword = project.findProperty("luminaraKeyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"$prodApiBase\"")
            // Fall back to debug signing so a release build is still installable
            // when no keystore has been configured yet.
            signingConfig = if ((project.findProperty("luminaraKeystore") as String?)
                    ?.let { file(it).exists() } == true
            ) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            // Local development: the emulator host. The app also probes
            // 127.0.0.1 for `adb reverse`, but only in debug builds.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
