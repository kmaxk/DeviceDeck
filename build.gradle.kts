import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

group = "de.devicedeck"
version = "1.4.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin")
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "DeviceDeck"
            packageVersion = "1.4.0"
            description = "ADB device manager for Android developers"
            copyright = "© 2026 DeviceDeck"
            vendor = "DeviceDeck"

            macOS {
                bundleID = "de.devicedeck"
                minimumSystemVersion = "12.0"
                dockName = "DeviceDeck"
                iconFile.set(project.file("src/main/resources/AppIcon.icns"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/AppIcon.png"))
            }

            windows {
                iconFile.set(project.file("src/main/resources/AppIcon.ico"))
                menuGroup = "DeviceDeck"
                perUserInstall = true
            }
        }
        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}
