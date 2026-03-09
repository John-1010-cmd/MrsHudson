plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

// 应用 Google Services 插件
apply(plugin = "com.google.gms.google-services")

// 读取 keystore 配置 (PKCS12 格式)
val keystoreFile = rootProject.file("release.keystore")
val keystorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String ?: "android"
val keystoreKeyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String ?: "mrshudson"
val keystoreKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String ?: "android"

android {
    namespace = "com.mrshudson.android"
    compileSdk = 34

    // 多架构支持配置
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    defaultConfig {
        applicationId = "com.mrshudson.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 支持所有架构的 NDK
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // 签名配置 (PKCS12 格式)
    signingConfigs {
        create("release") {
            // 如果 keystore 文件存在，使用它进行签名
            if (keystoreFile.exists() && keystorePassword.isNotEmpty()) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreKeyAlias
                // PKCS12 格式使用相同的密码
                keyPassword = if (keystoreKeyPassword.isEmpty()) keystorePassword else keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 使用 release 签名配置
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Networking
    implementation(libs.bundles.retrofit)

    // Room
    implementation(libs.bundles.room)
    kapt(libs.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // WorkManager
    implementation(libs.workmanager.runtime)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
