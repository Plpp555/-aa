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
        // 公開版 (GitHub Releases) はタグの版番号 (v1.2.3 → 1.2.3、versionCode 10203)。
        // それ以外の CI ビルドは実行番号を使い、ビルドのたびに新しい版として上書き更新できるようにする (ローカルビルドは 1)
        val releaseVersion = System.getenv("RELEASE_VERSION")?.takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) }
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        if (releaseVersion != null) {
            val (major, minor, patch) = releaseVersion.split(".").map { it.toInt() }
            versionCode = major * 10000 + minor * 100 + patch
            versionName = releaseVersion
        } else {
            versionCode = runNumber ?: 1
            versionName = "1.0." + (runNumber ?: 0)
        }
    }

    signingConfigs {
        // 開発用: debug (と、公開用の鍵がないときの release) をリポジトリに置いた同じ鍵で署名する。
        // CI の既定の debug 鍵は実行ごとに作り直されるため、署名が毎回変わって上書き更新できなかった。
        // この鍵は公開されているので、公開版の署名には使わない (下の "release")。
        create("shared") {
            storeFile = file("signing/liquid-launcher.p12")
            storeType = "pkcs12"
            storePassword = "liquidlauncher"
            keyAlias = "liquid"
            keyPassword = "liquidlauncher"
        }
        // 公開版 (GitHub Releases): 非公開の鍵。リリース用ワークフローが GitHub Secrets から環境変数で渡す
        val releaseStore = System.getenv("RELEASE_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storeType = "pkcs12"
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("shared")
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
