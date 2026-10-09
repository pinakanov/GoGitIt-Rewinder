import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.gogitit.rewinder.whisper"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()

        ndk {
            // Real phones and Apple Silicon emulators. Add "x86_64" for Intel emulators.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // Always optimize: a Debug build of ggml is 10x+ slower.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
