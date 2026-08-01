// Top-level build file
buildscript {
    // Set Kotlin version to 2.1.20 (supports metadata version 2.4.0)
    val kotlinVersion = "2.1.20"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        // Use Android Gradle Plugin 8.6.0 (compatible with Kotlin 2.1.20)
        classpath("com.android.tools.build:gradle:8.6.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    // Ensure Java 17 is used everywhere
    apply(plugin = "java")
    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register("make") {
    dependsOn(subprojects.map { it.tasks.named("make") })
}

tasks.register("clean") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}
