plugins { id("com.android.application") }
android {
    namespace = "com.vinnigamgee.totkrecomp"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vinnigamgee.totkrecomp.inspector"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-diagnostic"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").assets.srcDir(rootDir.resolve("../../profiles"))
}
