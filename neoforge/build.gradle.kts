plugins {
    id("net.neoforged.moddev")
    id("java-library")
    id("maven-publish")
}

base {
    archivesName = "${property("mod_id")}-${project.name}-${property("minecraft_version")}-${property("mod_version")}"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(property("java_version").toString())
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

val commonJava: Configuration by configurations.creating
val commonResources: Configuration by configurations.creating

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

neoForge {
    version = "${property("neoforge_version")}"

    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }

    runs {
        create("client") {
            client()
            ideName = "NeoForge Client"
            gameDirectory = mkdir(file("runs/client"))
        }
        create("data") {
            clientData()
            ideName = "NeoForge Data"
            gameDirectory = mkdir(file("runs/data"))
            programArguments.addAll(
                "--mod", "${property("mod_id")}", "--all", "--output",
                file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/assets").absolutePath,
                "--existing", file("src/main/resources/data").absolutePath,
            )
        }
        create("server") {
            server()
            ideName = "NeoForge Server"
            gameDirectory = mkdir(file("runs/server"))
        }
    }

    mods {
        create("${property("mod_id")}") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    compileOnly(project(":common"))
    add("commonJava",      project(path = ":common", configuration = "commonJava"))
    add("commonResources", project(path = ":common", configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(commonResources)
    from(commonResources)
    filesMatching(listOf(
        "META-INF/neoforge.mods.toml",
        "pack.mcmeta",
        "*.mixins.json",
    )) {
        expand(project.properties)
    }
    from("src/main/templates")
}

tasks.named<Javadoc>("javadoc") {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonJava, commonResources)
    from(commonJava)
    from(commonResources)
    from(rootProject.file("LICENSE")) {
        rename{ it }
    }
}

tasks.named<Jar>("jar") {
    dependsOn(commonJava, commonResources)
    from(commonJava)
    from(commonResources)
    from(rootProject.file("LICENSE")) {
        rename{ it }
    }
    manifest {
        attributes(
            "Specification-Title"    to findProperty("mod_name").toString(),
            "Specification-Vendor"   to findProperty("mod_author").toString(),
            "Specification-Version"  to findProperty("mod_version").toString(),
            "Implementation-Title"   to findProperty("mod_id").toString(),
            "Implementation-Version" to findProperty("mod_version").toString(),
            "Implementation-Vendor"  to findProperty("mod_author").toString(),
            "Built-On-Minecraft"     to findProperty("minecraft_version").toString(),
        )
    }
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(
                System.getenv("local_maven_url")
                    ?: layout.buildDirectory.dir("repo").get().asFile.toURI().toString()
            )
        }
    }
}
