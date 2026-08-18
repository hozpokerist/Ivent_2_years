import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
  id("io.github.takahirom.roborazzi")
  id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
  id("com.google.gms.google-services")
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.autoclicker.pxfvd"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      val keystore = file("${rootDir}/debug.keystore")
      if (keystore.exists()) {
        storeFile = keystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      if (file("${rootDir}/debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  @Suppress("DEPRECATION")
  kotlinOptions {
    jvmTarget = "21"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  sourceSets {
    getByName("main") {
      res.srcDirs("src/main/res")
    }
  }

  androidResources {
    noCompress += listOf("png", "webp")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
  implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
  implementation(platform("androidx.compose:compose-bom:2024.09.00"))
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.room:room-ktx:2.7.0")
  implementation("androidx.room:room-runtime:2.7.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.12.0")
  implementation("com.google.firebase:firebase-ai")
  implementation("com.google.firebase:firebase-appcheck-recaptcha")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
  implementation("com.squareup.okhttp3:okhttp:4.10.0")
  implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
  implementation("com.squareup.retrofit2:retrofit:2.12.0")

  testImplementation("androidx.compose.ui:ui-test-junit4")
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("androidx.test.ext:junit:1.3.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.59.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.59.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.59.0")

  androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.6.2")

  debugImplementation("androidx.compose.ui:ui-test-manifest")
  debugImplementation("androidx.compose.ui:ui-tooling")

  "ksp"("androidx.room:room-compiler:2.7.0")
  "ksp"("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")
}
