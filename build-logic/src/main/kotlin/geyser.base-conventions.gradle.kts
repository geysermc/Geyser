plugins {
    `java-library`
    id("net.kyori.indra")
}

val rootProperties: Map<String, *> = project.rootProject.properties
group = rootProperties["group"] as String + "." + rootProperties["id"] as String
version = rootProperties["version"] as String
description = rootProperties["description"] as String

indra {
    github("GeyserMC", "Geyser") {
        ci(true)
        issues(true)
        scm(true)
    }
    mitLicense()

    javaVersions {
        target(17)
    }
}

dependencies {
    compileOnly("org.checkerframework", "checker-qual", libs.checker.qual.get().version)
}

repositories {
    // mavenLocal()

    mavenCentral()

    // Floodgate, Cumulus etc.
    maven("https://repo.opencollab.dev/main")

    // Paper, Velocity
    maven("https://repo.papermc.io/repository/maven-public")

    // Spigot
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots") {
        mavenContent { snapshotsOnly() }
    }

    // BungeeCord
    maven("https://oss.sonatype.org/content/repositories/snapshots") {
        mavenContent { snapshotsOnly() }
    }

    // NeoForge
    maven("https://maven.neoforged.net/releases") {
        mavenContent { releasesOnly() }
    }

    // Minecraft
    maven("https://libraries.minecraft.net") {
        name = "minecraft"
        mavenContent { releasesOnly() }
    }

    // ViaVersion
    maven("https://repo.viaversion.com") {
        name = "viaversion"
    }

    // MinecraftAuth
    maven("https://maven.lenni0451.net/snapshots")

    // Jitpack for e.g. MCPL
    maven("https://jitpack.io") {
        content { includeGroupByRegex("com\\.github\\..*") }
    }

    // For Adventure snapshots
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}
