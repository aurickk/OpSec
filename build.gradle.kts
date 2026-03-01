plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
    id("maven-publish")
}

// Load properties - Stonecutter should make version-specific properties available
val minecraft_version: String by project
val minecraft_version_range: String = findProperty("minecraft_version_range")?.toString() ?: ">=1.21.1"
val version_suffix: String = findProperty("version_suffix")?.toString() ?: minecraft_version

group = property("maven_group").toString()
version = property("mod_version").toString()

base {
    archivesName.set("opsec-$version_suffix")
}

// Use "+" separator instead of default "-" for version
tasks.named<Jar>("jar") {
    archiveFileName.set("${base.archivesName.get()}+v${project.version}.jar")
}

tasks.named("remapJar") {
    (this as org.gradle.jvm.tasks.Jar).archiveFileName.set("${base.archivesName.get()}+v${project.version}.jar")
}

loom {
    splitEnvironmentSourceSets()
    
    mods {
        create("opsec") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
    
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName = "opsec.refmap.json"
    }
}

// Stonecutter uses src/ directly, no need to reference rootProject
sourceSets {
    main {
        resources.srcDirs("src/main/resources")
    }
    getByName("client") {
        java.srcDirs("src/client/java")
        resources.srcDirs("src/client/resources")
    }
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/") { name = "Terraformers" }
    maven("https://repo.spongepowered.org/repository/maven-public/") { name = "Sponge" }
    maven("https://maven.bawnorton.com/releases") { name = "Bawnorton" }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    modImplementation("com.terraformersmc:modmenu:${property("modmenu_version")}")
    
    // MixinSquared for cancelling other mods' mixins (Meteor Client fix)
    include(implementation(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.2-beta.4")!!)!!)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version_range", minecraft_version_range)
    
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version_range" to minecraft_version_range
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "opsec-$minecraft_version"
            from(components["java"])
        }
    }
}
