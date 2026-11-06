pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // Quartz 2.16.0 only: JitPack builds it from its tag (see gradle/libs.versions.toml). Only
        // Quartz's own groups are read from it; everything else still comes from Maven Central.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.vitorpamplona.quartz")
                includeGroup("com.github.vitorpamplona")
            }
        }
    }
}

rootProject.name = "jsonbench"

// Benchmark an unreleased Quartz: point at a vitorpamplona/quartz checkout and its :quartz
// project replaces the JitPack artifact, e.g.
//   ./gradlew -PquartzDir=../../quartz installDist      (or QUARTZ_DIR=… ./run.sh)
// Every result row then records the checkout's commit instead of the release version.
providers.gradleProperty("quartzDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.quartz:quartz")).using(project(":quartz"))
            substitute(module("com.github.vitorpamplona.quartz:quartz-jvm")).using(project(":quartz"))
        }
    }
}
