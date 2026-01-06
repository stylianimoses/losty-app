plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    // Apply the compose compiler plugin directly to resolve the build error
}

android {
    namespace = "com.fyp.losty"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fyp.losty"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xopt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Add Compose Material (legacy) for PullRefresh APIs
    implementation("androidx.compose.material:material:1.5.0")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.androidx.glance.appwidget)

    // Add Android Material Components so legacy styles/attributes resolve (e.g., Theme.MaterialComponents, colorControlNormal)
    implementation("com.google.android.material:material:1.9.0")

    // Google Play services auth for Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    // App Check debug provider for local development
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Add the dependencies for the Firebase products (use catalog aliases)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.functions.ktx)
    // firebase-auth is provided via the BoM (use firebase-auth-ktx via platform)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.google.accompanist:accompanist-swiperefresh:0.30.1")
    // Use correct AndroidX SwipeRefreshLayout artifact and stable version
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}