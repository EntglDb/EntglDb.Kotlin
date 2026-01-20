plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
    kotlin("plugin.serialization")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.entgldb.network"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }


}

dependencies {
    implementation(project(":core"))
    implementation(project(":protocol"))
    
    // Ktor for networking
    implementation("io.ktor:ktor-network:2.3.7")
    implementation("io.ktor:ktor-network-tls:2.3.7")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    implementation("androidx.core:core-ktx:1.12.0")

    // Brotli compression - use pure Java implementation for Android compatibility
    implementation("org.brotli:dec:0.1.2")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
