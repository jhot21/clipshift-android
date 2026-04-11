import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// zstd-jni ships as a JAR with native libs embedded under linux/<arch>/.
// AGP only extracts .so files from AARs, not JARs, so we extract them manually
// into the correct ABI-named directories and register them as jniLibs.
val zstdNative: Configuration by configurations.creating

val extractZstdNativeLibs by tasks.registering {
    val abiMap = mapOf(
        "linux/aarch64" to "arm64-v8a",
        "linux/arm"     to "armeabi-v7a",
        "linux/amd64"   to "x86_64",
        "linux/i386"    to "x86",
    )
    val outputDir = layout.buildDirectory.dir("zstd-jni-libs")
    outputs.dir(outputDir)
    inputs.files(zstdNative)

    doLast {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        for (jarFile in zstdNative.files) {
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.endsWith(".so")) continue
                    val parts = entry.name.split("/")
                    if (parts.size != 3) continue
                    val abi = abiMap["${parts[0]}/${parts[1]}"] ?: continue
                    val dest = outDir.resolve("$abi/${parts[2]}")
                    dest.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

android {
    namespace = "me.jhot.clipshift"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.jhot.clipshift"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("zstd-jni-libs"))
        }
    }
    packaging {
        // Strip non-Android native libs bundled inside zstd-jni.jar as resources
        resources.excludes += setOf("darwin/**", "win/**", "linux/**")
    }
}

// Ensure native libs are extracted before any build task reads the jniLibs source set
tasks.named("preBuild") {
    dependsOn(extractZstdNativeLibs)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.bouncycastle)
    implementation(libs.argon2kt)
    implementation(libs.security.crypto)
    implementation(libs.zstd.jni)
    zstdNative(libs.zstd.jni) { isTransitive = false }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
