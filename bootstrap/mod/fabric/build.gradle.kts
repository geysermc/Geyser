plugins {
    id("geyser.modded-conventions")
    id("geyser.modrinth-uploading-conventions")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val includeTransitive: Configuration = configurations.getByName("includeTransitive")

dependencies {
    modImplementation(libs.fabric.loader)
    modApi(libs.fabric.api)

    api(project(":mod", configuration = "namedElements"))
    shadow(project(path = ":mod", configuration = "transformProductionFabric")) {
        isTransitive = false
    }
    shadow(projects.core) { isTransitive = false }
    includeTransitive(projects.core)

    // These are NOT transitively included, and instead shadowed (+ relocated, if not under the org.geyser namespace).
    // Avoids fabric complaining about non-SemVer versioning
    shadow(libs.protocol.connection) { isTransitive = false }
    shadow(libs.protocol.common) { isTransitive = false }
    shadow(libs.protocol.codec) { isTransitive = false }
    shadow(libs.raknet) { isTransitive = false }
    shadow(libs.mcprotocollib) { isTransitive = false }

    // Since we also relocate cloudburst protocol: shade erosion common
    shadow(libs.erosion.common) { isTransitive = false }

    // Let's shade in our own api/common module
    shadow(projects.api) { isTransitive = false }
    shadow(projects.common) { isTransitive = false }

    modImplementation(libs.cloud.fabric)
    include(libs.cloud.fabric)
    include(libs.fabric.permissions.api)
}

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "org.geysermc.geyser.platform.fabric.GeyserFabricMain"
}

relocate("org.cloudburstmc.netty")
relocate("org.cloudburstmc.protocol")

tasks {
    remapJar {
        archiveBaseName.set("Geyser-Fabric")
    }

    remapModrinthJar {
        archiveBaseName.set("geyser-fabric")
    }
}

modrinth {
    loaders.add("fabric")
    uploadFile.set(tasks.getByPath("remapModrinthJar"))
    dependencies {
        required.project("fabric-api")
    }
}
