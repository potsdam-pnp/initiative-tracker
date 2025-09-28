import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.jetbrainsCompose)
  alias(libs.plugins.compose.compiler)
  id("io.kotest.multiplatform") version "5.9.1"
  id("com.ncorti.ktfmt.gradle") version "0.22.0"
  id("com.google.osdetector") version "1.7.3"
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName = "composeApp"
    browser {
      commonWebpackConfig {
        outputFileName = "composeApp.js"
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            static =
              (static ?: mutableListOf()).apply {
                // Serve sources to debug inside browser
                add(project.projectDir.path)
              }
          }
      }
    }
    binaries.executable()
  }

  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

  jvm()

  applyDefaultHierarchyTemplate()

  sourceSets {
    val sharedMain by creating {
      dependsOn(commonMain.get())
      androidMain.get().dependsOn(this)
      jvmMain.get().dependsOn(this)
    }

    commonMain { kotlin.srcDir("build/generated/proto-kotlin") }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    androidMain.dependencies {
      implementation(compose.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.ktor.client.cio)
      implementation(libs.acra.mail)
      implementation(libs.acra.dialog)
      implementation("com.canopas.intro-showcase-view:introshowcaseview:2.0.2")
    }
    sharedMain.dependencies {
      implementation(libs.ktor.server.core)
      implementation(libs.ktor.server.netty)
      implementation(libs.ktor.server.websockets)
    }
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      // implementation(compose.material3)
      implementation("org.jetbrains.compose.material3:material3:1.9.0-alpha04")
      implementation("org.jetbrains.compose.ui:ui-backhandler:1.9.0-alpha03")
      implementation(compose.ui)
      implementation(compose.animation)
      implementation(compose.materialIconsExtended)
      implementation(compose.components.resources)
      implementation(compose.components.uiToolingPreview)
      implementation(libs.lifecycle.viewmodel.compose)
      implementation(libs.androidx.navigation.compose)
      implementation(libs.ktor.client.core)
      implementation(libs.napier)
      implementation(libs.multiplatform.settings.no.arg)
      api(libs.pbandk.runtime)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.property)
      implementation(libs.kotest.framework.engine)
    }
    androidUnitTest.dependencies { implementation(libs.kotest.runner.junit5) }
    jvmTest.dependencies { implementation(libs.kotest.runner.junit5) }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.ktor.client.cio)
    }
    wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
  }
}

android {
  namespace = "io.github.potsdam_pnp.initiative_tracker"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
  sourceSets["main"].res.srcDirs("src/androidMain/res")
  sourceSets["main"].resources.srcDirs("src/commonMain/resources")

  defaultConfig {
    applicationId = "io.github.potsdam_pnp.initiative_tracker"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 220
    versionName = System.getenv("VERSION_NAME") ?: "0.5.1"
    buildConfigField("String", "DistributionChannel", "unknown")
  }
  packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,io.netty.versions.properties}" }
  }
  buildTypes { getByName("release") { isMinifyEnabled = false } }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  dependencies {
    debugImplementation(compose.uiTooling)
    implementation(libs.androidx.lifecycle.service)
  }

  lint { disable += "NullSafeMutableLiveData" }

  packaging { resources.excludes.add("kotlin-tooling-metadata.json") }

  flavorDimensions += "distributionChannel"
  productFlavors {
    create("playStore") { buildConfigField("String", "DistributionChannel", "\"play\"") }
    create("fdroid") { buildConfigField("String", "DistributionChannel", "\"f-droid/direct\"") }
  }
}

compose.desktop {
  application {
    mainClass = "MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "io.github.potsdam_pnp.initiative_tracker"
      packageVersion = "1.0.0"
    }
  }
}

ktfmt { googleStyle() }

val protocConfig by configurations.creating

dependencies {
  protocConfig("com.google.protobuf:protoc:4.31.0:${osdetector.os}-${osdetector.arch}@exe")
  protocConfig("pro.streem.pbandk:protoc-gen-pbandk-jvm:0.16.0:jvm8@jar")
}

val copyTask =
  tasks.register<Copy>("copyProtocAndMakeExecutable") {
    from(protocConfig.files.map { it.path })
    into("build/protoc")
    rename { if (it.endsWith(".exe")) "protoc.exe" else "protoc-gen-pbandk" }
    doLast {
      file("build/protoc/protoc.exe").setExecutable(true)
      file("build/protoc/protoc-gen-pbandk").setExecutable(true)
    }
  }

val generateCommonProto =
  tasks.register<Exec>("generateCommonProto") {
    val outputDir = layout.buildDirectory.dir("generated/proto-kotlin")
    outputs.dir(outputDir)
    inputs.files("src/jvmMain/proto/message.proto")
    executable = project.layout.buildDirectory.file("protoc/protoc.exe").get().asFile.path
    standardOutput = System.out
    errorOutput = System.err
    setEnvironment(
      "PATH" to
        "${project.layout.buildDirectory.dir("protoc").get().asFile.path}:${System.getenv("PATH")}"
    )
    setArgs(
      listOf("--pbandk_out=${outputDir.get().asFile.path}", "src/jvmMain/proto/message.proto")
    )
    dependsOn(copyTask)
  }

listOf(
    tasks.withType<KotlinCompileCommon>(),
    tasks.withType<KotlinCompile>(),
    tasks.withType<Kotlin2JsCompile>(),
  )
  .forEach { it.configureEach { dependsOn(generateCommonProto) } }
