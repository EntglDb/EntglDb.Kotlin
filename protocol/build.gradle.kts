plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

android {
    namespace = "com.entgldb.protocol"
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

tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
        jdkVersion.set(17)
        suppressInheritedMembers.set(false)
        suppressObviousFunctions.set(true)
    }
}

dependencies {
    api("com.google.protobuf:protobuf-kotlin-lite:3.24.4") // Updated version to match protoc if possible, checking latest stable
    implementation("com.google.protobuf:protobuf-javalite:3.24.4") // Java lite dependency often needed for generated Java code
    
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
    implementation("com.google.protobuf:protobuf-javalite:3.25.1")
}
