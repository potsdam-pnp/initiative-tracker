import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    id("com.ncorti.ktfmt.gradle") version("0.22.0") apply false
}

val nixManaged = hasProperty("nixManaged") && property("nixManaged") == "true"

if (nixManaged) {
    rootProject.plugins.withType<NodeJsRootPlugin> {
        rootProject.the<NodeJsEnvSpec>().download = false
    }
}
