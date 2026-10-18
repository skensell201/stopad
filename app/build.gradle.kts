plugins { id("com.android.application") }
val releaseSigningDirectory = File(providers.environmentVariable("STOPAD_SIGNING_DIR")
    .orElse("${System.getProperty("user.home")}/.local/share/stopad/signing").get())
val releaseKeyStore = releaseSigningDirectory.resolve("release.p12")
val releasePassword = releaseSigningDirectory.resolve("storepass")
android {
    namespace = "com.skensel.stopad"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.skensel.stopad"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0-dev"
    }
    signingConfigs {
        if (releaseKeyStore.isFile && releasePassword.isFile) {
            create("stopadRelease") {
                storeFile = releaseKeyStore
                storeType = "PKCS12"
                storePassword = releasePassword.readText().trim()
                keyAlias = "stopad-release"
                keyPassword = storePassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("stopadRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
