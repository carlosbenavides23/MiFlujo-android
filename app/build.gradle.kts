plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

val catalogLabUiTests = providers.gradleProperty("catalogLabUiTests").orNull == "true"

val catalogPreviewUiTests = providers.gradleProperty("catalogPreviewUiTests").orNull == "true"
check(!(catalogLabUiTests && catalogPreviewUiTests))

android {
    namespace = "com.carlos.miflujo"
    if (catalogLabUiTests) testBuildType = "catalogLab"
    if (catalogPreviewUiTests) testBuildType = "catalogPreview"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.carlos.miflujo"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("catalogLab") {
            initWith(getByName("debug"))
            applicationIdSuffix = if (catalogLabUiTests) ".cataloglab.uitest" else ".cataloglab"
            manifestPlaceholders["catalogLabLabel"] =
                if (catalogLabUiTests) "MiFlujo · Pruebas de catálogo" else "MiFlujo · Catálogo de pruebas"
            matchingFallbacks += "debug"
        }
        create("catalogPreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = if (catalogPreviewUiTests) ".catalogpreview.uitest" else ".catalogpreview"
            manifestPlaceholders["catalogPreviewLabel"] =
                if (catalogPreviewUiTests) "MiFlujo · Pruebas de integración" else "MiFlujo · Vista de catálogo"
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("catalogLab").res.srcDir("src/debug/res")
        listOf("catalogLab", "catalogPreview").forEach {
            getByName(it).apply {
                kotlin.directories.add("src/catalogUi/kotlin")
                res.directories.add("src/catalogUi/res")
                assets.directories.add("src/catalogUi/assets")
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.matching { it.name.endsWith("AndroidTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
    )
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    if (catalogLabUiTests) {
        add("catalogLabImplementation", libs.androidx.compose.ui.test.manifest)
    }
    debugImplementation(libs.androidx.compose.ui.tooling)
}
