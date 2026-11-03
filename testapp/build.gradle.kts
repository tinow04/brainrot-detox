plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.tinow.brainrotstopper.dummy"
    compileSdk = 37

    defaultConfig {
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "variant"
    productFlavors {
        create("one") {
            dimension = "variant"
            applicationId = "dev.tinow.brainrotstopper.dummy"
        }
        create("two") {
            dimension = "variant"
            applicationId = "dev.tinow.brainrotstopper.dummy2"
        }
    }
}
