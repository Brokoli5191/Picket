plugins {
    alias(libs.plugins.android.application)
}

val sdkLevels = libs.versions

android {
    namespace = "app.brokoli5191.picket.testtarget"
    compileSdk = sdkLevels.compileSdk.get().toInt()
    compileSdkMinor = sdkLevels.compileSdkMinor.get().toInt()

    defaultConfig {
        applicationId = "app.brokoli5191.picket.testtarget"
        minSdk = sdkLevels.minSdk.get().toInt()
        targetSdk = sdkLevels.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1"
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
}
