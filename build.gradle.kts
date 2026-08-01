// Top-level build file
buildscript {
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

    // Set Java compatibility for all subprojects
    apply(plugin = "java")
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Make sure these tasks are defined OUTSIDE the allprojects block
tasks.register("make") {
    dependsOn(subprojects.map { it.tasks.named("make") })
}

tasks.register("clean") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}
