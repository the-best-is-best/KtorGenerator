plugins {
    alias(libs.plugins.kotlinMultiplatform)
}


kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
//            api(projects.automapperAnnotations)
        }
        jvmMain.dependencies {
            compileOnly(libs.ksp.api)
            implementation(libs.kotlinpoet)
            implementation(libs.kotlinpoet.ksp)
            implementation(libs.kotlinpoet.metadata)
        }
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("src/jvmMain/resources")
}
