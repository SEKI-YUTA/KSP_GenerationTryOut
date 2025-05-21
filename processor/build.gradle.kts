plugins {
    kotlin("jvm")
}
java {
    sourceCompatibility = JavaVersion.VERSION_18
    targetCompatibility = JavaVersion.VERSION_18
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_18
    }
}
dependencies {
    implementation(libs.symbol.processing.api)
    implementation(libs.kotlinpoet.ksp)
    implementation(project(":annotations"))
}