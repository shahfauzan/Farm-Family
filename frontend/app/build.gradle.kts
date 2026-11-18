import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}
fun buildConfigValue(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val supabaseUrl = providers.environmentVariable("SUPABASE_URL")
    .orElse(providers.gradleProperty("SUPABASE_URL"))
    .orElse(localProperties.getProperty("SUPABASE_URL") ?: "")
    .get()
val supabaseAnonKey = providers.environmentVariable("SUPABASE_ANON_KEY")
    .orElse(providers.gradleProperty("SUPABASE_ANON_KEY"))
    .orElse(localProperties.getProperty("SUPABASE_ANON_KEY") ?: "")
    .get()

val releaseStoreFile = providers.environmentVariable("FARM_FAMILY_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("FARM_FAMILY_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FARM_FAMILY_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FARM_FAMILY_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').contains("Release", ignoreCase = true)
}
val appVersionName = providers.environmentVariable("FARM_FAMILY_VERSION_NAME")
    .orElse(providers.gradleProperty("FARM_FAMILY_VERSION_NAME"))
    .orElse("1.0.6")
    .get()

if (releaseBuildRequested && !releaseSigningConfigured) {
    throw GradleException("Release signing is not configured. Set the FARM_FAMILY_RELEASE_* environment variables.")
}

android {
    namespace = "com.farmfamily.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.farmfamily.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = appVersionName
        buildConfigField("String", "SUPABASE_URL", buildConfigValue(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigValue(supabaseAnonKey))
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
