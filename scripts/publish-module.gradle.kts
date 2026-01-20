// Publishing configuration for Maven Central
// Apply this from module build.gradle.kts files

apply(plugin = "maven-publish")
apply(plugin = "signing")

val versionName: String by project
val groupId: String by project

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                
                this.groupId = project.property("GROUP_ID") as String
                this.artifactId = project.name
                this.version = project.property("VERSION_NAME") as String
                
                pom {
                    name.set("EntglDb ${project.name}")
                    description.set(project.property("POM_DESCRIPTION") as String)
                    url.set(project.property("POM_URL") as String)
                    
                    licenses {
                        license {
                            name.set(project.property("POM_LICENCE_NAME") as String)
                            url.set(project.property("POM_LICENCE_URL") as String)
                        }
                    }
                    
                    developers {
                        developer {
                            id.set(project.property("POM_DEVELOPER_ID") as String)
                            name.set(project.property("POM_DEVELOPER_NAME") as String)
                        }
                    }
                    
                    scm {
                        connection.set(project.property("POM_SCM_CONNECTION") as String)
                        developerConnection.set(project.property("POM_SCM_DEV_CONNECTION") as String)
                        url.set(project.property("POM_SCM_URL") as String)
                    }
                }
            }
        }
        
        repositories {
            maven {
                name = "OSSRH"
                // New Sonatype Central Portal uses unified endpoint for both releases and snapshots
                val repoUrl = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
                url = uri(repoUrl)
                
                credentials {
                    username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                    password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }
    
    configure<SigningExtension> {
        // Sign using in-memory keys from environment (for CI)
        val signingKey = System.getenv("SIGNING_KEY")
        val signingPassword = System.getenv("SIGNING_PASSWORD")
        
        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        
        sign(extensions.getByType<PublishingExtension>().publications["release"])
    }
}
