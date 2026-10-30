plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.panel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.panel"
        minSdk = 21
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 21
        versionCode = 6
        versionName = "1.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "36.0.0"
}

dependencies {
    implementation("androidx.activity:activity:1.11.0")
    implementation("org.jspecify:jspecify:1.0.0")
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}