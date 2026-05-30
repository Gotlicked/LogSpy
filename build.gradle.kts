plugins {
    id("net.fabricmc.fabric-loom") version "1.16.2" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
}

subprojects {
    version = rootProject.version
    group   = rootProject.group
}
