import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
    kotlin("plugin.serialization")
    id("com.vanniktech.maven.publish")
}

configure<LibraryExtension>{
    compileSdk = 36
    namespace = "com.entgldb.network"

    defaultConfig{
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":protocol"))
    
    // Ktor for networking
    implementation("io.ktor:ktor-network:3.4.0")
    implementation("io.ktor:ktor-network-tls:3.4.0")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    
    implementation("androidx.core:core-ktx:1.17.0")

    // Brotli compression - use pure Java implementation for Android compatibility
    implementation("org.brotli:dec:0.1.2")
// optional

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
