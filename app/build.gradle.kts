import java.security.KeyStore
import java.security.MessageDigest
import java.io.FileInputStream
import com.android.build.gradle.AppExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mobisec.omniip"
    compileSdk = 34
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.mobisec.omniip"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        // F-05: Fail lint on release builds to prevent regressions
        abortOnError = true
        // Lint check is still lenient on debug to avoid blocking dev flow
        checkReleaseBuilds = true
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    (this as AppExtension).applicationVariants.all {
        val variantName = name.replaceFirstChar { it.uppercase() }
        val signingConfig = signingConfig
        val xorKey = 0x5A

        val taskName = "generate${variantName}SecurityConfig"
        val generateTask = tasks.register(taskName) {
            val outDir = project.file("build/generated/source/security")
            val outFile = project.file("build/generated/source/security/security_config.h")
            outputs.file(outFile)

            doLast {
                if (!outDir.exists()) {
                    outDir.mkdirs()
                }
                val hashArray = if (signingConfig != null && signingConfig.storeFile != null && signingConfig.storeFile!!.exists()) {
                    val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                    val pass = signingConfig.storePassword ?: ""
                    val alias = signingConfig.keyAlias ?: ""
                    ks.load(FileInputStream(signingConfig.storeFile!!), pass.toCharArray())
                    val cert = ks.getCertificate(alias)
                    if (cert != null) {
                        val md = MessageDigest.getInstance("SHA-256")
                        md.digest(cert.encoded)
                    } else {
                        ByteArray(32) { 0 }
                    }
                } else {
                    // F-05 fix: Throw on release builds that have no signing config.
                    // A zero-hash in production allows the RASP check to be bypassed by
                    // simply re-signing the APK with any key. Debug builds get zeros as before.
                    if (variantName.contains("Release", ignoreCase = true)) {
                        throw GradleException(
                            "F-05 SECURITY GUARD: Release build attempted without a signing config. " +
                            "The RASP signature hash cannot be zero in production. " +
                            "Configure a signingConfig in android { } and try again."
                        )
                    }
                    ByteArray(32) { 0 } // Debug builds: zero hash is acceptable
                }

                val obfuscatedHash = hashArray.map { (it.toInt() xor xorKey).toByte() }
                val hashStr = obfuscatedHash.joinToString(", ") { "0x%02x".format(it) }

                val content = """
                    #ifndef SECURITY_CONFIG_H
                    #define SECURITY_CONFIG_H

                    #include <stdint.h>

                    static const uint8_t OBFUSCATION_XOR_KEY = 0x${"%02x".format(xorKey)};
                    static const uint8_t PRODUCTION_SIGNATURE_HASH[32] = {
                        $hashStr
                    };

                    #endif // SECURITY_CONFIG_H
                """.trimIndent()

                outFile.writeText(content)
            }
        }

    }
}

tasks.whenTaskAdded {
    if (name.contains("externalNativeBuild") || name.contains("configureCMake") || name.contains("generateJsonModel")) {
        if (!name.contains("Clean", ignoreCase = true)) {
            if (name.contains("Debug", ignoreCase = true)) {
                dependsOn("generateDebugSecurityConfig")
            } else if (name.contains("Release", ignoreCase = true)) {
                dependsOn("generateReleaseSecurityConfig")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.maxmind.geoip2:geoip2:4.0.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    val workVersion = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    implementation("com.google.guava:guava:33.1.0-android")
    implementation("com.android.billingclient:billing-ktx:6.2.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("dnsjava:dnsjava:3.5.3")

    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")
}
