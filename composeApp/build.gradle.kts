import com.google.devtools.ksp.gradle.KspAATask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.comGoogleDevToolsKsp)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.serialization.json)

            implementation(libs.kotlinx.serialization.json)

            implementation(projects.ktorGeneratorAnnotations)

            implementation(libs.kotlinx.datetime)

            implementation(libs.automapperannotations)

            implementation("io.github.vinceglb:filekit-dialogs-compose:0.12.0")


        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)

        }

        sourceSets.named("commonMain").configure {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }
    }
}

android {
    namespace = "io.github.tbib.ktorgeneratorapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.tbib.ktorgeneratorapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    ksp(projects.ktorGeneratorProcessor)
    ksp(libs.automapper.processor)
}



ksp {
}


dependencies {
    add("kspCommonMainMetadata", projects.ktorGeneratorProcessor)
    add("kspAndroid", projects.ktorGeneratorProcessor)
    add("kspIosArm64", projects.ktorGeneratorProcessor)
    add("kspIosSimulatorArm64", projects.ktorGeneratorProcessor)

    debugImplementation(compose.uiTooling)
}

project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}


project.tasks.withType(KspAATask::class.java).configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        if (name == "kspDebugKotlinAndroid") {
            enabled = false
        }
        if (name == "kspReleaseKotlinAndroid") {
            enabled = false
        }
        if (name == "kspKotlinIosSimulatorArm64") {
            enabled = false
        }
        if (name == "kspKotlinIosX64") {
            enabled = false
        }
        if (name == "kspKotlinIosArm64") {
            enabled = false
        }
        dependsOn("kspCommonMainKotlinMetadata")
    }
}