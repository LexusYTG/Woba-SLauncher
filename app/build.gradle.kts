import java.util.Properties

plugins {
    id("com.android.application")
}

// Firma de release: se lee de keystore.properties (local, fuera de git) o de variables de entorno (CI).
// Si no hay ninguna, assembleRelease genera un APK sin firmar.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.woba.slauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.woba.slauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.0.1"
    }

    val storePath = signing("storeFile", "KEYSTORE_FILE")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signing("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "KEY_ALIAS")
                keyPassword = signing("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Sin dependencias: solo el framework de Android.
