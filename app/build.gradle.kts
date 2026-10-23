plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jobmate.app"
    compileSdk = 34

    // 正式签名配置：根目录存在 lulu-release.keystore 且注入了密码 (CI Secret) 时启用，
    // 否则保持 unsigned，不影响本地无密钥环境的日常构建
    val releaseKeystoreFile = rootProject.file("lulu-release.keystore")
    val releaseKeystorePassword: String = System.getenv("KEYSTORE_PASSWORD") ?: ""

    signingConfigs {
        create("release") {
            if (releaseKeystoreFile.exists() && releaseKeystorePassword.isNotEmpty()) {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = System.getenv("KEY_ALIAS") ?: "lulu"
                keyPassword = releaseKeystorePassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.jobmate.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystoreFile.exists() && releaseKeystorePassword.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
    }
    testOptions {
        // 纯 JVM 单测中让 android.util.Log 等桩方法返回默认值而非抛异常，
        // 使 StateMachine 等含日志调用的纯逻辑类可直接测试
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX & UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 协程 (异步调度)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DeepSeek API 客户端 (Retrofit + OkHttp + Gson)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room 本地数据库
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // 加密存储 (存放 DeepSeek API Key 与简历)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 单元测试
    testImplementation("junit:junit:4.13.2")
}
