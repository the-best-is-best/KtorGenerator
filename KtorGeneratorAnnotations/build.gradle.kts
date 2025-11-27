plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)

}
extra["packageNameSpace"] = "io.github.tbib.ktorgenerator.annotations"


kotlin {

    androidTarget()

    val xcfName = "KtorGeneratorAnnotationsKit"

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosX64(),
        macosArm64(),
        tvosX64(),
        tvosArm64(),
        tvosSimulatorArm64(),
        watchosX64(),
        watchosArm64(),
        watchosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = xcfName
        }
    }


    // 3. JVM (for server-side or desktop)
    jvm()

    // 4. JavaScript (for web browsers and NodeJS)
    js(IR) {
        browser()
        nodejs()
    }

    // 5. Linux Native Targets
    linuxX64()
    linuxArm64()


    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    android {
        // Minimal Android configuration needed for a library
        namespace = project.extra["packageNameSpace"].toString()
        compileSdk = 36
        defaultConfig {
            minSdk = 24
        }
        compileOptions {
            // You don't need to specify source/target compatibility for a KMP library
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here
                implementation(libs.ktor.client.core)

            }
        }

        commonTest {
            dependencies {
                // implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }


        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }
    }

}