plugins { id("com.android.application") }
android {
    namespace = "com.skensel.stopad"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.skensel.stopad"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0-dev"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
