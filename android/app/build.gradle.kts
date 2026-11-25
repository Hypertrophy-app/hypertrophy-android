import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.hypertrophy"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // Release signing credentials live in keystore.properties (gitignored).
    // See keystore.properties.example for the expected keys.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    // The publishable key is safe to ship in the app; the secret key never
    // leaves the Worker. local.properties is gitignored, so the checked-in
    // source keeps no environment-specific Clerk instance.
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(localProperties::load)
    }
    val clerkPublishableKey = localProperties.getProperty("CLERK_PUBLISHABLE_KEY")
        ?: providers.gradleProperty("CLERK_PUBLISHABLE_KEY").orNull
        ?: ""

    defaultConfig {
        applicationId = "app.hypertrophy"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Release builds override these from the git tag through -P flags.
        // See .github/workflows/release.yml.
        versionCode = (providers.gradleProperty("hypertrophy.versionCode").orNull ?: "1").toInt()
        versionName = providers.gradleProperty("hypertrophy.versionName").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLERK_PUBLISHABLE_KEY", "\"$clerkPublishableKey\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MediaPipe opens .task models through AssetManager.openFd(), which only
    // works for assets stored uncompressed in the APK. Without this, both the
    // GPU and CPU pose delegates fail to load the bundled model.
    androidResources {
        noCompress += listOf("tflite", "task", "litertlm")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":feature:workout"))
    implementation(project(":feature:coach"))
    implementation(project(":feature:squad"))
    implementation(project(":feature:today"))
    implementation(project(":feature:profile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.hilt.compiler)

    // API-only Clerk artifact: the app keeps its own Phoenix auth surfaces
    // instead of pulling in Clerk's prebuilt Compose screens.
    implementation(libs.clerk.android.api)

    testImplementation(libs.junit)
}
