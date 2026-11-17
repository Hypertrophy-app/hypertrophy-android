plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}

// The repository-wide verification command is `testDebugUnitTest`, which only
// exists on Android modules. Alias it here so pure-JVM kinematics tests run in
// the same aggregate invocation instead of being silently skipped.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the pure-JVM kinematics unit tests under the Android task name."
    dependsOn(tasks.named("test"))
}
