import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
    id("com.vanniktech.maven.publish")
}

configure<LibraryExtension>{
    compileSdk = 36
    namespace = "com.entgldb.protcol"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api("com.google.protobuf:protobuf-kotlin-lite:4.33.4") // Updated version to match protoc if possible, checking latest stable
    implementation("com.google.protobuf:protobuf-javalite:4.33.4") // Java lite dependency often needed for generated Java code
    
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.4"
    }
    
    // Configures the Protobuf Gradle Plugin to generate code for Android
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-javalite:4.33.4")
}
