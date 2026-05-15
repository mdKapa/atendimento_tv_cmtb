plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.cmtb.atendimentotv"
    compileSdk = 34

    defaultConfig {
        applicationId = "pt.cmtb.atendimentotv"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Permite usar ViewBinding — o equivalente aos widgets Flutter
    // Em vez de findViewById() repetitivo, tens acesso direto: binding.tvHora
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // === MEDIA3 / EXOPLAYER ===
    // 1.3.1 é a última versão compatível com compileSdk 34 + AGP 8.5.0
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // === RETROFIT + OKHTTP ===
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // === GLIDE ===
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // === VIEWMODEL + COROUTINES ===
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // viewModels() delegate — obrigatório para usar "by viewModels()" na Activity
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // === UI BASE — versões compatíveis com SDK 34 ===
    implementation("androidx.core:core-ktx:1.13.1")        // 1.13.x = SDK 34
    implementation("androidx.appcompat:appcompat:1.6.1")    // 1.6.x = SDK 34
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Dependências de Testes Unitários
    testImplementation("junit:junit:4.13.2")

    // Dependências de Testes Instrumentados (Emulador/Dispositivo)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}