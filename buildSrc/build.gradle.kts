plugins {
    `kotlin-dsl`
}

val java = JavaVersion.VERSION_17

repositories {
    maven("https://maven.aliyun.com/repository/google")
    maven("https://maven.aliyun.com/repository/public")
    google()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    //noinspection UseTomlInstead
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r")

    compileOnly(gradleApi())

    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.github.javaparser:javaparser-core:3.25.4")
    implementation("com.squareup:kotlinpoet:1.15.0")
}

java {
    targetCompatibility = java
    sourceCompatibility = java
}

kotlin {
    jvmToolchain(java.toString().toInt())
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    }
}
