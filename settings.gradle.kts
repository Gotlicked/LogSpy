pluginManagement {
    repositories {
        gradlePluginPortal()
        pluginManagement {
            repositories {
                gradlePluginPortal()
                exclusiveContent {
                    forRepository {
                        maven {
                            name = "Fabric"
                            url = uri("https://maven.fabricmc.net")
                        }
                    }
                    filter { includeGroupAndSubgroups("net.fabricmc") }
                }
                exclusiveContent {
                    forRepository {
                        maven {
                            name = "NeoForge"
                            url = uri("https://maven.neoforged.net/releases")
                        }
                    }
                    filter { includeGroupAndSubgroups("net.neoforged") }
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "LogSpy"

include("common")
include("fabric")
include("neoforge")
