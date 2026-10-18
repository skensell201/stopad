plugins { id("com.android.application") }
android {
    namespace = "com.skensel.stopad.probe"
    compileSdk = 36
    defaultConfig { applicationId = "com.skensel.stopad.probe"; minSdk = 29; targetSdk = 36 }
    flavorDimensions += "role"
    productFlavors {
        create("game") { dimension = "role"; applicationIdSuffix = ".game" }
        create("manual") { dimension = "role"; applicationIdSuffix = ".manual" }
        create("browser") { dimension = "role"; applicationIdSuffix = ".browser" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
