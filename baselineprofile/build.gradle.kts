plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

val sdkLevels = libs.versions
val managedDevice = "pixel6Api34"

android {
    namespace = "app.brokoli5191.picket.baselineprofile"
    compileSdk = sdkLevels.compileSdk.get().toInt()
    compileSdkMinor = sdkLevels.compileSdkMinor.get().toInt()

    defaultConfig {
        // Baseline profiles can be collected without root from API 33.
        minSdk = 33
        targetSdk = sdkLevels.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        create(managedDevice) {
            device = "Pixel 6"
            apiLevel = 34
            // "aosp" rather than "aosp-atd": profile collection needs the full image.
            systemImageSource = "aosp"
        }
    }
}

// Generated on the managed emulator by .github/workflows/baseline-profile.yml. On a real device
// (for example one with Shizuku running, which also covers the process list):
// ./gradlew :app:generateBaselineProfile -Ppicket.profileOnDevice=true
val onDevice = providers.gradleProperty("picket.profileOnDevice").map(String::toBoolean).getOrElse(false)

baselineProfile {
    useConnectedDevices = onDevice
    if (!onDevice) managedDevices += managedDevice
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// The tests find the app by the id of the APK they run against (it differs per build type).
androidComponents {
    onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { checkNotNull(loader.load(it)).applicationId },
        )
    }
}
