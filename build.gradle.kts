plugins {
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    id("com.android.library") version "8.2.2" apply false
    id("com.google.protobuf") version "0.9.4" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
    group = "com.entgldb"
    version = findProperty("VERSION_NAME") as String? ?: "0.0.1-SNAPSHOT"
    
    repositories {
        google()
        mavenCentral()
    }
}
