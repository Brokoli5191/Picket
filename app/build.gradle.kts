import com.android.build.api.artifact.SingleArtifact
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

// ---- Release contract (see .github/workflows/release.yml) ---------------------------------------
// versionName/versionCode come from -PappVersionName / -PappVersionCode; local builds fall back.
val appVersionName: String = providers.gradleProperty("appVersionName").getOrElse("0.0.0-dev")
val appVersionCode: Int = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)

// Release signing only from the environment. Missing values fail the release build loudly.
val signingEnvNames =
    listOf("SIGNING_KEYSTORE_PATH", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD")
val signingEnv: Map<String, String?> =
    signingEnvNames.associateWith { name -> providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() } }
val missingSigningEnv: List<String> = signingEnv.filterValues { it == null }.keys.toList()

val sdkLevels = libs.versions

android {
    namespace = "app.brokoli5191.picket"
    compileSdk = sdkLevels.compileSdk.get().toInt()
    compileSdkMinor = sdkLevels.compileSdkMinor.get().toInt()

    defaultConfig {
        applicationId = "app.brokoli5191.picket"
        minSdk = sdkLevels.minSdk.get().toInt()
        targetSdk = sdkLevels.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (missingSigningEnv.isEmpty()) {
                storeFile = file(signingEnv.getValue("SIGNING_KEYSTORE_PATH")!!)
                storePassword = signingEnv.getValue("SIGNING_STORE_PASSWORD")
                keyAlias = signingEnv.getValue("SIGNING_KEY_ALIAS")
                keyPassword = signingEnv.getValue("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // v1 ships English only; drop library translations so the UI is consistent.
        localeFilters += "en"
    }

    // No dependency metadata blob in APK/AAB (it is only readable by Google Play).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin")
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    // Reference images double as README screenshots.
    outputDir.set(rootProject.layout.projectDirectory.dir("docs/screenshots"))
}

dependencies {
    implementation(project(":core"))
    implementation(project(":privileged"))

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material.color.utilities)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// ---- Release signing guard ----------------------------------------------------------------------
val verifyReleaseSigning =
    tasks.register("verifyReleaseSigning") {
        group = "verification"
        description = "Fails when release signing environment variables are missing."
        val missing = missingSigningEnv
        doLast {
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Release signing is not configured. Missing environment variables: " +
                        missing.joinToString() + ". Picket never produces unsigned release APKs.",
                )
            }
        }
    }
tasks.matching { it.name == "packageRelease" || it.name == "validateSigningRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

// ---- "No INTERNET permission" guard ---------------------------------------------------------------
abstract class VerifyNoInternetPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val offending =
            listOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")
                .flatMap { tag ->
                    val nodes = document.getElementsByTagName(tag)
                    (0 until nodes.length).map { nodes.item(it) as Element }
                }.map { it.getAttributeNS(androidNs, "name") }
                .filter { it == "android.permission.INTERNET" }
        if (offending.isNotEmpty()) {
            throw GradleException(
                "Merged manifest ${manifest.path} declares android.permission.INTERNET. " +
                    "Picket must stay provably offline; find the dependency that adds it and remove it.",
            )
        }
        reportFile.get().asFile.writeText("OK: ${manifest.name} declares no android.permission.INTERNET\n")
    }
}

androidComponents {
    onVariants { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val verify =
            tasks.register<VerifyNoInternetPermission>("verify${capitalized}NoInternetPermission") {
                group = "verification"
                description = "Fails if the merged ${variant.name} manifest requests INTERNET."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                reportFile.set(layout.buildDirectory.file("reports/no-internet/${variant.name}.txt"))
            }
        tasks.matching { it.name == "assemble$capitalized" }.configureEach { dependsOn(verify) }
    }
}

val verifyNoInternetPermission =
    tasks.register("verifyNoInternetPermission") {
        group = "verification"
        description = "Fails if any merged app manifest requests android.permission.INTERNET."
        dependsOn(tasks.withType<VerifyNoInternetPermission>())
    }
tasks.named("check") { dependsOn(verifyNoInternetPermission) }
