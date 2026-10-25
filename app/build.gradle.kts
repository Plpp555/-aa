import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aa.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aa.launcher"
        minSdk = 23
        targetSdk = 36
        // CI では実行番号を使い、ビルドのたびに新しい版として上書き更新できるようにする (ローカルビルドは 1)
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = runNumber ?: 1
        versionName = "1.0." + (runNumber ?: 0)
    }

    signingConfigs {
        // debug / release とも、リポジトリに置いた同じ鍵で署名する。
        // CI の既定の debug 鍵は実行ごとに作り直されるため、署名が毎回変わって上書き更新できなかった。
        // 鍵は公開リポジトリにあるので誰でも同じ署名を作れる。ストア配布時は非公開の鍵に置き換えること。
        create("shared") {
            storeFile = file("signing/liquid-launcher.p12")
            storeType = "pkcs12"
            storePassword = "liquidlauncher"
            keyAlias = "liquid"
            keyPassword = "liquidlauncher"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("shared")
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

    lint {
        // Android 6.0 (API 23) 対応の確認: minSdk より新しい API を分岐なしで呼んでいたらエラーにする
        checkOnly += "NewApi"
        abortOnError = true
        textReport = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
