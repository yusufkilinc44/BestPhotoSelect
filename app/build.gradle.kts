plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bestphotoselect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bestphotoselect"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }

    // Lite sürümle (releases/BestPhotoSelect-v1.0.0.apk) aynı anahtar:
    // v2, telefonda v1 üzerine güncelleme olarak kurulabilir.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("applite/keystore/bestphoto.keystore")
            storePassword = "bestphoto"
            keyAlias = "bestphotoselect"
            keyPassword = "bestphoto"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Java 8 hedefi: derleme ortamında core-for-system-modules.jar bulunmadığından
    // JdkImageTransform'a ihtiyaç duymayan tek yapılandırma budur.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Testler saf JVM'dir; Android sınıflarına dokunan yardımcılar
            // (ör. PhotoItem.uri) çağrılmadığı sürece stub'lar sorun çıkarmaz.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.coil.compose)

    implementation(libs.mlkit.face.detection)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
