import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.nookipet.r8k2m"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // ---------------------------------------------------------------------------------------------
  // Signing
  //
  // Release: credentials are read, in order, from
  //   1. environment variables KEYSTORE_PATH / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
  //      (this is what AI Studio injects, so its builds keep working unchanged), then
  //   2. a git-ignored `keystore.properties` file in the project root (see
  //      keystore.properties.example), used for local sideload builds.
  // If neither provides a readable keystore, `assembleRelease` falls back to the debug key so you
  // still get an installable APK, and prints a warning. Never ship a debug-signed APK to a store.
  //
  // Debug: uses `debug.keystore` in the project root when present (AI Studio provides one),
  // otherwise AGP's standard debug key (auto-created), so a fresh clone or CI runner builds.
  // ---------------------------------------------------------------------------------------------
  val keystoreProps =
    Properties().apply {
      val f = rootProject.file("keystore.properties")
      if (f.isFile) f.inputStream().use { load(it) }
    }
  fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

  val releaseStorePath = signingValue("KEYSTORE_PATH", "storeFile") ?: "${rootDir}/my-upload-key.jks"
  val releaseStoreFile = rootProject.file(releaseStorePath)
  val releaseStorePassword = signingValue("STORE_PASSWORD", "storePassword")
  val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword")
  val hasReleaseKey = releaseStoreFile.isFile && releaseStorePassword != null && releaseKeyPassword != null

  signingConfigs {
    create("release") {
      storeFile = releaseStoreFile
      storePassword = releaseStorePassword
      keyAlias = signingValue("KEY_ALIAS", "keyAlias") ?: "upload"
      keyPassword = releaseKeyPassword
    }
    // Only defined when the project ships its own debug.keystore (AI Studio). Otherwise the
    // built-in "debug" config is used, which AGP creates at ~/.android/debug.keystore on demand.
    if (rootProject.file("debug.keystore").isFile) {
      create("debugConfig") {
        storeFile = rootProject.file("debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }
  val debugSigning = signingConfigs.findByName("debugConfig") ?: signingConfigs.getByName("debug")

  buildTypes {
    release {
      isCrunchPngs = false
      // R8 shrinking + resource shrinking for a small, optimized sideload APK. Filament/SceneView
      // keep rules live in proguard-rules.pro.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig =
        if (hasReleaseKey) {
          signingConfigs.getByName("release")
        } else {
          logger.warn(
            "Nooki: no release keystore found (env vars or keystore.properties). " +
              "Release APK will be signed with the DEBUG key - fine for sideloading, not for Play."
          )
          debugSigning
        }
    }
    debug {
      signingConfig = debugSigning
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Native ABIs. Filament (the 3D renderer behind SceneView) ships .so files for 4 ABIs, which is
  // most of the APK size. By default you get ONE universal APK (simplest to sideload).
  // Build per-ABI APKs with:  ./gradlew assembleRelease -PsplitAbi
  // and install the arm64-v8a one on any modern phone.
  // ---------------------------------------------------------------------------------------------
  splits {
    abi {
      isEnable = project.hasProperty("splitAbi")
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
      isUniversalApk = true
    }
  }

  // Keep 3D assets uncompressed so Filament can read them straight out of the APK.
  androidResources { noCompress += listOf("glb", "gltf", "ktx", "filamat") }

  lint {
    checkReleaseBuilds = true
    // Report problems (app/build/reports/lint-results-debug.html) without blocking the APK build.
    abortOnError = false
    checkDependencies = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Screenshot tests skip themselves unless asked for: ./gradlew testDebugUnitTest -Pscreenshots
      val wantScreenshots = project.hasProperty("screenshots").toString()
      all {
        it.systemProperty("nooki.screenshots", wantScreenshots)
        it.systemProperty("roborazzi.test.record", "true")
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Room writes each schema version here, so every migration can be checked against what shipped.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // 3D desk-pet rendering (Filament + glTF) and Material shape morphing
  implementation(libs.sceneview)
  implementation(libs.androidx.graphics.shapes)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
