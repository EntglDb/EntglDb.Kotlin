plugins {
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    id("com.android.library") version "8.2.2" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

allprojects {
    group = "com.entgldb"
    version = "0.1.0"
    
    repositories {
        google()
        mavenCentral()
    }
}
