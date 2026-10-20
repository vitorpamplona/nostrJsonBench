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
    }
}

rootProject.name = "jsonbench"

// Benchmark an unreleased Quartz: point at an Amethyst checkout and its :quartz project
// replaces the Maven Central artifact, e.g.
//   ./gradlew -PquartzDir=../../amethyst installDist      (or QUARTZ_DIR=… ./run.sh)
// Every result row then records the checkout's commit instead of the release version.
providers.gradleProperty("quartzDir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("com.vitorpamplona.quartz:quartz")).using(project(":quartz"))
        }
    }
}
