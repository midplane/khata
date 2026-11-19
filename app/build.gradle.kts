import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/*
 * Versioning: derived from the UTC time of the latest git commit, so every build of a commit
 * gets the same version.
 *   versionName = yymmdd.HHMM               e.g. 261116.1930
 *   versionCode = minutes since 2020-01-01  (always increasing; fits Android's int limit)
 * Falls back to the current time when git history isn't available (e.g. a source tarball).
 */
val versionTime: Instant = runCatching {
    providers.exec {
        commandLine("git", "log", "-1", "--format=%ct")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toLong().let(Instant::ofEpochSecond)
}.getOrElse { Instant.now() }

val appVersionName: String =
    DateTimeFormatter.ofPattern("yyMMdd.HHmm").withZone(ZoneOffset.UTC).format(versionTime)
val appVersionCode: Int =
    ((versionTime.epochSecond - Instant.parse("2020-01-01T00:00:00Z").epochSecond) / 60).toInt()

/*
 * Release signing: create keystore.properties in the repo root (git-ignored), see README.
 * Without it, release builds are signed with the local debug key.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.midplane.khata"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.midplane.khata"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // Sign with every scheme: some app stores require the legacy v1 (JAR) signature too,
                // which AGP otherwise skips for minSdk >= 24.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Exported Room schemas are committed so database migrations can be reviewed and tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
