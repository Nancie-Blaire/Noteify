plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.testtasksync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.testtasksync"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.work.runtime)

    // Google Sign-In & Credentials - TANGGALIN YUNG 20.7.0
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // UI
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    //implementation("com.google.code.gson:gson:2.10.1")
    //implementation("com.google.android.material:material:1.9.0")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("com.squareup.picasso:picasso:2.71828")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    //time
    implementation(libs.material.calendar)
    implementation(libs.joda.time)

    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

}