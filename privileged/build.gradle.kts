plugins {
    alias(libs.plugins.android.library)
}

val sdkLevels = libs.versions

android {
    namespace = "app.brokoli5191.picket.privileged"
    compileSdk = sdkLevels.compileSdk.get().toInt()
    compileSdkMinor = sdkLevels.compileSdkMinor.get().toInt()

    defaultConfig {
        minSdk = sdkLevels.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.shizuku.api)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
