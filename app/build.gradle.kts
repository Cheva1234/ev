import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val bundledModelAssetsDir = layout.buildDirectory.dir("generated/bundled-model-assets")
val bundledModelSource = providers.gradleProperty("evModelFile")
    .orElse(providers.environmentVariable("EV_MODEL_FILE"))
val prepareBundledModel = tasks.register<Sync>("prepareBundledModel") {
    from(bundledModelSource.map { sourcePath ->
        val source = file(sourcePath)
        if (!source.isFile || source.length() == 0L) {
            throw GradleException("EV model file does not exist or is empty: $sourcePath")
        }
        source
    }) {
        into("models")
        rename { "qwen3.5-0.8b.gguf" }
    }
    into(bundledModelAssetsDir)
}

android {
    namespace = "com.ev.terminal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ev.terminal"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.2"
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
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    androidResources {
        noCompress += "gguf"
    }
    sourceSets["main"].assets.srcDir(bundledModelAssetsDir)
}

tasks.configureEach {
    if (name.matches(Regex("merge.*Assets"))) {
        dependsOn(prepareBundledModel)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.commonmark:commonmark:0.21.0")
    testImplementation("junit:junit:4.13.2")
}
