// Top-level build file
buildscript {
    // Use Kotlin 2.1.20 (supports metadata version 2.4.0 from cloudstream.jar)
    val kotlinVersion = "2.1.20"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
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

    // Apply Java plugin and set source/target compatibility correctly
    apply(plugin = "java")
    
    // Correct way to set Java compatibility in Kotlin DSL
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register("make") {
    dependsOn(subprojects.map { it.tasks.named("make") })
}

tasks.register("clean") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}tasks.register("clean") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}
